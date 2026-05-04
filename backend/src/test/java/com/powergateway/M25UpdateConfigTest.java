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
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M2-5 修改接口配置验收测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-5 修改接口配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M25UpdateConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL  = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m25_product");
            stmt.execute("DROP TABLE IF EXISTS m25_inventory");
            stmt.execute(
                "CREATE TABLE m25_product (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  name VARCHAR(64) NOT NULL," +
                "  price DECIMAL(10,2)," +
                "  category VARCHAR(32)" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE m25_inventory (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  product_id BIGINT NOT NULL," +
                "  stock INT NOT NULL" +
                ")"
            );
            stmt.execute("INSERT INTO m25_product (name, price, category) VALUES ('商品A', 100.00, 'FOOD')");
            stmt.execute("INSERT INTO m25_product (name, price, category) VALUES ('商品B', 200.00, 'ELEC')");
            stmt.execute("INSERT INTO m25_inventory (product_id, stock) VALUES (1, 50)");
            stmt.execute("INSERT INTO m25_inventory (product_id, stock) VALUES (2, 30)");
        }

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");

        DbConnection dbConn = new DbConnection();
        dbConn.setName("m25-test-db");
        dbConn.setDbType("MySQL");
        dbConn.setUrl(H2_URL);
        dbConn.setUsername("sa");
        dbConn.setPassword(AesUtil.encrypt("", AES_KEY));
        dbConn.setEnv("dev");
        dbConnectionMapper.insert(dbConn);
        testDbId = dbConn.getId();
    }

    private Long saveUpdateInterface(String name, String configJson) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        MvcResult result = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).longValue();
    }

    private int executeUpdate(Long id, Map<String, Object> params) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("params", params);
        MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
    }

    private Object queryField(String table, String field, long id) throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + field + " FROM " + table + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(field) : null;
            }
        }
    }

    @Test @Order(1)
    void 保存UPDATE配置_含主键条件_成功() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"category\",\"sourceType\":\"REQUEST\",\"paramKey\":\"category\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        String body = "{\"name\":\"test-upd-1\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        MvcResult result = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn();
        String resp = result.getResponse().getContentAsString();
        assertEquals(200, (int) (Integer) JsonPath.read(resp, "$.code"));
        assertNotNull(JsonPath.read(resp, "$.data"));
    }

    @Test @Order(2)
    void 单表UPDATE_REQUEST数据来源_字段正确更新() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"category\",\"sourceType\":\"REQUEST\",\"paramKey\":\"category\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-request", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 1);
        params.put("category", "DRINK");
        int affected = executeUpdate(id, params);
        assertEquals(1, affected);
        assertEquals("DRINK", queryField("m25_product", "category", 1));
    }

    @Test @Order(3)
    void 单表UPDATE_CONST数据来源_字段为固定值() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"category\",\"sourceType\":\"CONST\",\"constValue\":\"FIXED\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-const", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 2);
        executeUpdate(id, params);
        assertEquals("FIXED", queryField("m25_product", "category", 2));
    }

    @Test @Order(4)
    void 多表UPDATE_成功_两表均正确更新() throws Exception {
        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"REQUEST\",\"paramKey\":\"price\"}]},"
                + "{\"tableName\":\"m25_inventory\",\"fields\":["
                + "{\"column\":\"stock\",\"sourceType\":\"REQUEST\",\"paramKey\":\"stock\"}]}"
                + "],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"},"
                + "{\"tableName\":\"m25_inventory\",\"field\":\"product_id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-multi", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 1);
        params.put("price", 150.0);
        params.put("stock", 88);
        int affected = executeUpdate(id, params);
        assertEquals(2, affected);
        assertNotNull(queryField("m25_product", "price", 1));
        assertNotNull(queryField("m25_inventory", "stock", 1));
    }

    @Test @Order(5)
    void 审计日志含beforeSnapshot() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"999.00\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-snapshot", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 1);
        executeUpdate(id, params);

        Thread.sleep(500);

        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT before_snapshot FROM sql_audit_log WHERE interface_id = ? AND op_type = 'UPDATE'")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应有审计记录");
                String snapshot = rs.getString("before_snapshot");
                assertNotNull(snapshot, "before_snapshot 不能为 null");
                assertFalse(snapshot.trim().isEmpty(), "before_snapshot 不能为空字符串");
            }
        }
    }

    @Test @Order(6)
    void 保存UPDATE_无条件_报错() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}"
                + "]}],\"conditions\":[]}";
        String body = "{\"name\":\"upd-no-cond\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test @Order(7)
    void 保存UPDATE_条件字段非主键非唯一键_报错() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"name\",\"op\":\"EQ\",\"paramKey\":\"name\"}"
                + "]}";
        String body = "{\"name\":\"upd-non-pk\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    @Test @Order(8)
    void 多表UPDATE_第二表字段不存在_事务回滚() throws Exception {
        Object originalPrice = queryField("m25_product", "price", 2);

        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}]},"
                + "{\"tableName\":\"m25_inventory\",\"fields\":["
                + "{\"column\":\"nonexistent_col\",\"sourceType\":\"CONST\",\"constValue\":\"1\"}]}"
                + "],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"},"
                + "{\"tableName\":\"m25_inventory\",\"field\":\"product_id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-rollback", configJson);

        Map<String, Object> params = new HashMap<>();
        params.put("productId", 2);
        Map<String, Object> body = new HashMap<>();
        body.put("params", params);
        mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(not(200)));

        Object priceAfter = queryField("m25_product", "price", 2);
        assertEquals(originalPrice.toString(), priceAfter.toString());
    }
}
