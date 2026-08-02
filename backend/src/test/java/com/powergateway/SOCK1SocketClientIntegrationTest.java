package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.SocketClient;
import com.powergateway.socket.codec.CharsetSupport;
import com.powergateway.socket.codec.FramingType;
import com.powergateway.socket.codec.LengthPrefixCodec;
import com.powergateway.socket.codec.XmlBoundaryCodec;
import com.powergateway.socket.exception.SocketConnectException;
import com.powergateway.socket.exception.SocketTimeoutException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-1 · SocketClient 集成测试(v0.3.0 · Task 4)。
 *
 * <p>本地起 Netty ServerSocket 打桩 · 断言 send-receive 完整链路 · 三 framing × 双 charset。</p>
 */
@ActiveProfiles("test")
class SOCK1SocketClientIntegrationTest {

    private final SocketClient client = new SocketClient();
    private final Charset UTF8 = StandardCharsets.UTF_8;
    private final Charset GBK = Charset.forName("GBK");

    // ============ Framing × Charset 矩阵 ============

    @Test
    @DisplayName("XML_BOUNDARY + UTF-8 · 请求 <?xml Transaction · 应答 Ack XML")
    void xmlBoundary_UTF8_请求应答() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Transaction><FunctionId>181345</FunctionId></Transaction>";
        String response = "<Ack><Result>0</Result><Msg>OK</Msg></Ack>";

        try (TestServer server = TestServer.echo(FramingType.XML_BOUNDARY, UTF8, response)) {
            byte[] resp = client.send(
                    "127.0.0.1", server.port(),
                    request.getBytes(UTF8),
                    FramingType.XML_BOUNDARY, UTF8,
                    3000, 5000);
            assertEquals(response, new String(resp, UTF8));
        }
    }

    @Test
    @DisplayName("XML_BOUNDARY + GBK · 中文报文 · 回环无乱码")
    void xmlBoundary_GBK_中文报文回环() throws Exception {
        String request = "<?xml version=\"1.0\" encoding=\"GBK\"?><交易><功能号>181345</功能号></交易>";
        String response = "<应答><结果>成功</结果></应答>";

        try (TestServer server = TestServer.echo(FramingType.XML_BOUNDARY, GBK, response)) {
            byte[] resp = client.send(
                    "127.0.0.1", server.port(),
                    request.getBytes(GBK),
                    FramingType.XML_BOUNDARY, GBK,
                    3000, 5000);
            assertEquals(response, new String(resp, GBK), "GBK 中文回环不应乱码");
        }
    }

    @Test
    @DisplayName("LENGTH_PREFIX_BE4 + UTF-8 · 4 字节头 · 单帧回环")
    void be4_UTF8_单帧回环() throws Exception {
        String request = "<Req>hello</Req>";
        String response = "<Ack>world</Ack>";

        try (TestServer server = TestServer.echo(FramingType.LENGTH_PREFIX_BE4, UTF8, response)) {
            byte[] resp = client.send(
                    "127.0.0.1", server.port(),
                    request.getBytes(UTF8),
                    FramingType.LENGTH_PREFIX_BE4, UTF8,
                    3000, 5000);
            assertEquals(response, new String(resp, UTF8));
        }
    }

    @Test
    @DisplayName("LENGTH_PREFIX_BE8 + GBK · 8 字节头 · 用户 2026-08-02 实证场景")
    void be8_GBK_单帧回环() throws Exception {
        String request = "<请求><数据>你好</数据></请求>";
        String response = "<应答><状态>OK</状态></应答>";

        try (TestServer server = TestServer.echo(FramingType.LENGTH_PREFIX_BE8, GBK, response)) {
            byte[] resp = client.send(
                    "127.0.0.1", server.port(),
                    request.getBytes(GBK),
                    FramingType.LENGTH_PREFIX_BE8, GBK,
                    3000, 5000);
            assertEquals(response, new String(resp, GBK));
        }
    }

    // ============ 异常路径 ============

    @Test
    @DisplayName("连接不上 · 抛 SocketConnectException")
    void 连接不上_抛异常() {
        int freePort = pickFreePort(); // 拿空闲端口但不启动服务
        SocketConnectException ex = assertThrows(
                SocketConnectException.class,
                () -> client.send(
                        "127.0.0.1", freePort,
                        "<Req/>".getBytes(UTF8),
                        FramingType.XML_BOUNDARY, UTF8,
                        1000, 2000)
        );
        assertTrue(ex.getMessage().contains("连接"), "错误消息含连接");
    }

    @Test
    @DisplayName("服务端不回应 · 读超时抛 SocketTimeoutException")
    void 读超时_抛异常() throws Exception {
        try (TestServer server = TestServer.silent(FramingType.XML_BOUNDARY, UTF8)) {
            SocketTimeoutException ex = assertThrows(
                    SocketTimeoutException.class,
                    () -> client.send(
                            "127.0.0.1", server.port(),
                            "<Req/>".getBytes(UTF8),
                            FramingType.XML_BOUNDARY, UTF8,
                            2000, 500)
            );
            assertTrue(ex.getMessage().contains("超时"));
        }
    }

    @Test
    @DisplayName("服务端收后立即断开 · 抛 BusinessException 说明未收到应答")
    void 服务端主动断开_抛异常() throws Exception {
        try (TestServer server = TestServer.closeImmediately(FramingType.XML_BOUNDARY, UTF8)) {
            BusinessException ex = assertThrows(
                    BusinessException.class,
                    () -> client.send(
                            "127.0.0.1", server.port(),
                            "<Req>data</Req>".getBytes(UTF8),
                            FramingType.XML_BOUNDARY, UTF8,
                            2000, 3000)
            );
            assertTrue(ex.getMessage().contains("断开") || ex.getMessage().contains("超时"),
                    "断开或超时都可接受 · 服务端行为不确定 · 收到:" + ex.getMessage());
        }
    }

    // ============ connectionMode 契约 ============

    @Test
    @DisplayName("checkConnectionMode: short 通过 · null/空 视作 short · long/pooled 抛异常")
    void connectionMode_契约() {
        SocketClient.checkConnectionMode("short");
        SocketClient.checkConnectionMode("SHORT");
        SocketClient.checkConnectionMode(null);   // 缺省视作 short
        SocketClient.checkConnectionMode("");     // 空视作 short

        BusinessException ex1 = assertThrows(BusinessException.class,
                () -> SocketClient.checkConnectionMode("long"));
        assertTrue(ex1.getMessage().contains("short"));

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> SocketClient.checkConnectionMode("pooled"));
        assertTrue(ex2.getMessage().contains("short"));
    }

    // ============ TestServer helper ============

    private static int pickFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("无法拿空闲端口", e);
        }
    }

    /**
     * 测试用 Netty ServerSocket · 收请求后按 responder 应答(或不应答)。
     */
    private static class TestServer implements AutoCloseable {

        private final EventLoopGroup boss;
        private final EventLoopGroup worker;
        private final Channel serverChannel;
        private final int port;

        private TestServer(int port, FramingType framing, Charset charset, ResponderMode mode, byte[] response) throws InterruptedException {
            this.boss = new NioEventLoopGroup(1);
            this.worker = new NioEventLoopGroup(1);
            ServerBootstrap sb = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
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
                                    switch (mode) {
                                        case ECHO_FIXED:
                                            ctx.writeAndFlush(response)
                                                    .addListener(ChannelFutureListener.CLOSE);
                                            break;
                                        case SILENT:
                                            // 不应答 · 客户端会读超时
                                            break;
                                        case CLOSE_IMMEDIATELY:
                                            ctx.close();
                                            break;
                                    }
                                }
                            });
                        }
                    });
            ChannelFuture cf = sb.bind(port).sync();
            this.serverChannel = cf.channel();
            this.port = port;
        }

        static TestServer echo(FramingType framing, Charset charset, String response) throws InterruptedException {
            return new TestServer(pickFreePort(), framing, charset, ResponderMode.ECHO_FIXED, response.getBytes(charset));
        }

        static TestServer silent(FramingType framing, Charset charset) throws InterruptedException {
            return new TestServer(pickFreePort(), framing, charset, ResponderMode.SILENT, null);
        }

        static TestServer closeImmediately(FramingType framing, Charset charset) throws InterruptedException {
            return new TestServer(pickFreePort(), framing, charset, ResponderMode.CLOSE_IMMEDIATELY, null);
        }

        int port() {
            return port;
        }

        @Override
        public void close() {
            try {
                serverChannel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                boss.shutdownGracefully(0, 300, TimeUnit.MILLISECONDS);
                worker.shutdownGracefully(0, 300, TimeUnit.MILLISECONDS);
            }
        }

        enum ResponderMode {
            ECHO_FIXED, SILENT, CLOSE_IMMEDIATELY
        }
    }
}
