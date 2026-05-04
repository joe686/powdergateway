package com.powergateway;

import com.jayway.jsonpath.JsonPath;
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

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M1-4 渠道模板管理验收测试
 * <p>
 * 验收标准：
 * 1. 渠道 CRUD 全流程（新增/列表/查单条/更新/删除）
 * 2. 渠道编码唯一性校验（重复编码返回 400）
 * 3. ChannelRouter.match 自动路由：传含识别字段的 JSON 报文，返回关联模板 id
 * 4. 删除不存在的渠道返回 404
 * 5. 必填字段缺失返回 400
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1-4 渠道模板管理")
class M14ChannelTemplateTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    // ─────────────────────── 辅助方法 ───────────────────────

    /** 先创建一个模板，返回模板 id，供渠道配置关联 */
    private Long createTemplate() throws Exception {
        String body = "{\"name\":\"M14测试模板_" + System.currentTimeMillis() + "\","
                + "\"srcFormat\":\"JSON\",\"targetFormat\":\"XML\","
                + "\"mappingRules\":[{\"srcField\":\"a\",\"targetField\":\"b\",\"fixedValue\":null}]}";
        MvcResult r = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
    }

    /** 新增渠道配置，返回新建 id */
    private Long createChannel(String channelCode, String identifyField, Long templateId) throws Exception {
        String body = String.format(
                "{\"channelCode\":\"%s\",\"channelName\":\"渠道%s\","
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

    // ─────────────────────── 1. 新增渠道 ───────────────────────

    @Test
    @DisplayName("新增渠道：保存后列表可查到，字段值正确")
    void save_newChannel_appearsInList() throws Exception {
        Long tplId = createTemplate();
        String code = "CH_NEW_" + System.currentTimeMillis();
        Long id = createChannel(code, "channelCode", tplId);

        mockMvc.perform(get("/api/channel/" + id)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.channelCode").value(code))
                .andExpect(jsonPath("$.data.identifyField").value("channelCode"))
                .andExpect(jsonPath("$.data.templateId").value(tplId));
    }

    // ─────────────────────── 2. 渠道列表 ───────────────────────

    @Test
    @DisplayName("渠道列表：新增后列表至少包含该条")
    void list_afterCreate_containsNewChannel() throws Exception {
        Long tplId = createTemplate();
        String code = "CH_LIST_" + System.currentTimeMillis();
        createChannel(code, "src", tplId);

        MvcResult r = mockMvc.perform(get("/api/channel/list")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        List<?> data = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assert data.size() >= 1 : "列表至少有 1 条";
    }

    // ─────────────────────── 3. 更新渠道 ───────────────────────

    @Test
    @DisplayName("更新渠道：channelName 和 identifyField 正确变更")
    void update_channel_fieldsChanged() throws Exception {
        Long tplId = createTemplate();
        String code = "CH_UPD_" + System.currentTimeMillis();
        Long id = createChannel(code, "oldField", tplId);

        String updateBody = String.format(
                "{\"id\":%d,\"channelCode\":\"%s\",\"channelName\":\"新名称\","
                + "\"identifyField\":\"newField\",\"templateId\":%d}",
                id, code, tplId);
        mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/channel/" + id)
                        .header("satoken", token))
                .andExpect(jsonPath("$.data.channelName").value("新名称"))
                .andExpect(jsonPath("$.data.identifyField").value("newField"));
    }

    // ─────────────────────── 4. 逻辑删除 ───────────────────────

    @Test
    @DisplayName("逻辑删除：删除后按 id 查询返回 404")
    void delete_channel_notFoundAfterDelete() throws Exception {
        Long tplId = createTemplate();
        Long id = createChannel("CH_DEL_" + System.currentTimeMillis(), "f", tplId);

        mockMvc.perform(delete("/api/channel/" + id)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/channel/" + id)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("删除不存在的渠道返回 404")
    void delete_notExist_returns404() throws Exception {
        mockMvc.perform(delete("/api/channel/999999")
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ─────────────────────── 5. 渠道编码唯一性 ──────────────────

    @Test
    @DisplayName("渠道编码重复：第二次新增返回 400")
    void save_duplicateChannelCode_returns400() throws Exception {
        Long tplId = createTemplate();
        String code = "CH_DUP_" + System.currentTimeMillis();
        createChannel(code, "f", tplId);

        String dupBody = String.format(
                "{\"channelCode\":\"%s\",\"channelName\":\"重复\","
                + "\"identifyField\":\"f\",\"templateId\":%d}", code, tplId);
        mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupBody))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ─────────────────────── 6. 必填校验 ────────────────────────

    @Test
    @DisplayName("渠道编码为空：返回 400")
    void save_missingChannelCode_returns400() throws Exception {
        Long tplId = createTemplate();
        String body = String.format(
                "{\"channelName\":\"无编码\",\"identifyField\":\"f\",\"templateId\":%d}", tplId);
        mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("识别字段为空：返回 400")
    void save_missingIdentifyField_returns400() throws Exception {
        Long tplId = createTemplate();
        String body = String.format(
                "{\"channelCode\":\"CH_NO_FIELD\",\"channelName\":\"无字段\",\"templateId\":%d}", tplId);
        mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ─────────────────────── 7. 渠道路由匹配 ────────────────────

    @Test
    @DisplayName("match：JSON 报文含识别字段值=渠道编码，返回对应 templateId")
    void match_jsonMessage_returnsCorrectTemplateId() throws Exception {
        Long tplId = createTemplate();
        String code = "CH_MATCH_" + System.currentTimeMillis();
        // 识别字段名为 "channelCode"，值为渠道编码本身
        createChannel(code, "channelCode", tplId);

        // 构造含识别字段的 JSON 报文
        String message = "{\"channelCode\":\"" + code + "\",\"data\":\"hello\"}";

        MvcResult r = mockMvc.perform(post("/api/channel/match")
                        .header("satoken", token)
                        .param("message", message)
                        .param("format", "JSON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Object returned = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        Long returnedId = returned == null ? null : ((Number) returned).longValue();
        assert tplId.equals(returnedId)
                : "应返回关联模板 id=" + tplId + "，实际返回=" + returnedId;
    }

    @Test
    @DisplayName("match：报文不含任何渠道识别字段，返回 null")
    void match_noMatchingField_returnsNull() throws Exception {
        // 不需要创建渠道，直接发一个无法匹配的报文
        String message = "{\"unrelated\":\"value\"}";

        MvcResult r = mockMvc.perform(post("/api/channel/match")
                        .header("satoken", token)
                        .param("message", message)
                        .param("format", "JSON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Object data = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assert data == null : "未匹配时应返回 null，实际=" + data;
    }

    @Test
    @DisplayName("match：不支持的格式返回 400")
    void match_unsupportedFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/channel/match")
                        .header("satoken", token)
                        .param("message", "{}")
                        .param("format", "UNKNOWN"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ─────────────────────── 8. 多渠道路由优先级 ─────────────────

    @Test
    @DisplayName("match：多渠道配置时精确命中正确渠道的 templateId")
    void match_multipleChannels_hitsCorrectOne() throws Exception {
        Long tplId1 = createTemplate();
        Long tplId2 = createTemplate();
        String ts = String.valueOf(System.currentTimeMillis());

        createChannel("MULTI_A_" + ts, "channelCode", tplId1);
        createChannel("MULTI_B_" + ts, "channelCode", tplId2);

        // 应命中渠道 B
        String message = "{\"channelCode\":\"MULTI_B_" + ts + "\",\"amount\":100}";
        MvcResult r = mockMvc.perform(post("/api/channel/match")
                        .header("satoken", token)
                        .param("message", message)
                        .param("format", "JSON"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Object returned = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        Long returnedId = returned == null ? null : ((Number) returned).longValue();
        assert tplId2.equals(returnedId)
                : "应命中渠道B的 templateId=" + tplId2 + "，实际=" + returnedId;
    }
}
