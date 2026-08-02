package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.SocketExecRequest;
import com.powergateway.socket.codec.FramingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-1 · SocketExecRequest 反解单元测试(v0.3.0 · Task 5)。
 */
@ActiveProfiles("test")
class SOCK1SocketExecRequestParseTest {

    private Map<String, Object> minimal() {
        Map<String, Object> socket = new LinkedHashMap<>();
        socket.put("ip", "10.1.2.3");
        socket.put("port", 6500);
        socket.put("framing", "xml_boundary");
        socket.put("charset", "UTF-8");
        socket.put("requestTemplate", "<?xml version=\"1.0\"?><Req>{param}</Req>");
        return socket;
    }

    @Test
    @DisplayName("完整字段全填 · 反解正确")
    void 完整字段全填_反解正确() {
        Map<String, Object> socket = minimal();
        socket.put("connTimeoutMs", 5000);
        socket.put("readTimeoutMs", 15000);
        socket.put("connectionMode", "short");
        socket.put("responseFlattenPrefix", "resp.");

        SocketExecRequest req = SocketExecRequest.fromMap(socket);
        assertEquals("10.1.2.3", req.getIp());
        assertEquals(6500, req.getPort());
        assertEquals(FramingType.XML_BOUNDARY, req.getFraming());
        assertEquals(Charset.forName("UTF-8"), req.getCharset());
        assertEquals(5000, req.getConnTimeoutMs());
        assertEquals(15000, req.getReadTimeoutMs());
        assertEquals("short", req.getConnectionMode());
        assertEquals("<?xml version=\"1.0\"?><Req>{param}</Req>", req.getRequestTemplate());
        assertEquals("resp.", req.getResponseFlattenPrefix());
    }

    @Test
    @DisplayName("只填必填 · 可选字段走缺省")
    void 只填必填_可选走缺省() {
        SocketExecRequest req = SocketExecRequest.fromMap(minimal());
        assertEquals(3000, req.getConnTimeoutMs(), "connTimeoutMs 缺省 3000");
        assertEquals(10000, req.getReadTimeoutMs(), "readTimeoutMs 缺省 10000");
        assertEquals("short", req.getConnectionMode(), "connectionMode 缺省 short");
        assertEquals("", req.getResponseFlattenPrefix(), "responseFlattenPrefix 缺省空");
    }

    // ============ 必填缺失(Q5=C + Q6=B · 无默认)============

    @Test
    @DisplayName("null/空 map · 抛异常")
    void null或空map_抛异常() {
        assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(null));
        assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(new HashMap<>()));
    }

    @Test
    @DisplayName("缺 ip · 抛 BusinessException")
    void 缺ip_抛异常() {
        Map<String, Object> socket = minimal();
        socket.remove("ip");
        BusinessException ex = assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
        assertTrue(ex.getMessage().contains("ip"));
    }

    @Test
    @DisplayName("缺 port · 抛异常")
    void 缺port_抛异常() {
        Map<String, Object> socket = minimal();
        socket.remove("port");
        assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
    }

    @Test
    @DisplayName("缺 framing · 抛异常 · Q5=C 无默认")
    void 缺framing_抛异常_Q5C() {
        Map<String, Object> socket = minimal();
        socket.remove("framing");
        BusinessException ex = assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
        assertTrue(ex.getMessage().contains("framing"));
    }

    @Test
    @DisplayName("缺 charset · 抛异常 · Q6=B 无默认")
    void 缺charset_抛异常_Q6B() {
        Map<String, Object> socket = minimal();
        socket.remove("charset");
        BusinessException ex = assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
        assertTrue(ex.getMessage().contains("charset"));
    }

    @Test
    @DisplayName("缺 requestTemplate · 抛异常")
    void 缺requestTemplate_抛异常() {
        Map<String, Object> socket = minimal();
        socket.remove("requestTemplate");
        BusinessException ex = assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
        assertTrue(ex.getMessage().contains("requestTemplate"));
    }

    // ============ 非法值 ============

    @Test
    @DisplayName("port 为 0 或负数 · 抛异常")
    void port_非正数_抛异常() {
        Map<String, Object> socket = minimal();
        socket.put("port", 0);
        assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
        socket.put("port", -1);
        assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
    }

    @Test
    @DisplayName("framing 非法值 · 抛异常并列出白名单")
    void framing_非法_抛异常() {
        Map<String, Object> socket = minimal();
        socket.put("framing", "unknown");
        assertThrows(IllegalArgumentException.class, () -> SocketExecRequest.fromMap(socket));
    }

    @Test
    @DisplayName("charset 非白名单 · 抛 BusinessException")
    void charset_非白名单_抛异常() {
        Map<String, Object> socket = minimal();
        socket.put("charset", "ISO-8859-1");
        BusinessException ex = assertThrows(BusinessException.class, () -> SocketExecRequest.fromMap(socket));
        assertTrue(ex.getMessage().contains("UTF-8"));
    }

    // ============ 三 framing + 双 charset 覆盖 ============

    @Test
    @DisplayName("BE4 + GBK 覆盖")
    void be4_GBK() {
        Map<String, Object> socket = minimal();
        socket.put("framing", "length_prefix_be4");
        socket.put("charset", "GBK");
        SocketExecRequest req = SocketExecRequest.fromMap(socket);
        assertEquals(FramingType.LENGTH_PREFIX_BE4, req.getFraming());
        assertEquals(Charset.forName("GBK"), req.getCharset());
    }

    @Test
    @DisplayName("BE8 + UTF-8 覆盖")
    void be8_UTF8() {
        Map<String, Object> socket = minimal();
        socket.put("framing", "length_prefix_be8");
        SocketExecRequest req = SocketExecRequest.fromMap(socket);
        assertEquals(FramingType.LENGTH_PREFIX_BE8, req.getFraming());
    }
}
