package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M2-3 查询接口配置验收测试
 *
 * 验收标准：
 * 1. 保存单表查询配置，成功返回 id，interface_config 中 JSON 结构完整
 * 2. 保存多表 LEFT JOIN 配置，成功
 * 3. 预览单表查询，返回数据行列表
 * 4. 预览带条件查询，返回过滤结果
 * 5. 预览2表 LEFT JOIN，返回数据
 * 6. 缺少名称时保存报错
 * 7. 不存在的接口 id 预览报错
 * 8. 未登录访问报错
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-3 查询接口配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M23QueryConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setupDb() {
        DbConnection conn = new DbConnection();
        conn.setName("H2_Query_Test_" + System.currentTimeMillis());
        conn.setDbType("MySQL");
        conn.setUrl(H2_URL);
        conn.setUsername("sa");
        conn.setPassword(AesUtil.encrypt("", AES_KEY));
        conn.setEnv("test");
        conn.setPoolSize(2);
        conn.setTimeout(3000);
        dbConnectionMapper.insert(conn);
        testDbId = conn.getId();
    }

    @BeforeEach
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @AfterAll
    void cleanup() {
        if (testDbId != null) dbConnectionMapper.deleteById(testDbId);
    }

    // ─── 辅助方法 ────────────────────────────────────────────────────────────────

    /** 构造单表查询 config_json */
    private String singleTableConfig() {
        return "{" +
            "\"tables\":[{\"name\":\"sys_user\",\"alias\":\"u\"}]," +
            "\"joins\":[]," +
            "\"fields\":[" +
                "{\"table\":\"u\",\"column\":\"id\",\"alias\":\"userId\"}," +
                "{\"table\":\"u\",\"column\":\"username\",\"alias\":\"username\"}" +
            "]," +
            "\"conditions\":[]," +
            "\"processRules\":[]" +
            "}";
    }

    /** 保存接口配置，返回接口 id */
    private Long saveInterface(String name, String configJson) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("dbConnectionId", testDbId);
        body.put("type", "SELECT");
        body.put("configJson", configJson);

        MvcResult r = mockMvc.perform(post("/api/interface/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
    }

    // ─── 测试用例 ─────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("保存单表查询配置_成功返回id")
    void save_单表_成功() throws Exception {
        Long id = saveInterface("单表查询_" + System.currentTimeMillis(), singleTableConfig());
        Assertions.assertNotNull(id);
        Assertions.assertTrue(id > 0);

        // 验证 interface_config 中有完整的 JSON 字段
        mockMvc.perform(get("/api/interface/" + id)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.configJson").isString())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.type").value("SELECT"));
    }

    @Test @Order(2)
    @DisplayName("保存多表LEFT_JOIN配置_成功")
    void save_多表JOIN_成功() throws Exception {
        String configJson = "{" +
            "\"tables\":[" +
                "{\"name\":\"sys_user\",\"alias\":\"u\"}," +
                "{\"name\":\"interface_config\",\"alias\":\"ic\"}" +
            "]," +
            "\"joins\":[{\"leftTable\":\"u\",\"leftCol\":\"id\"," +
                "\"rightTable\":\"ic\",\"rightCol\":\"db_connection_id\",\"type\":\"LEFT\"}]," +
            "\"fields\":[{\"table\":\"u\",\"column\":\"id\",\"alias\":\"userId\"}]," +
            "\"conditions\":[]," +
            "\"processRules\":[]" +
            "}";
        Long id = saveInterface("多表查询_" + System.currentTimeMillis(), configJson);
        Assertions.assertTrue(id > 0);
    }

    @Test @Order(3)
    @DisplayName("预览单表查询_返回数据行列表")
    void preview_单表_返回数据() throws Exception {
        Long id = saveInterface("预览单表_" + System.currentTimeMillis(), singleTableConfig());

        mockMvc.perform(post("/api/interface/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(4)
    @DisplayName("预览带条件查询_返回过滤结果含admin")
    void preview_带条件_返回过滤数据() throws Exception {
        String configJson = "{" +
            "\"tables\":[{\"name\":\"sys_user\",\"alias\":\"u\"}]," +
            "\"joins\":[]," +
            "\"fields\":[" +
                "{\"table\":\"u\",\"column\":\"id\",\"alias\":\"userId\"}," +
                "{\"table\":\"u\",\"column\":\"username\",\"alias\":\"username\"}" +
            "]," +
            "\"conditions\":[{\"field\":\"u.username\",\"op\":\"EQ\",\"paramKey\":\"username\"}]," +
            "\"processRules\":[]" +
            "}";
        Long id = saveInterface("带条件查询_" + System.currentTimeMillis(), configJson);

        mockMvc.perform(post("/api/interface/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"username\":\"admin\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(1)));
    }

    @Test @Order(5)
    @DisplayName("预览多表LEFT_JOIN_返回数据")
    void preview_多表JOIN_返回数据() throws Exception {
        String configJson = "{" +
            "\"tables\":[" +
                "{\"name\":\"sys_user\",\"alias\":\"u\"}," +
                "{\"name\":\"interface_config\",\"alias\":\"ic\"}" +
            "]," +
            "\"joins\":[{\"leftTable\":\"u\",\"leftCol\":\"id\"," +
                "\"rightTable\":\"ic\",\"rightCol\":\"db_connection_id\",\"type\":\"LEFT\"}]," +
            "\"fields\":[" +
                "{\"table\":\"u\",\"column\":\"id\",\"alias\":\"userId\"}," +
                "{\"table\":\"u\",\"column\":\"username\",\"alias\":\"username\"}" +
            "]," +
            "\"conditions\":[]," +
            "\"processRules\":[]" +
            "}";
        Long id = saveInterface("多表预览_" + System.currentTimeMillis(), configJson);

        mockMvc.perform(post("/api/interface/" + id + "/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(6)
    @DisplayName("保存_缺少名称_返回错误")
    void save_缺少名称_报错() throws Exception {
        String body = "{\"dbConnectionId\":" + testDbId + ",\"type\":\"SELECT\",\"configJson\":\"{}\"}";
        mockMvc.perform(post("/api/interface/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test @Order(7)
    @DisplayName("预览_不存在id_返回错误")
    void preview_不存在id_报错() throws Exception {
        mockMvc.perform(post("/api/interface/999999/preview")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test @Order(8)
    @DisplayName("未登录访问_返回非200")
    void save_未登录_报错() throws Exception {
        mockMvc.perform(post("/api/interface/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\",\"dbConnectionId\":1,\"type\":\"SELECT\",\"configJson\":\"{}\"}"))
                .andExpect(jsonPath("$.code").value(not(200)));
    }
}
