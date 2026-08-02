package com.powergateway.socket;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.codec.FramingType;
import com.powergateway.socket.codec.LengthPrefixCodec;
import com.powergateway.socket.codec.XmlBoundaryCodec;
import com.powergateway.socket.exception.SocketConnectException;
import com.powergateway.socket.exception.SocketTimeoutException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TCP Socket 客户端门面(v0.3.0 SOCK-1 · Task 4 实装)。
 *
 * <p>短连接(Q7=A + Q20=C 唯一实装) · 三分帧(Q5=C) · 双编码(Q6=B) · 同步 send-receive 语义。</p>
 *
 * <p><b>线程模型</b>:全局共享 {@link NioEventLoopGroup} 单例(4 个 worker) · 避免每次 send 创建线程池导致泄露。
 * JVM shutdown hook 里优雅 shutdown。</p>
 *
 * <p><b>connectionMode 契约</b>:仅支持 {@code "short"} · {@code "long"}/{@code "pooled"} 预留 · 走 {@link #checkConnectionMode(String)}。</p>
 */
public class SocketClient {

    private static final EventLoopGroup SHARED_GROUP = new NioEventLoopGroup(4, r -> {
        Thread t = new Thread(r, "socket-client-worker");
        t.setDaemon(true);
        return t;
    });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SHARED_GROUP.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
        }, "socket-client-group-shutdown"));
    }

    /**
     * 校验 connectionMode 契约 · 仅 short 实装 · 其他抛 BusinessException 预留 API。
     *
     * @param mode 接口配置里 connectionMode 字段值
     */
    public static void checkConnectionMode(String mode) {
        if (mode == null || mode.isEmpty()) {
            return; // 缺省视作 short
        }
        if (!"short".equalsIgnoreCase(mode)) {
            throw new BusinessException("暂只支持 short 短连接 · long/pooled 预留 · 收到:" + mode);
        }
    }

    /**
     * 同步发送 payload 到 host:port · 等待应答字节数组。
     *
     * @param host           目标 IP
     * @param port           目标端口
     * @param payload        请求字节(已按 charset 编码)
     * @param framing        分帧策略(Q5=C 三选一)
     * @param charset        编码(UTF-8 / GBK · Q6=B)
     * @param connTimeoutMs  连接超时毫秒
     * @param readTimeoutMs  读超时毫秒
     * @return 应答 payload 字节(未按 charset 解码)
     * @throws SocketConnectException 连接失败
     * @throws SocketTimeoutException 读超时
     */
    public byte[] send(String host,
                       int port,
                       byte[] payload,
                       FramingType framing,
                       Charset charset,
                       int connTimeoutMs,
                       int readTimeoutMs) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        Bootstrap b = new Bootstrap()
                .group(SHARED_GROUP)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connTimeoutMs)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new IdleStateHandler(readTimeoutMs, 0, 0, TimeUnit.MILLISECONDS));
                        if (framing == FramingType.XML_BOUNDARY) {
                            p.addLast(XmlBoundaryCodec.decoder(charset));
                            p.addLast(XmlBoundaryCodec.encoder());
                        } else {
                            p.addLast(LengthPrefixCodec.decoder(framing));
                            p.addLast(LengthPrefixCodec.encoder(framing));
                        }
                        p.addLast(new SimpleChannelInboundHandler<byte[]>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                future.complete(msg);
                                ctx.close();
                            }

                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                                if (evt instanceof IdleStateEvent) {
                                    future.completeExceptionally(
                                            new SocketTimeoutException("读超时 " + readTimeoutMs + "ms · " + host + ":" + port));
                                    ctx.close();
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                future.completeExceptionally(cause);
                                ctx.close();
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) {
                                if (!future.isDone()) {
                                    future.completeExceptionally(
                                            new BusinessException("Socket 服务端主动断开 · 未收到应答 · " + host + ":" + port));
                                }
                            }
                        });
                    }
                });

        ChannelFuture connectFuture = b.connect(host, port);
        try {
            boolean done = connectFuture.await(connTimeoutMs + 500L, TimeUnit.MILLISECONDS);
            if (!done) {
                connectFuture.cancel(true);
                throw new SocketConnectException("连接超时 " + connTimeoutMs + "ms · " + host + ":" + port);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SocketConnectException("连接被中断 · " + host + ":" + port, e);
        }
        if (!connectFuture.isSuccess()) {
            Throwable cause = connectFuture.cause();
            throw new SocketConnectException(
                    "连接失败 " + host + ":" + port + " · " + (cause == null ? "unknown" : cause.getMessage()),
                    cause);
        }
        Channel channel = connectFuture.channel();
        channel.writeAndFlush(payload);
        try {
            return future.get(readTimeoutMs + 1000L, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new SocketTimeoutException("等待应答超时 " + readTimeoutMs + "ms · " + host + ":" + port, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) throw (SocketTimeoutException) cause;
            if (cause instanceof SocketConnectException) throw (SocketConnectException) cause;
            if (cause instanceof BusinessException) throw (BusinessException) cause;
            throw new BusinessException("Socket 通信异常:" + (cause == null ? "unknown" : cause.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Socket 等待被中断");
        } finally {
            if (channel.isActive()) {
                channel.close();
            }
        }
    }
}
