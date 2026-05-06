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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-7 ExecController 统一执行入口")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M27ExecControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;
    private static final String H2_URL  = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        // 建 H2 测试表并插入数据
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m27_product");
            stmt.execute("CREATE TABLE m27_product (" +
                         "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                         "  name VARCHAR(64) NOT NULL" +
                         ")");
            stmt.execute("INSERT INTO m27_product(name) VALUES('Apple'),('Banana'),('Cherry')");
        }

        // 登录取 token
        String loginBody = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";
        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        // 注册指向 H2 的 DB 连接
        DbConnection db = new DbConnection();
        db.setName("M27_H2");
        db.setDbType("MySQL");
        db.setUrl(H2_URL);
        db.setUsername("sa");
        db.setPassword(AesUtil.encrypt("", AES_KEY));
        db.setEnv("dev");
        dbConnectionMapper.insert(db);
        testDbId = db.getId();
    }

    /** 保存并发布一个 SELECT 接口，返回其 id */
    private Long saveAndPublishSelect() throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"m27_product\",\"alias\":\"p\"}]," +
            "\"fields\":[{\"table\":\"p\",\"column\":\"id\",\"alias\":\"id\"}," +
            "{\"table\":\"p\",\"column\":\"name\",\"alias\":\"name\"}]," +
            "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "M27_SELECT_" + System.currentTimeMillis());
        req.put("dbConnectionId", testDbId);
        req.put("type", "SELECT");
        req.put("configJson", configJson);

        MvcResult sr = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = ((Number) JsonPath.read(sr.getResponse().getContentAsString(), "$.data")).longValue();

        mockMvc.perform(post("/api/interface/" + id + "/publish")
                .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200));
        return id;
    }

    @Test
    @Order(1)
    void 无token调用exec_成功返回数据() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @Order(2)
    void 分页查询_返回指定行数() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{},\"page\":1,\"pageSize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(3)
    void 禁用接口调用exec_返回403() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/interface/" + id + "/disable")
                .header("satoken", token));
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @Order(4)
    void 草稿接口调用exec_返回400() throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"m27_product\",\"alias\":\"p\"}]," +
            "\"fields\":[{\"table\":\"p\",\"column\":\"id\",\"alias\":\"id\"}]," +
            "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "M27_DRAFT_" + System.currentTimeMillis());
        req.put("dbConnectionId", testDbId);
        req.put("type", "SELECT");
        req.put("configJson", configJson);
        MvcResult sr = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = ((Number) JsonPath.read(sr.getResponse().getContentAsString(), "$.data")).longValue();

        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
