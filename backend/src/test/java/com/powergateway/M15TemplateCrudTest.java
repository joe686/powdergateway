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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M1-5 转换模板管理 CRUD 验收测试
 * <p>
 * 验收标准：
 * 1. 分页列表：关键词搜索正常，总数/当前页数据正确
 * 2. 复制：name 加 _copy 后缀，version=1，is_latest=1
 * 3. 版本留存：修改后旧版本 is_latest=0，新版本 version 递增
 * 4. 逻辑删除：删除后列表不返回该条；查询返回 404
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M1-5 转换模板管理 CRUD")
class M15TemplateCrudTest {

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

    /** 新增模板，返回新建 id */
    private Long createTemplate(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"srcFormat\":\"JSON\",\"targetFormat\":\"XML\","
                + "\"mappingRules\":[{\"srcField\":\"a\",\"targetField\":\"b\",\"fixedValue\":null}]}";
        MvcResult r = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
    }

    // ─────────────────────── 1. 分页列表 ───────────────────────

    @Test
    @DisplayName("分页列表：创建 3 条后 total >= 3，records 不为空")
    void list_paged_returnsCorrectTotal() throws Exception {
        createTemplate("分页测试模板A");
        createTemplate("分页测试模板B");
        createTemplate("分页测试模板C");

        mockMvc.perform(get("/api/template/list")
                        .param("page", "1")
                        .param("size", "10")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("分页列表：关键词搜索只返回匹配名称的模板")
    void list_keyword_filtersCorrectly() throws Exception {
        String unique = "KEYWORD_UNIQUE_" + System.currentTimeMillis();
        createTemplate(unique);
        createTemplate("其他不相关模板_" + System.currentTimeMillis());

        MvcResult r = mockMvc.perform(get("/api/template/list")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", unique)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        int total = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data.total")).intValue();
        assert total >= 1 : "搜索结果至少有 1 条";

        // 所有返回记录名称均应包含关键词
        int size = ((List<?>) JsonPath.read(r.getResponse().getContentAsString(), "$.data.records")).size();
        for (int i = 0; i < size; i++) {
            String name = JsonPath.read(r.getResponse().getContentAsString(), "$.data.records[" + i + "].name");
            assert name.contains(unique) : "返回记录名称应包含关键词";
        }
    }

    @Test
    @DisplayName("分页列表：第 2 页数据正确（size=1 时 page=2 有数据）")
    void list_secondPage_hasData() throws Exception {
        String ts = String.valueOf(System.currentTimeMillis());
        createTemplate("分页P2测试_" + ts + "_1");
        createTemplate("分页P2测试_" + ts + "_2");

        mockMvc.perform(get("/api/template/list")
                        .param("page", "2")
                        .param("size", "1")
                        .param("keyword", "分页P2测试_" + ts)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    // ─────────────────────── 2. 复制模板 ───────────────────────

    @Test
    @DisplayName("复制模板：name 加 _copy 后缀，version=1，is_latest=1")
    void copy_template_nameHasCopySuffix() throws Exception {
        Long srcId = createTemplate("复制测试模板_" + System.currentTimeMillis());

        MvcResult copyResult = mockMvc.perform(post("/api/template/" + srcId + "/copy")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        Long copyId = ((Number) JsonPath.read(copyResult.getResponse().getContentAsString(), "$.data")).longValue();

        mockMvc.perform(get("/api/template/" + copyId)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name", org.hamcrest.Matchers.endsWith("_copy")))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.isLatest").value(1));
    }

    @Test
    @DisplayName("复制模板：复制后原模板与副本独立，修改副本不影响原模板")
    void copy_independent_fromSource() throws Exception {
        Long srcId = createTemplate("独立复制测试_" + System.currentTimeMillis());

        MvcResult copyResult = mockMvc.perform(post("/api/template/" + srcId + "/copy")
                        .header("satoken", token))
                .andReturn();
        Long copyId = ((Number) JsonPath.read(copyResult.getResponse().getContentAsString(), "$.data")).longValue();

        // 原模板仍存在
        mockMvc.perform(get("/api/template/" + srcId)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200));

        // 副本存在且 id 不同
        assert !srcId.equals(copyId) : "副本 id 应不同于原模板";
    }

    // ─────────────────────── 3. 版本留存 ───────────────────────

    @Test
    @DisplayName("版本留存：修改后旧版本 is_latest=0，历史版本可在 list（latestOnly=false）中查到")
    void update_versionArchived_inListWithLatestOnlyFalse() throws Exception {
        String name = "版本留存测试_" + System.currentTimeMillis();
        Long v1Id = createTemplate(name);

        // 修改 → 生成 v2
        String updateBody = "{\"id\":" + v1Id + ",\"name\":\"" + name + "\","
                + "\"srcFormat\":\"JSON\",\"targetFormat\":\"XML\","
                + "\"mappingRules\":[{\"srcField\":\"x\",\"targetField\":\"y\",\"fixedValue\":null}]}";
        MvcResult v2Result = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andReturn();
        Long v2Id = ((Number) JsonPath.read(v2Result.getResponse().getContentAsString(), "$.data")).longValue();

        // v2 是最新版本
        mockMvc.perform(get("/api/template/" + v2Id)
                        .header("satoken", token))
                .andExpect(jsonPath("$.data.isLatest").value(1))
                .andExpect(jsonPath("$.data.version").value(2));

        // latestOnly=false 时，历史版本（v1）也能搜到
        MvcResult allVersions = mockMvc.perform(get("/api/template/list")
                        .param("page", "1")
                        .param("size", "50")
                        .param("keyword", name)
                        .param("latestOnly", "false")
                        .header("satoken", token))
                .andReturn();
        int total = ((Number) JsonPath.read(allVersions.getResponse().getContentAsString(), "$.data.total")).intValue();
        assert total >= 2 : "latestOnly=false 时应包含历史版本，total >= 2，实际=" + total;
    }

    // ─────────────────────── 4. 逻辑删除 ───────────────────────

    @Test
    @DisplayName("逻辑删除：删除后查询返回 404，列表不含该模板")
    void delete_logicalDelete_notFoundInListOrById() throws Exception {
        String name = "删除测试_" + System.currentTimeMillis();
        Long id = createTemplate(name);

        // 确认删除前可查到
        mockMvc.perform(get("/api/template/" + id)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200));

        // 执行删除
        mockMvc.perform(delete("/api/template/" + id)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 按 id 查询返回 404
        mockMvc.perform(get("/api/template/" + id)
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(404));

        // 列表搜索不含该条
        MvcResult listResult = mockMvc.perform(get("/api/template/list")
                        .param("page", "1")
                        .param("size", "50")
                        .param("keyword", name)
                        .header("satoken", token))
                .andReturn();
        int total = ((Number) JsonPath.read(listResult.getResponse().getContentAsString(), "$.data.total")).intValue();
        assert total == 0 : "删除后列表不应包含该模板，实际 total=" + total;
    }

    @Test
    @DisplayName("删除不存在的模板返回 404")
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/template/999999")
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // ─────────────────────── 5. 复制不存在的模板 ───────────────────────

    @Test
    @DisplayName("复制不存在的模板返回 404")
    void copy_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/template/999999/copy")
                        .header("satoken", token))
                .andExpect(jsonPath("$.code").value(404));
    }
}
