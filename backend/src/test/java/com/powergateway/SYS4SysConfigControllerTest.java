package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-4 SysConfigController 接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS4SysConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeAll
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        adminToken = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    @Order(1)
    void getAll_返回200和配置列表() throws Exception {
        mockMvc.perform(get("/api/config/all")
                        .header("satoken", adminToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data").isArray())
               .andExpect(jsonPath("$.data[0].configKey").exists())
               .andExpect(jsonPath("$.data[0].groupName").exists())
               .andExpect(jsonPath("$.data[0].valueType").exists());
    }

    @Test
    @Order(2)
    void update_admin角色可以更新配置() throws Exception {
        mockMvc.perform(put("/api/config")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Collections.singletonMap("alert_fail_rate", "10"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200));
    }
}
