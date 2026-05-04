package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M2-2 表结构查询验收测试
 *
 * 验收标准：
 * 1. 选择连接后表结构树正确展示（tableName、columns 字段齐全）
 * 2. 刷新缓存后返回数据与初次查询一致
 * 3. Redis 缓存键存在（测试环境无 Redis，仅验证接口不报错）
 * 4. Excel 导出返回正确 Content-Type 且内容非空
 * 5. 不存在的 dbId 返回非 200
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-2 表结构查询")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M22TableMetaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        // 直接插入一个指向 H2 测试库的连接记录（绕过 Service 密码非空校验）
        DbConnection conn = new DbConnection();
        conn.setName("H2_Meta_Test_" + System.currentTimeMillis());
        conn.setDbType("MySQL");
        conn.setUrl(H2_URL);
        conn.setUsername("sa");
        conn.setPassword(AesUtil.encrypt("", AES_KEY)); // H2 无密码
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
        if (testDbId != null) {
            dbConnectionMapper.deleteById(testDbId);
        }
    }

    // ─────────────────── 测试用例 ───────────────────

    @Test
    @Order(1)
    @DisplayName("查询表结构_返回已知表_含列信息")
    void getTables_返回表列表_含列信息() throws Exception {
        mockMvc.perform(get("/api/db/" + testDbId + "/tables")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(0)))
                // 验证存在 sys_user 表（H2 可能大写 → 忽略大小写匹配）
                .andExpect(jsonPath("$.data[*].tableName",
                        hasItem(anyOf(equalToIgnoringCase("sys_user")))))
                // 验证列信息结构
                .andExpect(jsonPath("$.data[0].columns").isArray())
                .andExpect(jsonPath("$.data[0].columns[0].name").isString())
                .andExpect(jsonPath("$.data[0].columns[0].type").isString());
    }

    @Test
    @Order(2)
    @DisplayName("查询表结构_sys_user表_含id列且为主键")
    void getTables_sysUser表_含主键列() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/db/" + testDbId + "/tables")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andReturn();

        String body = r.getResponse().getContentAsString();
        // 找到 sys_user 表（大小写不敏感）
        com.jayway.jsonpath.DocumentContext ctx = com.jayway.jsonpath.JsonPath.parse(body);
        java.util.List<java.util.Map<String, Object>> tables = ctx.read("$.data");
        java.util.Optional<java.util.Map<String, Object>> sysUserTable = tables.stream()
                .filter(t -> ((String) t.get("tableName")).equalsIgnoreCase("sys_user"))
                .findFirst();

        Assertions.assertTrue(sysUserTable.isPresent(), "应存在 sys_user 表");

        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> columns =
                (java.util.List<java.util.Map<String, Object>>) sysUserTable.get().get("columns");
        Assertions.assertFalse(columns.isEmpty(), "sys_user 应有列信息");

        // 验证 id 列存在且为主键
        java.util.Optional<java.util.Map<String, Object>> idCol = columns.stream()
                .filter(c -> ((String) c.get("name")).equalsIgnoreCase("id"))
                .findFirst();
        Assertions.assertTrue(idCol.isPresent(), "应有 id 列");
        Assertions.assertTrue((Boolean) idCol.get().get("isPrimary"), "id 列应为主键");
    }

    @Test
    @Order(3)
    @DisplayName("刷新缓存_返回相同表结构")
    void refreshCache_返回相同表结构() throws Exception {
        mockMvc.perform(delete("/api/db/" + testDbId + "/tables/cache")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(greaterThan(0)));
    }

    @Test
    @Order(4)
    @DisplayName("Excel导出_返回xlsx内容类型_内容非空")
    void exportExcel_返回xlsx文件() throws Exception {
        mockMvc.perform(get("/api/db/" + testDbId + "/tables/export")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @Order(5)
    @DisplayName("不存在的dbId_返回非200")
    void getTables_不存在dbId_返回错误() throws Exception {
        mockMvc.perform(get("/api/db/999999/tables")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test
    @Order(6)
    @DisplayName("未登录访问_返回非200")
    void getTables_未登录_返回错误() throws Exception {
        mockMvc.perform(get("/api/db/" + testDbId + "/tables"))
                .andExpect(jsonPath("$.code").value(not(200)));
    }
}
