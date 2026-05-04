package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M1-7 端口分发路由验收测试（TDD Red → Green）
 * <p>
 * 验收标准（来自需求文档）：
 * <ol>
 *   <li>配置路由后调用 /api/dispatch，请求报文经请求模板转换后转发 B 系统</li>
 *   <li>B 系统应答经应答模板转换后返回给调用方</li>
 *   <li>不配置应答模板时，B 系统原始应答直接透传</li>
 *   <li>/api/convert 与 /api/dispatch 可独立使用互不影响</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1-7 端口分发路由")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M17PortRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;
    private String token;

    private static final String B_SYSTEM_URL = "http://mock-b-system/api/receive";
    private static final String CHANNEL_CODE = "CH_M17_" + System.currentTimeMillis();

    @BeforeEach
    void setup() throws Exception {
        // 每个测试重置 MockRestServiceServer
        mockServer = MockRestServiceServer.createServer(restTemplate);

        // 登录获取 token
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    // ─────────────────── 辅助方法 ───────────────────

    /** 创建转换模板，返回 id */
    private Long createTemplate(String name, String srcFormat, String targetFormat,
                                 String mappingRulesJson) throws Exception {
        String body = String.format(
                "{\"name\":\"%s\",\"srcFormat\":\"%s\",\"targetFormat\":\"%s\",\"mappingRules\":%s}",
                name, srcFormat, targetFormat, mappingRulesJson);
        MvcResult r = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
    }

    /** 创建渠道配置，返回 id */
    private Long createChannel(String channelCode, String identifyField, Long templateId) throws Exception {
        String body = String.format(
                "{\"channelCode\":\"%s\",\"channelName\":\"测试渠道%s\","
                        + "\"identifyField\":\"%s\",\"templateId\":%d}",
                channelCode, channelCode, identifyField, templateId);
        MvcResult r = mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
    }

    /** 创建端口路由，返回 id */
    private Long createPortRoute(String channelCode, String portAddress,
                                  Long requestTemplateId, Long responseTemplateId) throws Exception {
        String respTplField = responseTemplateId == null ? "null" : responseTemplateId.toString();
        String reqTplField = requestTemplateId == null ? "null" : requestTemplateId.toString();
        String body = String.format(
                "{\"channelCode\":\"%s\",\"portAddress\":\"%s\","
                        + "\"portMethod\":\"POST\",\"timeout\":3000,\"retryCount\":1,"
                        + "\"requestTemplateId\":%s,\"responseTemplateId\":%s}",
                channelCode, portAddress, reqTplField, respTplField);
        MvcResult r = mockMvc.perform(post("/api/port-route/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
    }

    // ─────────────────── 1. CRUD 测试 ───────────────────

    @Test
    @Order(1)
    @DisplayName("新增端口路由：返回 id，列表可查到")
    void savePortRoute_new_returnId() throws Exception {
        Long reqTplId = createTemplate("M17_REQ_TPL_" + System.currentTimeMillis(),
                "JSON", "XML", "[]");

        Long routeId = createPortRoute("CH_CRUD_" + System.currentTimeMillis(),
                "http://test-b/api", reqTplId, null);

        assertNotNull(routeId);
        assertTrue(routeId > 0);
    }

    @Test
    @Order(2)
    @DisplayName("查询端口路由列表：分页返回正确结构")
    void listPortRoutes_returnsPaginatedResult() throws Exception {
        Long reqTplId = createTemplate("M17_LIST_TPL_" + System.currentTimeMillis(),
                "JSON", "XML", "[]");
        createPortRoute("CH_LIST_" + System.currentTimeMillis(), "http://list-b/api", reqTplId, null);

        mockMvc.perform(get("/api/port-route/list")
                        .header("satoken", token)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    @Order(3)
    @DisplayName("更新端口路由：修改 portAddress 后查询验证变更")
    void savePortRoute_update_changesPersisted() throws Exception {
        Long reqTplId = createTemplate("M17_UPD_TPL_" + System.currentTimeMillis(),
                "JSON", "XML", "[]");
        Long routeId = createPortRoute("CH_UPD_" + System.currentTimeMillis(),
                "http://old-b/api", reqTplId, null);

        // 更新：修改 portAddress
        String updateBody = String.format(
                "{\"id\":%d,\"channelCode\":\"CH_UPD_UPDATED\",\"portAddress\":\"http://new-b/api\","
                        + "\"portMethod\":\"POST\",\"timeout\":5000,\"retryCount\":2,"
                        + "\"requestTemplateId\":%d,\"responseTemplateId\":null}",
                routeId, reqTplId);
        mockMvc.perform(post("/api/port-route/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("删除端口路由：逻辑删除后不再出现在列表")
    void deletePortRoute_notInListAfterDeletion() throws Exception {
        Long reqTplId = createTemplate("M17_DEL_TPL_" + System.currentTimeMillis(),
                "JSON", "XML", "[]");
        Long routeId = createPortRoute("CH_DEL_" + System.currentTimeMillis(),
                "http://del-b/api", reqTplId, null);

        mockMvc.perform(delete("/api/port-route/" + routeId)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ─────────────────── 2. 端口连通测试 ───────────────────

    @Test
    @Order(5)
    @DisplayName("端口连通测试：目标端口可达，返回 success=true")
    void testConnectivity_portReachable_returnsSuccess() throws Exception {
        Long reqTplId = createTemplate("M17_CONN_TPL_" + System.currentTimeMillis(),
                "JSON", "XML", "[]");
        Long routeId = createPortRoute("CH_CONN_" + System.currentTimeMillis(),
                B_SYSTEM_URL, reqTplId, null);

        // 模拟 B 系统 GET 探活请求返回 200
        mockServer.expect(requestTo(B_SYSTEM_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        mockMvc.perform(post("/api/port-route/" + routeId + "/test")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

        mockServer.verify();
    }

    @Test
    @Order(6)
    @DisplayName("端口连通测试：目标端口不可达，返回 success=false")
    void testConnectivity_portUnreachable_returnsFalse() throws Exception {
        Long reqTplId = createTemplate("M17_CONN2_TPL_" + System.currentTimeMillis(),
                "JSON", "XML", "[]");
        Long routeId = createPortRoute("CH_CONN2_" + System.currentTimeMillis(),
                B_SYSTEM_URL, reqTplId, null);

        // 模拟 B 系统不可达
        mockServer.expect(requestTo(B_SYSTEM_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        mockMvc.perform(post("/api/port-route/" + routeId + "/test")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(false));

        mockServer.verify();
    }

    // ─────────────────── 3. 分发接口核心验收测试 ───────────────────

    @Test
    @Order(7)
    @DisplayName("验收标准1+2：请求经请求模板转换后转发，B应答经应答模板转换后返回")
    void dispatch_biDirectionalConversion_correct() throws Exception {
        String uniqueCode = "CH_BIDIR_" + System.currentTimeMillis();

        // 1. 请求模板：JSON → XML（A→B），字段 userId → user_id
        Long reqTplId = createTemplate("M17_REQ_" + uniqueCode, "JSON", "XML",
                "[{\"srcField\":\"userId\",\"targetField\":\"user_id\",\"fixedValue\":null}]");

        // 2. 应答模板：XML → JSON（B→A），字段 result_code → resultCode
        Long respTplId = createTemplate("M17_RESP_" + uniqueCode, "XML", "JSON",
                "[{\"srcField\":\"result_code\",\"targetField\":\"resultCode\",\"fixedValue\":null}]");

        // 3. 渠道配置（dispatch 需要通过渠道路由找到 port_route）
        createChannel(uniqueCode, "channel", reqTplId);

        // 4. 端口路由
        createPortRoute(uniqueCode, B_SYSTEM_URL, reqTplId, respTplId);

        // 5. Mock B 系统：接收 POST，返回 XML 应答
        String bResponse = "<root><result_code>SUCCESS</result_code></root>";
        mockServer.expect(requestTo(B_SYSTEM_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(bResponse, MediaType.TEXT_XML));

        // 6. 调用 /api/dispatch，报文含渠道标识
        String message = String.format("{\"userId\":\"U001\",\"channel\":\"%s\"}", uniqueCode);
        String body = String.format(
                "{\"message\":%s,\"srcFormat\":\"JSON\"}",
                objectMapper.writeValueAsString(message));

        MvcResult result = mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.response").exists())
                .andExpect(jsonPath("$.data.channelCode").value(uniqueCode))
                .andExpect(jsonPath("$.data.costMs").isNumber())
                .andReturn();

        // 7. 验证：应答经应答模板转换，包含 resultCode（映射后字段名），值为 SUCCESS
        String response = JsonPath.read(result.getResponse().getContentAsString(), "$.data.response");
        assertTrue(response.contains("resultCode"), "应答应包含映射后字段 resultCode，实际：" + response);
        assertTrue(response.contains("SUCCESS"), "应答应包含 B 返回值 SUCCESS，实际：" + response);

        mockServer.verify();
    }

    @Test
    @Order(8)
    @DisplayName("验收标准3：不配置应答模板时，B 应答原样透传")
    void dispatch_noResponseTemplate_bResponsePassthrough() throws Exception {
        String uniqueCode = "CH_PASSTHRU_" + System.currentTimeMillis();

        Long reqTplId = createTemplate("M17_PASS_REQ_" + uniqueCode, "JSON", "XML",
                "[{\"srcField\":\"orderId\",\"targetField\":\"order_id\",\"fixedValue\":null}]");

        createChannel(uniqueCode, "channel", reqTplId);

        // 不配置应答模板（responseTemplateId = null）
        createPortRoute(uniqueCode, B_SYSTEM_URL, reqTplId, null);

        String bResponse = "{\"status\":\"OK\",\"code\":\"0000\"}";
        mockServer.expect(requestTo(B_SYSTEM_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(bResponse, MediaType.APPLICATION_JSON));

        String message = String.format("{\"orderId\":\"P001\",\"channel\":\"%s\"}", uniqueCode);
        String body = String.format(
                "{\"message\":%s,\"srcFormat\":\"JSON\"}",
                objectMapper.writeValueAsString(message));

        MvcResult result = mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        // B 应答原样透传
        String response = JsonPath.read(result.getResponse().getContentAsString(), "$.data.response");
        assertEquals(bResponse, response, "无应答模板时 B 应答应原样透传");

        mockServer.verify();
    }

    @Test
    @Order(9)
    @DisplayName("验收标准4：/api/convert 与 /api/dispatch 可独立使用互不影响")
    void dispatch_and_convert_independent() throws Exception {
        // /api/convert 独立使用（不需要 port_route）
        Long tplId = createTemplate("M17_IND_TPL_" + System.currentTimeMillis(),
                "JSON", "XML",
                "[{\"srcField\":\"name\",\"targetField\":\"name\",\"fixedValue\":null}]");

        String convertBody = String.format(
                "{\"templateId\":%d,\"message\":\"{\\\"name\\\":\\\"Alice\\\"}\",\"srcFormat\":\"JSON\"}",
                tplId);

        mockMvc.perform(post("/api/convert")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(convertBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.result").exists())
                .andExpect(jsonPath("$.data.targetFormat").value("XML"));
    }

    @Test
    @Order(10)
    @DisplayName("分发失败：渠道未命中任何路由配置，返回 code=400")
    void dispatch_channelNotFound_returns400() throws Exception {
        // 报文不含任何渠道标识，也无对应 port_route
        String body = "{\"message\":\"{\\\"foo\\\":\\\"bar\\\"}\",\"srcFormat\":\"JSON\"}";

        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(11)
    @DisplayName("分发失败：渠道有配置但无端口路由，返回 code=404")
    void dispatch_channelExistsButNoPortRoute_returns404() throws Exception {
        String uniqueCode = "CH_NORT_" + System.currentTimeMillis();

        Long tplId = createTemplate("M17_NORT_TPL_" + uniqueCode, "JSON", "XML", "[]");
        // 创建渠道但不创建 port_route
        createChannel(uniqueCode, "channel", tplId);

        String message = String.format("{\"data\":\"test\",\"channel\":\"%s\"}", uniqueCode);
        String body = String.format(
                "{\"message\":%s,\"srcFormat\":\"JSON\"}",
                objectMapper.writeValueAsString(message));

        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @Order(12)
    @DisplayName("分发失败：B 系统不可用（HTTP 500），返回 code=502")
    void dispatch_bSystemUnavailable_returns502() throws Exception {
        String uniqueCode = "CH_502_" + System.currentTimeMillis();

        Long reqTplId = createTemplate("M17_502_TPL_" + uniqueCode, "JSON", "XML", "[]");
        createChannel(uniqueCode, "channel", reqTplId);
        createPortRoute(uniqueCode, B_SYSTEM_URL, reqTplId, null);

        // B 系统返回 500
        mockServer.expect(requestTo(B_SYSTEM_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String message = String.format("{\"data\":\"test\",\"channel\":\"%s\"}", uniqueCode);
        String body = String.format(
                "{\"message\":%s,\"srcFormat\":\"JSON\"}",
                objectMapper.writeValueAsString(message));

        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502));
    }

    @Test
    @Order(13)
    @DisplayName("缺少 message：返回 code=400")
    void dispatch_missingMessage_returns400() throws Exception {
        String body = "{\"srcFormat\":\"JSON\"}";
        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(14)
    @DisplayName("新增端口路由：渠道编码为空，返回 code=400")
    void savePortRoute_emptyChannelCode_returns400() throws Exception {
        String body = "{\"channelCode\":\"\",\"portAddress\":\"http://b/api\","
                + "\"requestTemplateId\":1,\"responseTemplateId\":null}";
        mockMvc.perform(post("/api/port-route/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(15)
    @DisplayName("新增端口路由：portAddress 为空，返回 code=400")
    void savePortRoute_emptyPortAddress_returns400() throws Exception {
        String body = "{\"channelCode\":\"CH_VAL\",\"portAddress\":\"\","
                + "\"requestTemplateId\":1,\"responseTemplateId\":null}";
        mockMvc.perform(post("/api/port-route/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
