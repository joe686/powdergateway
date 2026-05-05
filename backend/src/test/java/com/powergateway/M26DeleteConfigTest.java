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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-6 删除接口配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M26DeleteConfigTest {

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
            stmt.execute("DROP TABLE IF EXISTS m26_order_item");
            stmt.execute("DROP TABLE IF EXISTS m26_order");
            stmt.execute(
                "CREATE TABLE m26_order (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT NOT NULL," +
                "  status VARCHAR(32) NOT NULL" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE m26_order_item (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  order_id BIGINT NOT NULL," +
                "  product_id BIGINT NOT NULL" +
                ")"
            );
            // user_id=10：2条（用于批量保护测试）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (1, 10, 'PENDING')");
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (2, 10, 'PENDING')");
            // user_id=20：1条（用于单条删除）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (3, 20, 'DONE')");
            // user_id=30：1条 + order_item（用于多表删除）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (4, 30, 'DONE')");
            // user_id=40：1条（用于事务回滚测试）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (5, 40, 'PENDING')");
            // order_items
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (1, 1, 100)");
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (2, 1, 101)");
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (3, 4, 200)");
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (4, 4, 201)");
        }

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");

        DbConnection dbConn = new DbConnection();
        dbConn.setName("m26-test-db");
        dbConn.setDbType("MySQL");
        dbConn.setUrl(H2_URL);
        dbConn.setUsername("sa");
        dbConn.setPassword(AesUtil.encrypt("", AES_KEY));
        dbConn.setEnv("dev");
        dbConnectionMapper.insert(dbConn);
        testDbId = dbConn.getId();
    }

    /** 保存一个 DELETE 接口配置，返回接口 id */
    private Long saveDeleteInterface(String name, String configJson, int allowBatchDelete) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"DELETE\",\"allowBatchDelete\":" + allowBatchDelete
                + ",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        MvcResult result = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).longValue();
    }

    /** 直接查 H2 记录数 */
    private int countInH2(String table, String whereClause) throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ─── Preview Tests ────────────────────────────────────────────────────────

    @Test @Order(1)
    void 预览待删数据_单表_返回匹配行() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
                "\"conditions\":[{\"field\":\"user_id\",\"op\":\"EQ\",\"paramKey\":\"userId\"}]}]}";
        Long id = saveDeleteInterface("prev-single", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("userId", 10); }});

        mockMvc.perform(post("/api/interface/" + id + "/delete-preview")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.m26_order").isArray())
                .andExpect(jsonPath("$.data.m26_order", hasSize(2)));
    }

    @Test @Order(2)
    void 预览待删数据_多表_分表返回结果() throws Exception {
        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m26_order\","
                + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]},"
                + "{\"tableName\":\"m26_order_item\","
                + "\"conditions\":[{\"field\":\"order_id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
                + "]}";
        Long id = saveDeleteInterface("prev-multi", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("orderId", 4); }});

        mockMvc.perform(post("/api/interface/" + id + "/delete-preview")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.m26_order", hasSize(1)))
                .andExpect(jsonPath("$.data.m26_order_item", hasSize(2)));
    }

    // ─── Execute Tests ────────────────────────────────────────────────────────

    @Test @Order(3)
    void 批量保护_多条记录_拒绝执行() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
                "\"conditions\":[{\"field\":\"user_id\",\"op\":\"EQ\",\"paramKey\":\"userId\"}]}]}";
        Long id = saveDeleteInterface("exec-batch-protect", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("userId", 10); }});

        mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(not(200)));

        assertEquals(2, countInH2("m26_order", "user_id = 10"));
    }

    @Test @Order(4)
    void 开启批量删除_多条成功_返回影响行数() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
                "\"conditions\":[{\"field\":\"user_id\",\"op\":\"EQ\",\"paramKey\":\"userId\"}]}]}";
        Long id = saveDeleteInterface("exec-batch-ok", configJson, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("userId", 10); }});

        MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        int affected = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
        assertEquals(2, affected);
        assertEquals(0, countInH2("m26_order", "user_id = 10"));
    }

    @Test @Order(5)
    void 单条记录DELETE_成功_数据消失() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
                "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}]}";
        Long id = saveDeleteInterface("exec-single", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("orderId", 3); }});

        MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        int affected = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
        assertEquals(1, affected);
        assertEquals(0, countInH2("m26_order", "id = 3"));
    }

    @Test @Order(6)
    void 多表DELETE_两表均成功() throws Exception {
        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m26_order\","
                + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]},"
                + "{\"tableName\":\"m26_order_item\","
                + "\"conditions\":[{\"field\":\"order_id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
                + "]}";
        Long id = saveDeleteInterface("exec-multi", configJson, 1); // 多表总计3条，需开启批量删除

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("orderId", 4); }});

        MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        int affected = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
        assertEquals(3, affected); // 1(m26_order) + 2(m26_order_item)
        assertEquals(0, countInH2("m26_order", "id = 4"));
        assertEquals(0, countInH2("m26_order_item", "order_id = 4"));
    }

    @Test @Order(7)
    void 多表DELETE_第二表列不存在_事务回滚() throws Exception {
        assertEquals(1, countInH2("m26_order", "id = 5"));

        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m26_order\","
                + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]},"
                + "{\"tableName\":\"m26_order_item\","
                + "\"conditions\":[{\"field\":\"nonexistent_col\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
                + "]}";
        Long id = saveDeleteInterface("exec-rollback", configJson, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("orderId", 5); }});

        mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(not(200)));

        assertEquals(1, countInH2("m26_order", "id = 5"));
    }

    @Test @Order(8)
    void 审计日志含各表COUNT信息() throws Exception {
        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m26_order\","
                + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
                + "]}";
        Long id = saveDeleteInterface("exec-audit", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("orderId", 5); }});

        mockMvc.perform(post("/api/interface/" + id + "/execute")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200));

        Thread.sleep(500);

        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT before_snapshot FROM sql_audit_log WHERE interface_id = ? AND op_type = 'DELETE'")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应有 DELETE 审计记录");
                String snapshot = rs.getString("before_snapshot");
                assertNotNull(snapshot);
                assertTrue(snapshot.contains("m26_order"), "before_snapshot 应含表名");
            }
        }
    }
}
