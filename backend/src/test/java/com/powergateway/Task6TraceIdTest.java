package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.config.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v0.3.1 Task 6 · TraceIdFilter + AOP MDC 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Task6TraceIdTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        adminToken = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    @DisplayName("TraceIdFilter · 响应头带 X-Trace-Id · 32 位 UUID(去 -)")
    void filter_响应头_X_Trace_Id() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();
        String traceId = r.getResponse().getHeader("X-Trace-Id");
        assertNotNull(traceId);
        assertEquals(32, traceId.length(), "UUID 去 - 后应 32 位");
        assertTrue(traceId.matches("[a-f0-9]{32}"), "应全为 16 进制字符");
    }

    @Test
    @DisplayName("TraceIdFilter · 上游透传 X-Trace-Id · 沿用不重新生成")
    void filter_上游透传_沿用() throws Exception {
        String upstream = "upstream-trace-id-12345";
        mockMvc.perform(get("/api/health").header("X-Trace-Id", upstream))
                .andExpect(header().string("X-Trace-Id", upstream));
    }

    @Test
    @DisplayName("TraceIdFilter · finally 清理 MDC · 请求外读不到")
    void filter_finally_清理MDC() throws Exception {
        mockMvc.perform(get("/api/health"));
        // 请求结束后 · MDC 已在 finally 里清理
        assertNull(MDC.get(TraceIdFilter.MDC_KEY),
                "请求后 MDC.traceId 应被清理 · 避免线程复用泄露");
    }

    @Test
    @DisplayName("TraceIdFilter · MDC_KEY 常量")
    void constants() {
        assertEquals("traceId", TraceIdFilter.MDC_KEY);
        assertEquals("X-Trace-Id", TraceIdFilter.HEADER_NAME);
    }

    @Test
    @DisplayName("TraceIdFilter.currentTraceId · 请求外返 null · 请求内可读(通过 MockMvc header 验证间接)")
    void currentTraceId() {
        // 请求外
        assertNull(TraceIdFilter.currentTraceId());
    }
}
