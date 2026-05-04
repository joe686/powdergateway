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

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M2-4 插入接口配置验收测试
 *
 * 验收标准：
 * 1. 保存 INSERT 配置，成功返回 id，type=INSERT
 * 2. 执行单表 INSERT，REQUEST 数据来源
 * 3. 执行单表 INSERT，CONST 数据来源
 * 4. 执行双表 INSERT，含 CALC 运算数据来源，两表均成功
 * 5. 执行双表 INSERT，第二表失败（UNIQUE 约束），全部回滚
 * 6. ColumnValidator：配置字段值为 null 且列不允许为空，返回错误
 * 7. 未登录访问返回非 200
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-4 插入接口配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M24InsertConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL  = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        // 在 H2 中创建测试用表
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m24_order");
            stmt.execute("DROP TABLE IF EXISTS m24_item");
            stmt.execute(
                "CREATE TABLE m24_order (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT NOT NULL," +
                "  total_amount DECIMAL(10,2) NOT NULL" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE m24_item (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  order_id BIGINT," +
                "  item_code VARCHAR(64) NOT NULL UNIQUE," +
                "  amount DECIMAL(10,2)" +
                ")"
            );
            // 预插入一条记录，供回滚测试制造 UNIQUE 冲突
            stmt.execute("INSERT INTO m24_item (item_code, amount) VALUES ('DUPLICATE-CODE', 99.0)");
        }

        // 注册指向 H2 的数据库连接
        DbConnection dbConn = new DbConnection();
        dbConn.setName("H2_Insert_Test_" + System.currentTimeMillis());
        dbConn.setDbType("MySQL");
        dbConn.setUrl(H2_URL);
        dbConn.setUsername("sa");
        dbConn.setPassword(AesUtil.encrypt("", AES_KEY));
        dbConn.setEnv("test");
        dbConn.setPoolSize(2);
        dbConn.setTimeout(3000);
        dbConnectionMapper.insert(dbConn);
        testDbId = dbConn.getId();
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
    void cleanup() throws Exception {
        if (testDbId != null) dbConnectionMapper.deleteById(testDbId);
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m24_order");
            stmt.execute("DROP TABLE IF EXISTS m24_item");
        }
    }

    // ─── 辅助方法 ────────────────────────────────────────────────────────────────

    /** 保存 INSERT 接口配置，返回 id */
    private Long saveInsertInterface(String name, String configJson) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("dbConnectionId", testDbId);
        body.put("type", "INSERT");
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

    /** 执行 INSERT，返回 MvcResult */
    private MvcResult executeInsert(Long id, Map<String, Object> params) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("params", params);
        return mockMvc.perform(post("/api/interface/" + id + "/execute")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    /** 查询 H2 表的行数 */
    private int countRows(String tableName) throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** 构造单表（m24_order）REQUEST 来源 config_json */
    private String singleTableRequestConfig() {
        return "{\"tables\":[{" +
               "\"tableName\":\"m24_order\"," +
               "\"fields\":[" +
                   "{\"column\":\"user_id\",\"sourceType\":\"REQUEST\",\"paramKey\":\"userId\"}," +
                   "{\"column\":\"total_amount\",\"sourceType\":\"REQUEST\",\"paramKey\":\"totalAmount\"}" +
               "]}]}";
    }

    /** 构造单表（m24_order）CONST 来源 config_json */
    private String singleTableConstConfig() {
        return "{\"tables\":[{" +
               "\"tableName\":\"m24_order\"," +
               "\"fields\":[" +
                   "{\"column\":\"user_id\",\"sourceType\":\"CONST\",\"constValue\":\"100\"}," +
                   "{\"column\":\"total_amount\",\"sourceType\":\"CONST\",\"constValue\":\"999.99\"}" +
               "]}]}";
    }

    // ─── 测试用例 ─────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("保存INSERT配置_成功返回id且type为INSERT")
    void save_INSERT配置_成功() throws Exception {
        Long id = saveInsertInterface("INSERT配置_" + System.currentTimeMillis(), singleTableRequestConfig());
        assertNotNull(id);
        assertTrue(id > 0);

        mockMvc.perform(get("/api/interface/" + id)
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.type").value("INSERT"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.configJson").isString());
    }

    @Test @Order(2)
    @DisplayName("执行单表INSERT_REQUEST数据来源_成功插入")
    void execute_单表REQUEST_成功() throws Exception {
        Long id = saveInsertInterface("单表REQUEST_" + System.currentTimeMillis(), singleTableRequestConfig());

        int before = countRows("m24_order");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", 10L);
        params.put("totalAmount", 200.50);

        MvcResult r = executeInsert(id, params);
        String body = r.getResponse().getContentAsString();
        assertEquals(200, (int) (Integer) JsonPath.read(body, "$.code"));

        assertEquals(before + 1, countRows("m24_order"));
    }

    @Test @Order(3)
    @DisplayName("执行单表INSERT_CONST数据来源_成功插入")
    void execute_单表CONST_成功() throws Exception {
        Long id = saveInsertInterface("单表CONST_" + System.currentTimeMillis(), singleTableConstConfig());

        int before = countRows("m24_order");
        MvcResult r = executeInsert(id, new LinkedHashMap<>());
        assertEquals(200, (int) (Integer) JsonPath.read(r.getResponse().getContentAsString(), "$.code"));

        assertEquals(before + 1, countRows("m24_order"));
    }

    @Test @Order(4)
    @DisplayName("执行双表INSERT_含CALC运算来源_两表均成功")
    void execute_双表含CALC_成功() throws Exception {
        // m24_order: user_id from REQUEST, total_amount from CALC (price * qty)
        // m24_item:  item_code from REQUEST (唯一值), amount from CONST
        String configJson = "{\"tables\":[" +
            "{\"tableName\":\"m24_order\",\"fields\":[" +
                "{\"column\":\"user_id\",\"sourceType\":\"REQUEST\",\"paramKey\":\"userId\"}," +
                "{\"column\":\"total_amount\",\"sourceType\":\"CALC\",\"expression\":\"price * qty\"}" +
            "]}," +
            "{\"tableName\":\"m24_item\",\"fields\":[" +
                "{\"column\":\"item_code\",\"sourceType\":\"REQUEST\",\"paramKey\":\"itemCode\"}," +
                "{\"column\":\"amount\",\"sourceType\":\"CONST\",\"constValue\":\"50.0\"}" +
            "]}" +
        "]}";

        Long id = saveInsertInterface("双表CALC_" + System.currentTimeMillis(), configJson);

        int orderBefore = countRows("m24_order");
        int itemBefore  = countRows("m24_item");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", 20L);
        params.put("price", 100);
        params.put("qty", 3);
        params.put("itemCode", "ITEM-" + System.currentTimeMillis());

        MvcResult r = executeInsert(id, params);
        assertEquals(200, (int) (Integer) JsonPath.read(r.getResponse().getContentAsString(), "$.code"));

        assertEquals(orderBefore + 1, countRows("m24_order"));
        assertEquals(itemBefore  + 1, countRows("m24_item"));
    }

    @Test @Order(5)
    @DisplayName("执行双表INSERT_第二表UNIQUE冲突_全部回滚")
    void execute_双表_第二表失败_全部回滚() throws Exception {
        // m24_order: user_id/total_amount from CONST（会成功执行）
        // m24_item:  item_code = 'DUPLICATE-CODE'（与预插入数据冲突）→ SQL 报错 → 全部回滚
        String configJson = "{\"tables\":[" +
            "{\"tableName\":\"m24_order\",\"fields\":[" +
                "{\"column\":\"user_id\",\"sourceType\":\"CONST\",\"constValue\":\"999\"}," +
                "{\"column\":\"total_amount\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}" +
            "]}," +
            "{\"tableName\":\"m24_item\",\"fields\":[" +
                "{\"column\":\"item_code\",\"sourceType\":\"CONST\",\"constValue\":\"DUPLICATE-CODE\"}," +
                "{\"column\":\"amount\",\"sourceType\":\"CONST\",\"constValue\":\"10.0\"}" +
            "]}" +
        "]}";

        Long id = saveInsertInterface("双表回滚_" + System.currentTimeMillis(), configJson);

        int orderBefore = countRows("m24_order");

        MvcResult r = executeInsert(id, new LinkedHashMap<>());
        int code = (int) (Integer) JsonPath.read(r.getResponse().getContentAsString(), "$.code");
        assertNotEquals(200, code, "第二表失败应返回非 200");

        // 验证 m24_order 没有新增行（已回滚）
        assertEquals(orderBefore, countRows("m24_order"), "事务应已全部回滚");
    }

    @Test @Order(6)
    @DisplayName("ColumnValidator_配置字段值为null且列NotNull_返回错误")
    void execute_配置字段值为null_校验失败() throws Exception {
        // user_id NOT NULL，但 REQUEST 来源且请求中不传该参数 → resolve 结果为 null → 校验失败
        String configJson = "{\"tables\":[{" +
            "\"tableName\":\"m24_order\"," +
            "\"fields\":[" +
                "{\"column\":\"user_id\",\"sourceType\":\"REQUEST\",\"paramKey\":\"userId\"}," +
                "{\"column\":\"total_amount\",\"sourceType\":\"CONST\",\"constValue\":\"5.0\"}" +
            "]}]}";

        Long id = saveInsertInterface("校验失败_" + System.currentTimeMillis(), configJson);

        // 不传 userId → DataSourceResolver 返回 null → ColumnValidator 抛异常
        MvcResult r = executeInsert(id, new LinkedHashMap<>());
        int code = (int) (Integer) JsonPath.read(r.getResponse().getContentAsString(), "$.code");
        assertNotEquals(200, code, "非空字段值为 null 应校验失败");
    }

    @Test @Order(7)
    @DisplayName("未登录访问execute_返回非200")
    void execute_未登录_报错() throws Exception {
        mockMvc.perform(post("/api/interface/1/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{}}"))
                .andExpect(jsonPath("$.code").value(not(200)));
    }
}
