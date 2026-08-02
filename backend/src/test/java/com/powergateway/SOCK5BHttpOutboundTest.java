package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.outbound.HttpOutboundExecutor;
import com.powergateway.socket.outbound.HttpOutboundRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-5-B · v0.3.2 Task 4 · HttpOutboundExecutor 单元测试。
 *
 * <p>测试环境无真实 Eureka · 只覆盖 fromMap 反解 + exec 时 discover 未命中抛 503。</p>
 * <p>完整 end-to-end 走 Task 10 手工验收(pg-testkit + 用户 Eureka 实例)。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SOCK5BHttpOutboundTest {

    @Autowired private HttpOutboundExecutor executor;

    @Test
    @DisplayName("HttpOutboundRequest.fromMap · 完整字段 + 缺省")
    void fromMap_完整() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationName", "lcpt-hstc-online-query");
        m.put("path", "/hstc/query/online/");
        m.put("method", "post");
        m.put("connTimeoutMs", 5000);
        m.put("readTimeoutMs", 15000);
        HttpOutboundRequest req = HttpOutboundRequest.fromMap(m);
        assertEquals("lcpt-hstc-online-query", req.getApplicationName());
        assertEquals("/hstc/query/online/", req.getPath());
        assertEquals("POST", req.getMethod(), "method 大写规范化");
        assertEquals(5000, req.getConnTimeoutMs());
        assertEquals(15000, req.getReadTimeoutMs());
    }

    @Test
    @DisplayName("HttpOutboundRequest.fromMap · method 缺省 POST · 超时缺省 3000/10000")
    void fromMap_缺省() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationName", "app-A");
        m.put("path", "/a");
        HttpOutboundRequest req = HttpOutboundRequest.fromMap(m);
        assertEquals("POST", req.getMethod());
        assertEquals(3000, req.getConnTimeoutMs());
        assertEquals(10000, req.getReadTimeoutMs());
    }

    @Test
    @DisplayName("HttpOutboundRequest.fromMap · 缺 applicationName/path · 抛异常")
    void fromMap_缺必填() {
        Map<String, Object> m = new HashMap<>();
        assertThrows(BusinessException.class, () -> HttpOutboundRequest.fromMap(m));
        m.put("path", "/a");
        assertThrows(BusinessException.class, () -> HttpOutboundRequest.fromMap(m));
        m.clear();
        m.put("applicationName", "a");
        assertThrows(BusinessException.class, () -> HttpOutboundRequest.fromMap(m));
    }

    @Test
    @DisplayName("HttpOutboundRequest.fromMap · null/空 · 抛异常")
    void fromMap_null() {
        assertThrows(BusinessException.class, () -> HttpOutboundRequest.fromMap(null));
        assertThrows(BusinessException.class, () -> HttpOutboundRequest.fromMap(new HashMap<>()));
    }

    @Test
    @DisplayName("exec · Eureka 未发现应用 · 抛 503")
    void exec_discover未命中_503() {
        HttpOutboundRequest req = HttpOutboundRequest.fromMap(mapOf("applicationName", "NON-EXIST-APP-99999", "path", "/x"));
        Map<String, Object> body = new HashMap<>();
        body.put("k", "v");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.exec(req, body, "test-trace-id", "180345"));
        assertEquals(503, ex.getCode());
        assertTrue(ex.getMessage().contains("Eureka 未发现"));
        assertTrue(ex.getMessage().contains("NON-EXIST-APP-99999"));
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }
}
