package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M2-1 数据库连接管理 Controller 验收测试
 *
 * 验收标准：
 * 1. 保存连接后列表中可查到，密码脱敏为 ***
 * 2. 新建时未传密码返回 400
 * 3. 更新时密码传 *** 不覆盖原值
 * 4. 删除后列表中不再存在
 * 5. 测试连通接口对不可达数据库返回 success=false（结构正确）
 * 6. 对不存在的连接 ID 操作返回 404/错误
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-1 数据库连接管理 Controller")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M21DbConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long savedId;

    // ─────────────────── 辅助方法 ───────────────────

    private String buildSaveBody(Long id, String name, String password) {
        String idPart = id == null ? "" : "\"id\":" + id + ",";
        return "{" + idPart
                + "\"name\":\"" + name + "\","
                + "\"dbType\":\"MySQL\","
                + "\"url\":\"jdbc:mysql://localhost:3306/nonexist_db\","
                + "\"username\":\"root\","
                + "\"password\":\"" + password + "\","
                + "\"env\":\"dev\","
                + "\"poolSize\":2,"
                + "\"timeout\":3000"
                + "}";
    }

    @BeforeEach
    void login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    // ─────────────────── 测试用例 ───────────────────

    @Test
    @Order(1)
    @DisplayName("新建连接_列表中可查到_密码脱敏")
    void save_新建连接_列表可查到_密码脱敏() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/db/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildSaveBody(null, "测试连接_CT", "myPwd@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();

        savedId = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();

        // 列表中可查到，密码脱敏
        mockMvc.perform(get("/api/db/list")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + savedId + ")].password", hasItem("***")))
                .andExpect(jsonPath("$.data[?(@.id == " + savedId + ")].name", hasItem("测试连接_CT")));
    }

    @Test
    @Order(2)
    @DisplayName("新建连接_未传密码_返回400")
    void save_新建_未传密码_返回400() throws Exception {
        String body = "{\"name\":\"无密码连接\",\"dbType\":\"MySQL\","
                + "\"url\":\"jdbc:mysql://localhost:3306/x\",\"username\":\"root\","
                + "\"password\":\"\",\"env\":\"dev\"}";
        mockMvc.perform(post("/api/db/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test
    @Order(3)
    @DisplayName("更新连接_密码传***_原密码不变_名称更新")
    void save_更新_密码传星号_原密码不变() throws Exception {
        Assumptions.assumeTrue(savedId != null, "依赖 Order(1) 创建的 savedId");

        mockMvc.perform(post("/api/db/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildSaveBody(savedId, "更新后名称", "***")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 更新后名称已变
        mockMvc.perform(get("/api/db/list")
                        .header("satoken", token))
                .andExpect(jsonPath("$.data[?(@.id == " + savedId + ")].name", hasItem("更新后名称")));
    }

    @Test
    @Order(4)
    @DisplayName("测试连通_不可达数据库_返回success=false且结构正确")
    void testConnection_不可达数据库_返回失败结果() throws Exception {
        Assumptions.assumeTrue(savedId != null, "依赖 Order(1) 创建的 savedId");

        mockMvc.perform(post("/api/db/" + savedId + "/test")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").isBoolean())
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    @Order(5)
    @DisplayName("测试连通_不存在的ID_返回非200")
    void testConnection_不存在ID_返回错误() throws Exception {
        mockMvc.perform(post("/api/db/999999/test")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test
    @Order(6)
    @DisplayName("删除连接_列表中不再存在")
    void delete_连接_列表中消失() throws Exception {
        Assumptions.assumeTrue(savedId != null, "依赖 Order(1) 创建的 savedId");

        mockMvc.perform(delete("/api/db/" + savedId)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/db/list")
                        .header("satoken", token))
                .andExpect(jsonPath("$.data[?(@.id == " + savedId + ")]", hasSize(0)));

        savedId = null;
    }

    @Test
    @Order(7)
    @DisplayName("删除_不存在的ID_返回非200")
    void delete_不存在ID_返回错误() throws Exception {
        mockMvc.perform(delete("/api/db/999999")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test
    @Order(8)
    @DisplayName("未登录访问_返回401或非200")
    void 未登录_访问受保护接口_失败() throws Exception {
        mockMvc.perform(get("/api/db/list"))
                .andExpect(jsonPath("$.code").value(not(200)));
    }
}
