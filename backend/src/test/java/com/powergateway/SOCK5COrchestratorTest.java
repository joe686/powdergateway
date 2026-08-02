package com.powergateway;

import com.powergateway.socket.route.InboundSocketOrchestrator;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-5-C · v0.3.2 Task 3 · InboundSocketOrchestrator 纯函数单元测试。
 *
 * <p>完整 orchestrator.handle 集成走 Task 10 手工验收(需 pg-testkit + Eureka + HTTP mock)· 本类只覆盖纯静态工具。</p>
 */
@ActiveProfiles("test")
class SOCK5COrchestratorTest {

    // ============ extractFunctionId ============

    @Test
    @DisplayName("extractFunctionId · bank 报文 · 从 //bizHeader/FunctionId 提取")
    void extractFunctionId_bank() {
        String xml = "<?xml version=\"1.0\"?><Transaction><Body><request><bizHeader>"
                + "<FunctionId>180345</FunctionId><ExSerial>x</ExSerial></bizHeader>"
                + "<bizBody><Account>1021530253</Account></bizBody></request></Body></Transaction>";
        assertEquals("180345", InboundSocketOrchestrator.extractFunctionId(xml));
    }

    @Test
    @DisplayName("extractFunctionId · host 报文 · 从任意位置的 //FunctionId 提取")
    void extractFunctionId_host() {
        String xml = "<Transaction><FunctionId>181345</FunctionId><CustomerId>C001</CustomerId></Transaction>";
        assertEquals("181345", InboundSocketOrchestrator.extractFunctionId(xml));
    }

    @Test
    @DisplayName("extractFunctionId · 不含 FunctionId · 返 null")
    void extractFunctionId_不含() {
        String xml = "<Req><Other>x</Other></Req>";
        assertNull(InboundSocketOrchestrator.extractFunctionId(xml));
    }

    @Test
    @DisplayName("extractFunctionId · 格式非法 · 返 null 不抛")
    void extractFunctionId_格式非法() {
        assertNull(InboundSocketOrchestrator.extractFunctionId("<bad>not closed"));
        assertNull(InboundSocketOrchestrator.extractFunctionId(""));
    }

    // ============ extractBizBody / extractBizHeader ============

    @Test
    @DisplayName("extractBizBody · bank 嵌套 Transaction/Body/request/bizBody · 递归找到")
    void extractBizBody_bank() {
        Map<String, Object> xmlMap = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> bizBody = new LinkedHashMap<>();
        bizBody.put("Account", "1021530253");
        request.put("bizBody", bizBody);
        body.put("request", request);
        xmlMap.put("Body", body);

        Map<String, Object> out = InboundSocketOrchestrator.extractBizBody(xmlMap);
        assertEquals("1021530253", out.get("Account"));
    }

    @Test
    @DisplayName("extractBizBody · host 扁平 · 无 bizBody · fallback 整 xmlMap")
    void extractBizBody_host_fallback() {
        Map<String, Object> xmlMap = new HashMap<>();
        xmlMap.put("FunctionId", "181345");
        xmlMap.put("CustomerId", "C001");
        assertEquals(xmlMap, InboundSocketOrchestrator.extractBizBody(xmlMap));
    }

    @Test
    @DisplayName("extractBizHeader · bank 找到 · host 返 null")
    void extractBizHeader() {
        // bank
        Map<String, Object> bank = new LinkedHashMap<>();
        Map<String, Object> nested = new LinkedHashMap<>();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("FunctionId", "180345");
        nested.put("bizHeader", header);
        bank.put("Body", nested);
        assertEquals("180345", InboundSocketOrchestrator.extractBizHeader(bank).get("FunctionId"));

        // host
        Map<String, Object> host = new HashMap<>();
        host.put("FunctionId", "181345");
        assertNull(InboundSocketOrchestrator.extractBizHeader(host));
    }

    // ============ injectOriginalFunctionId · 用户 Q1 明确 ============

    @Test
    @DisplayName("injectOriginalFunctionId · Map 类型 · 直接塞")
    void inject_map() {
        Map<String, Object> req = new HashMap<>();
        req.put("k", "v");
        InboundSocketOrchestrator.injectOriginalFunctionId(req, "180345");
        assertEquals("180345", req.get("_originalFunctionId"));
    }

    @Test
    @DisplayName("injectOriginalFunctionId · bank 数组包 [{head,body}] · 塞第一元素")
    void inject_bank_list() {
        List<Object> bank = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("head", new HashMap<>());
        item.put("body", new HashMap<>());
        bank.add(item);
        InboundSocketOrchestrator.injectOriginalFunctionId(bank, "180345");
        assertEquals("180345", item.get("_originalFunctionId"));
    }

    @Test
    @DisplayName("injectOriginalFunctionId · 空 list · 不抛异常")
    void inject_空list() {
        List<Object> empty = new ArrayList<>();
        InboundSocketOrchestrator.injectOriginalFunctionId(empty, "180345");
        // 不抛异常即通过
    }

    // ============ buildResponseXml ============

    @Test
    @DisplayName("buildResponseXml · Map · <Response> 根 + 扁平字段")
    void buildResponseXml_map() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Code", "0");
        data.put("Msg", "OK");
        String xml = InboundSocketOrchestrator.buildResponseXml(data);
        assertTrue(xml.contains("<Response>"));
        assertTrue(xml.contains("<Code>0</Code>"));
        assertTrue(xml.contains("<Msg>OK</Msg>"));
    }

    @Test
    @DisplayName("buildResponseXml · Map 嵌套 · 保留结构")
    void buildResponseXml_嵌套() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", "1");
        data.put("Detail", inner);
        String xml = InboundSocketOrchestrator.buildResponseXml(data);
        assertTrue(xml.contains("<Detail><x>1</x></Detail>"));
    }

    @Test
    @DisplayName("buildResponseXml · Map 含 List · <item> 包每项")
    void buildResponseXml_list字段() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("Name", "A");
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("Name", "B");
        data.put("list", Arrays.asList(row1, row2));
        String xml = InboundSocketOrchestrator.buildResponseXml(data);
        assertTrue(xml.contains("<list><item><Name>A</Name></item><item><Name>B</Name></item></list>"));
    }
}
