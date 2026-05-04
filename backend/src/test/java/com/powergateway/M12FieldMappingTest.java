package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-2 字段映射配置验收测试
 * <p>
 * 验收标准：配置映射后保存，重新打开模板映射关系正确还原；预览结果字段映射符合配置。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1-2 字段映射配置")
class M12FieldMappingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    // ─────────────────────── 1. 新增模板（含映射规则） ───────────────────────

    @Test
    @DisplayName("新增模板：返回新 id，查询映射规则正确还原")
    void saveNew_andReload_mappingRulesRestored() throws Exception {
        String body = "{\"name\":\"测试映射模板\",\"srcFormat\":\"JSON\",\"targetFormat\":\"XML\","
                + "\"mappingRules\":["
                + "{\"srcField\":\"user_id\",\"targetField\":\"userId\",\"fixedValue\":null},"
                + "{\"srcField\":null,\"targetField\":\"type\",\"fixedValue\":\"1\"}"
                + "]}";

        MvcResult saveResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        Long id = ((Number) JsonPath.read(saveResult.getResponse().getContentAsString(), "$.data")).longValue();

        // 重新查询，验证映射规则还原
        mockMvc.perform(get("/api/template/" + id)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试映射模板"))
                .andExpect(jsonPath("$.data.srcFormat").value("JSON"))
                .andExpect(jsonPath("$.data.targetFormat").value("XML"))
                .andExpect(jsonPath("$.data.mappingRule").value(Matchers.containsString("user_id")))
                .andExpect(jsonPath("$.data.mappingRule").value(Matchers.containsString("userId")))
                .andExpect(jsonPath("$.data.mappingRule").value(Matchers.containsString("type")));
    }

    // ─────────────────────── 2. 更新模板（版本留存） ───────────────────────

    @Test
    @DisplayName("更新模板：旧版本 is_latest=0，新版本 version 递增")
    void updateTemplate_oldVersionArchived() throws Exception {
        String createBody = "{\"name\":\"版本测试模板\",\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\","
                + "\"mappingRules\":[{\"srcField\":\"a\",\"targetField\":\"b\",\"fixedValue\":null}]}";

        MvcResult createResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn();
        Long v1Id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data")).longValue();

        String updateBody = "{\"id\":" + v1Id + ",\"name\":\"版本测试模板\","
                + "\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\","
                + "\"mappingRules\":[{\"srcField\":\"a\",\"targetField\":\"b_new\",\"fixedValue\":null}]}";

        MvcResult updateResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Long v2Id = ((Number) JsonPath.read(updateResult.getResponse().getContentAsString(), "$.data")).longValue();

        mockMvc.perform(get("/api/template/" + v2Id)
                        .header("satoken", token))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.isLatest").value(1))
                .andExpect(jsonPath("$.data.mappingRule").value(Matchers.containsString("b_new")));
    }

    // ─────────────────────── 3. 映射预览（源字段映射） ───────────────────────

    @Test
    @DisplayName("预览：源字段正确映射到目标字段")
    void preview_srcFieldMapping_correct() throws Exception {
        String createBody = "{\"name\":\"预览测试模板\",\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\","
                + "\"mappingRules\":[{\"srcField\":\"user_id\",\"targetField\":\"userId\",\"fixedValue\":null}]}";

        MvcResult createResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data")).longValue();

        // 预览报文：{"user_id":"U001","name":"张三"}
        String previewBody = "{\"message\":\"{\\\"user_id\\\":\\\"U001\\\",\\\"name\\\":\\\"张三\\\"}\","
                + "\"format\":\"JSON\"}";

        mockMvc.perform(post("/api/template/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value("U001"))
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    // ─────────────────────── 4. 映射预览（固定值） ───────────────────────

    @Test
    @DisplayName("预览：固定值填入目标字段")
    void preview_fixedValue_overridesSrcField() throws Exception {
        String createBody = "{\"name\":\"固定值测试\",\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\","
                + "\"mappingRules\":[{\"srcField\":null,\"targetField\":\"type\",\"fixedValue\":\"FIXED_TYPE\"}]}";

        MvcResult createResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data")).longValue();

        String previewBody = "{\"message\":\"{\\\"type\\\":\\\"ORIGINAL\\\",\\\"x\\\":\\\"1\\\"}\","
                + "\"format\":\"JSON\"}";

        mockMvc.perform(post("/api/template/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.type").value("FIXED_TYPE"));
    }

    // ─────────────────────── 5. 自动匹配（同名字段） ───────────────────────

    @Test
    @DisplayName("预览：同名字段映射后值不变")
    void preview_sameNameMapping_valuePreserved() throws Exception {
        String createBody = "{\"name\":\"自动匹配测试\",\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\","
                + "\"mappingRules\":[{\"srcField\":\"amount\",\"targetField\":\"amount\",\"fixedValue\":null}]}";

        MvcResult createResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data")).longValue();

        String previewBody = "{\"message\":\"{\\\"amount\\\":\\\"99.00\\\"}\",\"format\":\"JSON\"}";

        mockMvc.perform(post("/api/template/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.amount").value("99.00"));
    }

    // ─────────────────────── 6. XML 格式预览 ───────────────────────

    @Test
    @DisplayName("预览：XML 源报文字段映射")
    void preview_xmlMessage_mappingCorrect() throws Exception {
        String createBody = "{\"name\":\"XML预览测试\",\"srcFormat\":\"XML\",\"targetFormat\":\"JSON\","
                + "\"mappingRules\":[{\"srcField\":\"orderId\",\"targetField\":\"order_id\",\"fixedValue\":null}]}";

        MvcResult createResult = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn();
        Long id = ((Number) JsonPath.read(createResult.getResponse().getContentAsString(), "$.data")).longValue();

        String xmlMsg = "<root><orderId>ORD-001</orderId><amount>100</amount></root>";
        Map<String, Object> previewReq = new HashMap<>();
        previewReq.put("message", xmlMsg);
        previewReq.put("format", "XML");

        mockMvc.perform(post("/api/template/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.order_id").value("ORD-001"));
    }

    // ─────────────────────── 7. 查询不存在模板 ───────────────────────

    @Test
    @DisplayName("查询不存在的模板返回 404")
    void getById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/template/999999")
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(404));
    }
}
