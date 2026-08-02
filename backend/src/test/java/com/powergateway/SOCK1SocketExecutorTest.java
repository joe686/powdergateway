package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.socket.SocketClient;
import com.powergateway.socket.SocketExecRequest;
import com.powergateway.socket.SocketExecResponse;
import com.powergateway.socket.SocketExecutor;
import com.powergateway.socket.codec.FramingType;
import com.powergateway.socket.codec.XmlBoundaryCodec;
import com.powergateway.utils.FormatConverter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-1 · SocketExecutor 集成测试(v0.3.0 · Task 6)。
 *
 * <p>@SpringBootTest 拉起完整 Spring 上下文 · 拿 FormatConverter/ObjectMapper Bean · 本地起 Netty ServerSocket 打桩 · 走 SocketExecutor 完整链路。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SOCK1SocketExecutorTest {

    @Autowired private FormatConverter formatConverter;
    @Autowired private ObjectMapper objectMapper;

    private final Charset UTF8 = StandardCharsets.UTF_8;

    private SocketExecutor newExecutor() {
        return new SocketExecutor(new SocketClient(), formatConverter, objectMapper);
    }

    // ============ 端到端 host 报文场景 ============

    @Test
    @DisplayName("XML_BOUNDARY + UTF-8 · host 场景端到端 · rawXml + flattened + latency 都返回")
    void 端到端_host场景_flat结果() throws Exception {
        String respXml = "<?xml version=\"1.0\"?><Response>"
                + "<FunctionId>181345</FunctionId>"
                + "<Result><Code>0</Code><Msg>OK</Msg></Result>"
                + "</Response>";

        try (TestServer server = TestServer.echo(FramingType.XML_BOUNDARY, UTF8, respXml)) {
            InterfaceConfig cfg = buildSocketConfig(server.port(), "xml_boundary", "UTF-8",
                    "<?xml version=\"1.0\"?><Request><FunctionId>{fnId}</FunctionId><Amount>{amt}</Amount></Request>",
                    "");

            Map<String, Object> params = new HashMap<>();
            params.put("fnId", "181345");
            params.put("amt", "100.00");

            SocketExecResponse resp = newExecutor().execute(cfg, params);
            assertNotNull(resp);
            assertEquals(respXml, resp.getRawXml());
            assertTrue(resp.getLatencyMs() >= 0);
            // 扁平化断言:嵌套 Result.Code / Result.Msg
            assertEquals("181345", resp.getFlattened().get("FunctionId"));
            assertEquals("0", resp.getFlattened().get("Result.Code"));
            assertEquals("OK", resp.getFlattened().get("Result.Msg"));
        }
    }

    @Test
    @DisplayName("BE4 + UTF-8 · 4 字节头端到端 · 应答扁平化")
    void be4端到端_扁平化() throws Exception {
        String respXml = "<Ack><Code>OK</Code></Ack>";

        try (TestServer server = TestServer.echo(FramingType.LENGTH_PREFIX_BE4, UTF8, respXml)) {
            InterfaceConfig cfg = buildSocketConfig(server.port(), "length_prefix_be4", "UTF-8",
                    "<Req>{x}</Req>", "");

            Map<String, Object> params = new HashMap<>();
            params.put("x", "hello");

            SocketExecResponse resp = newExecutor().execute(cfg, params);
            assertEquals("OK", resp.getFlattened().get("Code"));
        }
    }

    // ============ responseFlattenPrefix 支持 ============

    @Test
    @DisplayName("responseFlattenPrefix=resp. · 扁平化 key 加前缀")
    void responseFlattenPrefix_加前缀() throws Exception {
        String respXml = "<Ack><Code>0</Code></Ack>";
        try (TestServer server = TestServer.echo(FramingType.XML_BOUNDARY, UTF8, respXml)) {
            // 请求需完整 root · XmlBoundaryDecoder 找 </Req> 边界
            InterfaceConfig cfg = buildSocketConfig(server.port(), "xml_boundary", "UTF-8",
                    "<Req></Req>", "resp.");
            SocketExecResponse resp = newExecutor().execute(cfg, new HashMap<>());
            assertEquals("0", resp.getFlattened().get("resp..Code"),
                    "prefix=resp. 后 key=resp..Code(prefix + . + fieldname)");
        }
    }

    // ============ 请求模板占位替换 ============

    @Test
    @DisplayName("requestTemplate 占位替换 · {name} 从 params 取值")
    void requestTemplate_占位替换() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", "hello");
        params.put("c", null); // null → 空串
        String rendered = SocketExecutor.renderTemplate(
                "<R><A>{a}</A><B>{b}</B><C>{c}</C></R>", params);
        assertEquals("<R><A>1</A><B>hello</B><C></C></R>", rendered);
    }

    @Test
    @DisplayName("requestTemplate null 或 params 空 · 返回模板原文")
    void requestTemplate_边界() {
        assertEquals("<R/>", SocketExecutor.renderTemplate("<R/>", null));
        assertEquals("<R/>", SocketExecutor.renderTemplate("<R/>", new HashMap<>()));
    }

    // ============ 异常路径 ============

    @Test
    @DisplayName("config_json 缺 socket 段 · 抛 BusinessException")
    void config缺socket段_抛异常() {
        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setConfigJson("{\"other\":\"value\"}");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> newExecutor().execute(cfg, new HashMap<>()));
        assertTrue(ex.getMessage().contains("socket"));
    }

    @Test
    @DisplayName("config_json 为空 · 抛 BusinessException")
    void config为空_抛异常() {
        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setConfigJson(null);
        assertThrows(BusinessException.class,
                () -> newExecutor().execute(cfg, new HashMap<>()));

        cfg.setConfigJson("");
        assertThrows(BusinessException.class,
                () -> newExecutor().execute(cfg, new HashMap<>()));
    }

    @Test
    @DisplayName("connectionMode=long · 抛 BusinessException(SocketClient.checkConnectionMode 拦截)")
    void connectionMode_long_抛异常() throws Exception {
        InterfaceConfig cfg = buildSocketConfigFull(6500, "xml_boundary", "UTF-8",
                "<Req/>", "", "long");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> newExecutor().execute(cfg, new HashMap<>()));
        assertTrue(ex.getMessage().contains("short"));
    }

    // ============ Helper ============

    /** 构造一个最小 SOCKET InterfaceConfig(不入库,直接返给 SocketExecutor)。 */
    private InterfaceConfig buildSocketConfig(int port, String framing, String charset,
                                              String template, String flattenPrefix) throws Exception {
        return buildSocketConfigFull(port, framing, charset, template, flattenPrefix, "short");
    }

    private InterfaceConfig buildSocketConfigFull(int port, String framing, String charset,
                                                  String template, String flattenPrefix, String connMode) throws Exception {
        Map<String, Object> socket = new LinkedHashMap<>();
        socket.put("ip", "127.0.0.1");
        socket.put("port", port);
        socket.put("framing", framing);
        socket.put("charset", charset);
        socket.put("requestTemplate", template);
        socket.put("responseFlattenPrefix", flattenPrefix);
        socket.put("connectionMode", connMode);

        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("socket", socket);

        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setId(999L);
        cfg.setName("sock-test");
        cfg.setType("SOCKET");
        cfg.setStatus("published");
        cfg.setConfigJson(objectMapper.writeValueAsString(configJson));
        return cfg;
    }

    private static int pickFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 本地 Netty 打桩服务端 · 收帧后按固定 payload 应答并关闭连接 */
    private static class TestServer implements AutoCloseable {

        private final EventLoopGroup boss;
        private final EventLoopGroup worker;
        private final Channel serverChannel;
        private final int port;

        private TestServer(int port, FramingType framing, Charset charset, byte[] response) throws InterruptedException {
            this.boss = new NioEventLoopGroup(1);
            this.worker = new NioEventLoopGroup(1);
            ServerBootstrap sb = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            if (framing == FramingType.XML_BOUNDARY) {
                                ch.pipeline().addLast(XmlBoundaryCodec.decoder(charset));
                                ch.pipeline().addLast(XmlBoundaryCodec.encoder());
                            } else {
                                ch.pipeline().addLast(com.powergateway.socket.codec.LengthPrefixCodec.decoder(framing));
                                ch.pipeline().addLast(com.powergateway.socket.codec.LengthPrefixCodec.encoder(framing));
                            }
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<byte[]>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
                                    ctx.writeAndFlush(response)
                                            .addListener(ChannelFutureListener.CLOSE);
                                }
                            });
                        }
                    });
            ChannelFuture cf = sb.bind(port).sync();
            this.serverChannel = cf.channel();
            this.port = port;
        }

        static TestServer echo(FramingType framing, Charset charset, String response) throws InterruptedException {
            return new TestServer(pickFreePort(), framing, charset, response.getBytes(charset));
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
    }
}
