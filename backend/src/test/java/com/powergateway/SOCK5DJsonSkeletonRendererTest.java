package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.route.JsonSkeletonConfig;
import com.powergateway.socket.route.JsonSkeletonRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-5-D · v0.3.2 Task 6 · JsonSkeletonRenderer 单元测试。
 */
@ActiveProfiles("test")
class SOCK5DJsonSkeletonRendererTest {

    // ============ parseSegments · 简化 JSONPath 语法 ============

    @Test
    @DisplayName("parseSegments · $ 根返空列表")
    void parseSegments_根() {
        assertTrue(JsonSkeletonRenderer.parseSegments("$").isEmpty());
        assertTrue(JsonSkeletonRenderer.parseSegments("").isEmpty());
        assertTrue(JsonSkeletonRenderer.parseSegments(null).isEmpty());
    }

    @Test
    @DisplayName("parseSegments · $[0].body → [0, body]")
    void parseSegments_bank() {
        assertEquals(Arrays.asList(0, "body"), JsonSkeletonRenderer.parseSegments("$[0].body"));
    }

    @Test
    @DisplayName("parseSegments · $.a.b[2].c → [a, b, 2, c]")
    void parseSegments_混合() {
        assertEquals(Arrays.asList("a", "b", 2, "c"), JsonSkeletonRenderer.parseSegments("$.a.b[2].c"));
    }

    @Test
    @DisplayName("parseSegments · 语法非法抛异常")
    void parseSegments_非法() {
        assertThrows(BusinessException.class, () -> JsonSkeletonRenderer.parseSegments("$.[bad]"));
        assertThrows(BusinessException.class, () -> JsonSkeletonRenderer.parseSegments("$..a"));
    }

    // ============ Bank wrap · 数组包 head + body ============

    @Test
    @DisplayName("wrap bank · 请求 → [{head:{...},body:{...}}] 结构")
    void wrap_bank_请求() {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        cfg.setRequestBodyPath("$[0].body");
        cfg.setRequestHeadPath("$[0].head");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Account", "1021530253");
        body.put("BankAcc", "6231891100152858291");
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("FunctionId", "180345");
        head.put("ExSerial", "0586202607221406236816015");

        Object result = JsonSkeletonRenderer.wrapRequest(body, head, cfg);
        assertTrue(result instanceof List);
        List<?> list = (List<?>) result;
        assertEquals(1, list.size());
        Map<?, ?> item = (Map<?, ?>) list.get(0);
        assertEquals(body, item.get("body"));
        assertEquals(head, item.get("head"));
    }

    // ============ Host flat · skeleton 4 路径全空 ============

    @Test
    @DisplayName("wrap host flat · skeleton 空 · head+body 合并返")
    void wrap_host_flat() {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        assertTrue(cfg.isFlat());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("FunctionId", "181345");
        body.put("CustomerId", "C001");

        Object result = JsonSkeletonRenderer.wrapRequest(body, null, cfg);
        // flat 无 head · 直接返 body
        assertEquals(body, result);
    }

    @Test
    @DisplayName("wrap host flat · head+body 都有 · 合并")
    void wrap_host_flat_合并() {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Data", "x");
        Map<String, Object> head = new LinkedHashMap<>();
        head.put("FunctionId", "181345");

        Object result = JsonSkeletonRenderer.wrapRequest(body, head, cfg);
        assertTrue(result instanceof Map);
        Map<?, ?> merged = (Map<?, ?>) result;
        assertEquals("181345", merged.get("FunctionId"));
        assertEquals("x", merged.get("Data"));
    }

    // ============ Bank unwrap · 从数组包提业务体 ============

    @Test
    @DisplayName("unwrap bank · responseBodyPath 提 body")
    void unwrap_bank_body() {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        cfg.setResponseBodyPath("$[0].body");

        // 构造 bank 应答:[{head:{}, body:{TotNum:"1", RetNum:"1"}}]
        List<Object> bankJson = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("TotNum", "1");
        body.put("RetNum", "1");
        item.put("body", body);
        bankJson.add(item);

        Object unwrapped = JsonSkeletonRenderer.unwrapResponse(bankJson, cfg);
        assertEquals(body, unwrapped);
    }

    @Test
    @DisplayName("unwrap bank · responseListPath 优先提 list")
    void unwrap_bank_list优先() {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        cfg.setResponseBodyPath("$[0].body");
        cfg.setResponseListPath("$[0].body.list");

        // [{head:{}, body:{list:[{GroupName:"测试组合宝"}]}}]
        List<Object> bankJson = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        List<Object> innerList = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("GroupName", "测试组合宝");
        innerList.add(row);
        body.put("list", innerList);
        item.put("body", body);
        bankJson.add(item);

        Object unwrapped = JsonSkeletonRenderer.unwrapResponse(bankJson, cfg);
        assertEquals(innerList, unwrapped);
    }

    @Test
    @DisplayName("unwrap host flat · skeleton 空返原样")
    void unwrap_host_flat() {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        Map<String, Object> flat = new HashMap<>();
        flat.put("FunctionId", "181345");
        Object unwrapped = JsonSkeletonRenderer.unwrapResponse(flat, cfg);
        assertEquals(flat, unwrapped);
    }

    // ============ getByPath / setByPath 边界 ============

    @Test
    @DisplayName("getByPath · 路径走不通返 null · 不抛异常")
    void getByPath_走不通_返null() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", "x");
        assertNull(JsonSkeletonRenderer.getByPath(m, "$.b.c"));
        assertNull(JsonSkeletonRenderer.getByPath(m, "$[0]"));
        assertNull(JsonSkeletonRenderer.getByPath(null, "$.a"));
    }

    @Test
    @DisplayName("setByPath · 中间节点自动创建")
    void setByPath_中间自动创建() {
        Object root = JsonSkeletonRenderer.setByPath(new ArrayList<>(), "$[0].body.list[0].name", "Alice");
        assertEquals("Alice", JsonSkeletonRenderer.getByPath(root, "$[0].body.list[0].name"));
    }

    @Test
    @DisplayName("JsonSkeletonConfig.fromMap · 完整 4 字段")
    void config_fromMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requestBodyPath", "$[0].body");
        m.put("requestHeadPath", "$[0].head");
        m.put("responseBodyPath", "$[0].body");
        m.put("responseListPath", "$[0].body.list");
        JsonSkeletonConfig cfg = JsonSkeletonConfig.fromMap(m);
        assertNotNull(cfg);
        assertEquals("$[0].body", cfg.getRequestBodyPath());
        assertEquals("$[0].head", cfg.getRequestHeadPath());
        assertEquals("$[0].body", cfg.getResponseBodyPath());
        assertEquals("$[0].body.list", cfg.getResponseListPath());
        assertTrue(!cfg.isFlat());
    }

    @Test
    @DisplayName("JsonSkeletonConfig.fromMap · null/空返 flat 模式")
    void config_null_flat() {
        assertTrue(JsonSkeletonConfig.fromMap(null).isFlat());
        assertTrue(JsonSkeletonConfig.fromMap(new HashMap<>()).isFlat());
    }
}
