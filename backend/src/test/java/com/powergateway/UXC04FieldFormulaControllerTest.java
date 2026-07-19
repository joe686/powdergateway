package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.FieldFormulaMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.service.TableMetaService;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.anyLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UXC04FieldFormulaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper om;
    @Autowired DbConnectionMapper dbConnMapper;
    @Autowired FieldFormulaMapper mapper;
    @Autowired AesUtil aesUtil;

    @MockBean TableMetaService tableMetaService;

    private String adminToken;
    private Long dbId;
    private TableMeta orders;

    @BeforeAll
    void setup() throws Exception {
        DbConnection conn = new DbConnection();
        conn.setName("uxc-ctrl-db");
        conn.setDbType("MySQL");
        conn.setUrl("jdbc:h2:mem:uxcctrl;DB_CLOSE_DELAY=-1");
        conn.setUsername("sa");
        conn.setPassword(aesUtil.encrypt(""));
        conn.setEnv("dev");
        dbConnMapper.insert(conn);
        dbId = conn.getId();

        ColumnMeta amount = new ColumnMeta(); amount.setName("amount");
        orders = new TableMeta();
        orders.setTableName("orders");
        orders.setColumns(Collections.singletonList(amount));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        adminToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");
    }

    @BeforeEach
    void setupMocks() {
        Mockito.when(tableMetaService.getTables(anyLong()))
               .thenReturn(Collections.singletonList(orders));
    }

    private String legalJson(String name) {
        return "{\"name\":\"" + name + "\",\"scene\":\"测试\",\"dbConnectionId\":" + dbId
                + ",\"formulaJson\":\"{\\\"type\\\":\\\"CONDITION_GROUP\\\",\\\"logic\\\":\\\"AND\\\",\\\"children\\\":["
                + "{\\\"nodeType\\\":\\\"CONDITION\\\",\\\"op\\\":\\\"GT\\\","
                + "\\\"left\\\":{\\\"kind\\\":\\\"COLUMN\\\",\\\"tableName\\\":\\\"orders\\\",\\\"columnName\\\":\\\"amount\\\"},"
                + "\\\"right\\\":{\\\"kind\\\":\\\"CONST\\\",\\\"constType\\\":\\\"NUMBER\\\",\\\"constValue\\\":100}}"
                + "]}\"}";
    }

    @Test @Order(1)
    void GET_list_分页参数默认值() throws Exception {
        mockMvc.perform(get("/api/field-formula/list")
                .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(2)
    void POST_save_合法_返回id() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/field-formula/save")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("ctrl-1")))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number id = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assertTrue(id.longValue() > 0);
    }

    @Test @Order(3)
    void GET_byId_软删后返回null() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/field-formula/save")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("ctrl-del")))
                .andReturn();
        Number id = JsonPath.read(saved.getResponse().getContentAsString(), "$.data");

        mockMvc.perform(delete("/api/field-formula/" + id.longValue())
                .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(200));

        MvcResult r = mockMvc.perform(get("/api/field-formula/" + id.longValue())
                .header("satoken", adminToken))
                .andReturn();
        // 软删后 data 应为 null
        assertNull(JsonPath.read(r.getResponse().getContentAsString(), "$.data"));
    }

    @Test @Order(4)
    void POST_duplicate_返回新id() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/field-formula/save")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("ctrl-dup-src")))
                .andReturn();
        Number id = JsonPath.read(saved.getResponse().getContentAsString(), "$.data");

        MvcResult dup = mockMvc.perform(post("/api/field-formula/" + id.longValue() + "/duplicate")
                .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number newId = JsonPath.read(dup.getResponse().getContentAsString(), "$.data");
        assertNotEquals(id.longValue(), newId.longValue());
    }

    @Test @Order(5)
    void POST_validate_校验失败返回错误列表() throws Exception {
        String body = "{\"dbConnectionId\":" + dbId + ",\"formulaJson\":\"{broken\"}";
        MvcResult r = mockMvc.perform(post("/api/field-formula/validate")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Boolean ok = JsonPath.read(r.getResponse().getContentAsString(), "$.data.ok");
        assertFalse(ok);
    }

    @Test @Order(6)
    void POST_save_未登录_返回401或非200() throws Exception {
        mockMvc.perform(post("/api/field-formula/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(legalJson("no-token")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    int code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
                    assertTrue(status == 401 || code != 200, "未登录必须被拒");
                });
    }
}
