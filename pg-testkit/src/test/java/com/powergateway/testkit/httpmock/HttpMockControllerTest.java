package com.powergateway.testkit.httpmock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v0.3.2 Task 5 · pg-testkit HttpMockController 单元测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HttpMockControllerTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("POST /mock/hstc/query/online/ · body 含 FunctionId=180345 · 返 mocks/http/180345.json")
    void 命中180345_返mock文件() throws Exception {
        String body = "[{\"head\":{\"FunctionId\":\"180345\"},\"body\":{\"Account\":\"1021530253\"}}]";
        String resp = mockMvc.perform(post("/mock/hstc/query/online/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(resp.contains("180345"));
        org.junit.jupiter.api.Assertions.assertTrue(resp.contains("组合宝"), "应含 UTF-8 中文 · 实际:" + resp);
    }

    @Test
    @DisplayName("POST /mock/x · body 无 FunctionId · 返 default.json")
    void 未命中_返default() throws Exception {
        mockMvc.perform(post("/mock/x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("default mock response")));
    }

    @Test
    @DisplayName("透传 X-Trace-Id + X-Original-Function-Id 不影响应答")
    void 透传header_不影响() throws Exception {
        String body = "[{\"head\":{\"FunctionId\":\"180345\"},\"body\":{}}]";
        mockMvc.perform(post("/mock/hstc/query/online/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "test-trace-id-32-chars-abcdef")
                        .header("X-Original-Function-Id", "180345")
                        .content(body))
                .andExpect(status().isOk());
    }
}
