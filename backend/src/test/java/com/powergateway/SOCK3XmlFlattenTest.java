package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.controller.MessageToolsController.XmlFlattenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SOCK-3 · XML 扁平化 API 单元测试(v0.3.0 · Task 8)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SOCK3XmlFlattenTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        adminToken = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    @DisplayName("XML 扁平化 · 嵌套结构 · 生成 dot.notation key")
    void xmlFlatten_嵌套结构() throws Exception {
        String xml = "<Response>"
                + "<FunctionId>181345</FunctionId>"
                + "<Result><Code>0</Code><Msg>OK</Msg></Result>"
                + "</Response>";

        XmlFlattenRequest body = new XmlFlattenRequest();
        body.setXml(xml);

        mockMvc.perform(post("/api/tools/xml-flatten")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keyCount").value(3))
                .andExpect(jsonPath("$.data.flattened.FunctionId").value("181345"))
                .andExpect(jsonPath("$.data.flattened.['Result.Code']").value("0"))
                .andExpect(jsonPath("$.data.flattened.['Result.Msg']").value("OK"));
    }

    @Test
    @DisplayName("XML 扁平化 · prefix=resp. · key 加前缀")
    void xmlFlatten_加前缀() throws Exception {
        String xml = "<Ack><Code>0</Code></Ack>";
        XmlFlattenRequest body = new XmlFlattenRequest();
        body.setXml(xml);
        body.setPrefix("resp.");

        mockMvc.perform(post("/api/tools/xml-flatten")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flattened.['resp..Code']").value("0"));
    }

    @Test
    @DisplayName("XML 为空 · 400")
    void xmlFlatten_空_400() throws Exception {
        XmlFlattenRequest body = new XmlFlattenRequest();
        body.setXml("");

        mockMvc.perform(post("/api/tools/xml-flatten")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("XML 格式非法 · 400")
    void xmlFlatten_格式非法_400() throws Exception {
        XmlFlattenRequest body = new XmlFlattenRequest();
        body.setXml("<Bad>not closed");

        mockMvc.perform(post("/api/tools/xml-flatten")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400));
    }
}
