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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("FN-06 ExecController Accept 格式协商")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FN06ExecFormatTest {

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
            stmt.execute("DROP TABLE IF EXISTS fn06_item");
            stmt.execute("CREATE TABLE fn06_item (" +
                         "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                         "  name VARCHAR(64) NOT NULL" +
                         ")");
            stmt.execute("INSERT INTO fn06_item(name) VALUES('Item1'),('Item2')");
        }

        String loginBody = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";
        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        DbConnection db = new DbConnection();
        db.setName("FN06_H2");
        db.setDbType("MySQL");
        db.setUrl(H2_URL);
        db.setUsername("sa");
        db.setPassword(AesUtil.encrypt("", AES_KEY));
        db.setEnv("dev");
        dbConnectionMapper.insert(db);
        testDbId = db.getId();
    }

    private Long saveAndPublishSelect() throws Exception {
        return saveAndPublishSelectWithFormat("JSON");
    }

    private Long saveAndPublishSelectWithFormat(String fmt) throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"fn06_item\",\"alias\":\"t\"}]," +
            "\"fields\":[{\"table\":\"t\",\"column\":\"id\",\"alias\":\"id\"}," +
            "{\"table\":\"t\",\"column\":\"name\",\"alias\":\"name\"}]," +
            "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "FN06_SELECT_" + System.currentTimeMillis());
        req.put("dbConnectionId", testDbId);
        req.put("type", "SELECT");
        req.put("configJson", configJson);
        req.put("responseFormat", fmt);

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

    private Long saveAndPublishInsert() throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"fn06_item\"," +
            "\"fields\":[{\"column\":\"name\",\"source\":\"REQUEST\",\"requestParam\":\"name\"}]}]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "FN06_INSERT_" + System.currentTimeMillis());
        req.put("dbConnectionId", testDbId);
        req.put("type", "INSERT");
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
    void 默认Accept_返回JSON_Result包装保持兼容() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("application/json")))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(2)
    void Accept_XML_返回XML() throws Exception {
        Long id = saveAndPublishSelect();
        MvcResult r = mockMvc.perform(post("/api/exec/" + id)
                .header("Accept", "application/xml")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("xml")))
            .andReturn();
        String body = r.getResponse().getContentAsString();
        assertTrue(body.startsWith("<"), "XML 响应必须以 < 开头，实际: " + body);
        assertTrue(body.contains("rows") || body.contains("row"),
            "XML 响应必须包裹 rows/row 节点");
    }

    @Test
    @Order(3)
    void Query_format_csv_覆盖Accept() throws Exception {
        Long id = saveAndPublishSelect();
        MvcResult r = mockMvc.perform(post("/api/exec/" + id + "?format=csv")
                .header("Accept", "application/xml")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString("csv")))
            .andReturn();
        String body = r.getResponse().getContentAsString();
        assertTrue(body.length() > 0, "CSV 响应体不能为空");
    }

    @Test
    @Order(4)
    void Accept_FormUrlEncoded_返回FormData() throws Exception {
        Long id = saveAndPublishSelect();
        MvcResult r = mockMvc.perform(post("/api/exec/" + id)
                .header("Accept", "application/x-www-form-urlencoded")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
            .andExpect(status().isOk())
            .andReturn();
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("="), "FormData 响应必须含 key=value");
    }

    @Test
    @Order(5)
    void 未指定Accept_读取config默认responseFormat_XML() throws Exception {
        Long id = saveAndPublishSelectWithFormat("XML");
        MvcResult r = mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
            .andReturn();
        assertTrue(r.getResponse().getContentAsString().startsWith("<"));
    }

    @Test
    @Order(6)
    void 未知format参数_400() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id + "?format=protobuf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(7)
    void INSERT_Accept_XML_返回XML包装的影响行数() throws Exception {
        Long id = saveAndPublishInsert();
        MvcResult r = mockMvc.perform(post("/api/exec/" + id)
                .header("Accept", "application/xml")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{\"name\":\"新增项\"}}"))
            .andExpect(status().isOk())
            .andReturn();
        String body = r.getResponse().getContentAsString();
        assertTrue(body.startsWith("<"));
        assertTrue(body.contains("affected") || body.contains("data"));
    }
}
