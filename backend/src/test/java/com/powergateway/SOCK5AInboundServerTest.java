package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.codec.FramingType;
import com.powergateway.socket.codec.XmlBoundaryCodec;
import com.powergateway.socket.inbound.SocketInboundConfig;
import com.powergateway.socket.inbound.SocketInboundServer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-5-A · v0.3.2 Task 2 · SocketInboundServer 骨架单元测试。
 */
@ActiveProfiles("test")
class SOCK5AInboundServerTest {

    private final Charset UTF8 = StandardCharsets.UTF_8;

    @Test
    @DisplayName("XML_BOUNDARY · 起服务 · 客户端连接发送 · server 接收并断开(骨架)")
    void xml_boundary_起服务_客户端连接() throws Exception {
        int port = pickFreePort();
        SocketInboundConfig cfg = configFrom(port, "xml_boundary", "UTF-8");
        SocketInboundServer server = new SocketInboundServer(cfg);
        server.start();
        try {
            assertTrue(server.isRunning());
            String xml = "<?xml version=\"1.0\"?><Transaction><Body><request><bizHeader>"
                    + "<FunctionId>180345</FunctionId></bizHeader></request></Body></Transaction>";
            sendXmlOnce(port, xml, UTF8);
            Thread.sleep(200); // 让 server 处理
        } finally {
            server.stop();
        }
        assertFalse(server.isRunning());
    }

    @Test
    @DisplayName("LENGTH_PREFIX_BE8 · 起服务 · 三分帧全支持")
    void be8_起服务() throws Exception {
        int port = pickFreePort();
        SocketInboundConfig cfg = configFrom(port, "length_prefix_be8", "UTF-8");
        SocketInboundServer server = new SocketInboundServer(cfg);
        server.start();
        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("LENGTH_PREFIX_BE4 · GBK 编码 · 起服务")
    void be4_GBK_起服务() throws Exception {
        int port = pickFreePort();
        SocketInboundConfig cfg = configFrom(port, "length_prefix_be4", "GBK");
        SocketInboundServer server = new SocketInboundServer(cfg);
        server.start();
        try {
            assertTrue(server.isRunning());
        } finally {
            server.stop();
        }
    }

    // ============ connectionMode 契约 ============

    @Test
    @DisplayName("connectionMode=long · 构造时抛 BusinessException(Q20=C)")
    void connectionMode_long_抛异常() {
        Map<String, Object> m = minimalInbound(6500, "xml_boundary", "UTF-8");
        m.put("connectionMode", "long");
        SocketInboundConfig cfg = SocketInboundConfig.fromMap(m);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> new SocketInboundServer(cfg));
        assertTrue(ex.getMessage().contains("short"));
    }

    @Test
    @DisplayName("connectionMode short/空/null 允许")
    void connectionMode_short_允许() {
        for (String mode : new String[]{"short", "SHORT", null, ""}) {
            Map<String, Object> m = minimalInbound(6500, "xml_boundary", "UTF-8");
            if (mode != null) m.put("connectionMode", mode);
            SocketInboundConfig cfg = SocketInboundConfig.fromMap(m);
            new SocketInboundServer(cfg); // 不抛异常
        }
    }

    // ============ Config 反解 ============

    @Test
    @DisplayName("SocketInboundConfig.fromMap · 完整字段 + 缺省")
    void config_fromMap() {
        Map<String, Object> m = minimalInbound(6500, "xml_boundary", "UTF-8");
        SocketInboundConfig cfg = SocketInboundConfig.fromMap(m);
        assertEquals(6500, cfg.getPort());
        assertEquals(FramingType.XML_BOUNDARY, cfg.getFraming());
        assertEquals(Charset.forName("UTF-8"), cfg.getCharset());
        assertEquals(30000, cfg.getReadTimeoutMs(), "readTimeoutMs 缺省 30000");
        assertEquals(100, cfg.getMaxConnections(), "maxConnections 缺省 100");
        assertEquals("short", cfg.getConnectionMode());
        assertEquals("//FunctionId", cfg.getFunctionIdXPath());
    }

    @Test
    @DisplayName("port 非法 · 抛异常")
    void port_非法_抛异常() {
        Map<String, Object> m = minimalInbound(0, "xml_boundary", "UTF-8");
        assertThrows(BusinessException.class, () -> SocketInboundConfig.fromMap(m));
        m.put("port", 70000);
        assertThrows(BusinessException.class, () -> SocketInboundConfig.fromMap(m));
    }

    @Test
    @DisplayName("缺 framing/charset · 抛异常")
    void 缺必填_抛异常() {
        Map<String, Object> m = new HashMap<>();
        m.put("port", 6500);
        assertThrows(BusinessException.class, () -> SocketInboundConfig.fromMap(m));
        m.put("framing", "xml_boundary");
        assertThrows(BusinessException.class, () -> SocketInboundConfig.fromMap(m));
    }

    @Test
    @DisplayName("null/空 map · 抛异常")
    void null或空_抛异常() {
        assertThrows(BusinessException.class, () -> SocketInboundConfig.fromMap(null));
        assertThrows(BusinessException.class, () -> SocketInboundConfig.fromMap(new HashMap<>()));
    }

    // ============ Helper ============

    private static SocketInboundConfig configFrom(int port, String framing, String charset) {
        return SocketInboundConfig.fromMap(minimalInbound(port, framing, charset));
    }

    private static Map<String, Object> minimalInbound(int port, String framing, String charset) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port", port);
        m.put("framing", framing);
        m.put("charset", charset);
        return m;
    }

    private static int pickFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 用 Netty 客户端连一次 · 发一个 XML · 拿完应答即断 */
    private static void sendXmlOnce(int port, String xml, Charset charset) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup(1);
        try {
            Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(XmlBoundaryCodec.encoder());
                        }
                    });
            Channel channel = b.connect("127.0.0.1", port).sync().channel();
            channel.writeAndFlush(xml.getBytes(charset)).sync();
            channel.closeFuture().await(1, TimeUnit.SECONDS);
        } finally {
            group.shutdownGracefully(0, 300, TimeUnit.MILLISECONDS);
        }
    }
}
