package com.powergateway;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogController 接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogControllerTest {

    @Autowired private MockMvc mockMvc;

    private String token;

    @BeforeAll
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    @Order(1)
    void list接口_返回200和Result包装() throws Exception {
        mockMvc.perform(get("/api/log/list")
                        .header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    void historyList接口_返回200和Result包装() throws Exception {
        mockMvc.perform(get("/api/log/history/list")
                        .header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    void auditList接口_返回200和Result包装() throws Exception {
        mockMvc.perform(get("/api/log/audit/list")
                        .header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    void export接口_返回Excel内容类型() throws Exception {
        mockMvc.perform(get("/api/log/export")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }
}
