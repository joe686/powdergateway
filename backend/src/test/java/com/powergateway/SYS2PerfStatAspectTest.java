package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.service.PerfStatService;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-2 PerfStatAspect AOP 切面测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS2PerfStatAspectTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfStatService perfStatService;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;
    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS sys2_test_table");
            stmt.execute("CREATE TABLE sys2_test_table (id BIGINT AUTO_INCREMENT PRIMARY KEY, val VARCHAR(64))");
            stmt.execute("INSERT INTO sys2_test_table(val) VALUES('A'),('B')");
        }

        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        DbConnection db = new DbConnection();
        db.setName("SYS2_H2_" + System.currentTimeMillis());
        db.setDbType("MySQL");
        db.setUrl(H2_URL);
        db.setUsername("sa");
        db.setPassword(AesUtil.encrypt("", AES_KEY));
        db.setEnv("dev");
        dbConnectionMapper.insert(db);
        testDbId = db.getId();
    }

    @AfterAll
    void cleanup() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .eq(PerfStatRecord::getOpType, "SELECT"));
    }

    private Long saveAndPublishSelect() throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"sys2_test_table\",\"alias\":\"t\"}]," +
                "\"fields\":[{\"table\":\"t\",\"column\":\"id\",\"alias\":\"id\"}]," +
                "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "SYS2_SELECT_" + System.currentTimeMillis());
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
                .header("satoken", token));
        return id;
    }

    @Test
    @Order(1)
    void 执行成功_切面写入success为1的记录() throws Exception {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .eq(PerfStatRecord::getOpType, "SELECT"));

        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk());

        perfStatService.flushForTest();

        List<PerfStatRecord> records = perfStatMapper.selectList(
                new LambdaQueryWrapper<PerfStatRecord>()
                        .eq(PerfStatRecord::getInterfaceId, id));
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSuccess()).isEqualTo(1);
        assertThat(records.get(0).getCostMs()).isGreaterThanOrEqualTo(0);
        assertThat(records.get(0).getOpType()).isEqualTo("SELECT");
    }

    @Test
    @Order(2)
    void 执行禁用接口_切面写入success为0的记录() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/interface/" + id + "/disable")
                .header("satoken", token));

        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .eq(PerfStatRecord::getInterfaceId, id));

        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk());

        perfStatService.flushForTest();

        List<PerfStatRecord> records = perfStatMapper.selectList(
                new LambdaQueryWrapper<PerfStatRecord>()
                        .eq(PerfStatRecord::getInterfaceId, id));
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSuccess()).isEqualTo(0);
    }
}
