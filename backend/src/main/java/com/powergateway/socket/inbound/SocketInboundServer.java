package com.powergateway.socket.inbound;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.codec.FramingType;
import com.powergateway.socket.codec.LengthPrefixCodec;
import com.powergateway.socket.codec.XmlBoundaryCodec;
import com.powergateway.socket.route.InboundSocketOrchestrator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 入站 Socket 服务端(v0.3.2 SOCK-5-A · Task 2 骨架)。
 *
 * <p>Netty ServerBootstrap 复用 v0.3.0 socket.codec.* · 三分帧 + 双编码全支持。</p>
 *
 * <p><b>Task 2 骨架</b>:提供 start(config) / stop() API · 由 Task 3 InboundSocketOrchestrator 或 Task 7 InterfaceConfig 加载时调 start。
 * v0.3.2 每个 INBOUND_SOCKET 接口配置对应一个 SocketInboundServer 实例(独立端口 · 独立生命周期)。</p>
 */
public class SocketInboundServer {

    private static final Logger log = LoggerFactory.getLogger(SocketInboundServer.class);

    private final SocketInboundConfig config;
    private final InboundSocketOrchestrator orchestrator;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public SocketInboundServer(SocketInboundConfig config) {
        this(config, null);
    }

    public SocketInboundServer(SocketInboundConfig config, InboundSocketOrchestrator orchestrator) {
        this.config = config;
        this.orchestrator = orchestrator;
        validateConnectionMode();
    }

    /**
     * connectionMode 契约校验:仅 short 实装 · Q20=C。
     */
    private void validateConnectionMode() {
        String mode = config.getConnectionMode();
        if (mode != null && !mode.isEmpty() && !"short".equalsIgnoreCase(mode)) {
            throw new BusinessException("SOCK-5-A · 暂只支持 short 短连接 · long/pooled 预留 · 收到:" + mode);
        }
    }

    /** 启动服务 · 阻塞等待端口绑定完成 */
    public synchronized void start() throws InterruptedException {
        if (serverChannel != null && serverChannel.isActive()) {
            log.warn("SOCK-5-A · SocketInboundServer 已在运行 · port={}", config.getPort());
            return;
        }
        bossGroup = new NioEventLoopGroup(1, r -> {
            Thread t = new Thread(r, "sock-in-boss-" + config.getPort());
            t.setDaemon(true);
            return t;
        });
        workerGroup = new NioEventLoopGroup(4, r -> {
            Thread t = new Thread(r, "sock-in-worker-" + config.getPort());
            t.setDaemon(true);
            return t;
        });
        ServerBootstrap sb = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, false)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                new IdleStateHandler(config.getReadTimeoutMs(), 0, 0, TimeUnit.MILLISECONDS));
                        if (config.getFraming() == FramingType.XML_BOUNDARY) {
                            ch.pipeline().addLast(XmlBoundaryCodec.decoder(config.getCharset()));
                            ch.pipeline().addLast(XmlBoundaryCodec.encoder());
                        } else {
                            ch.pipeline().addLast(LengthPrefixCodec.decoder(config.getFraming()));
                            ch.pipeline().addLast(LengthPrefixCodec.encoder(config.getFraming()));
                        }
                        ch.pipeline().addLast(new SocketInboundHandler(config.getCharset(), orchestrator));
                    }
                });
        ChannelFuture cf = sb.bind(config.getPort()).sync();
        serverChannel = cf.channel();
        log.info("SOCK-5-A · SocketInboundServer 启动:port={} framing={} charset={} connectionMode={}",
                config.getPort(), config.getFraming(), config.getCharset(), config.getConnectionMode());
    }

    /** 优雅停机 */
    public synchronized void stop() {
        try {
            if (serverChannel != null && serverChannel.isActive()) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (bossGroup != null) bossGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
            if (workerGroup != null) workerGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS);
            serverChannel = null;
            log.info("SOCK-5-A · SocketInboundServer 停机:port={}", config.getPort());
        }
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public SocketInboundConfig getConfig() {
        return config;
    }
}
