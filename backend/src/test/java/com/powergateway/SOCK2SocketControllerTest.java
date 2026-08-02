package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.controller.SocketController.TestConnectRequest;
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

import java.io.IOException;
import java.net.ServerSocket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SOCK-2 · SocketController.testConnect 单元测试(v0.3.0 · Task 9)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SOCK2SocketControllerTest {

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
    @DisplayName("testConnect · 本地起 ServerSocket · 应报 reachable=true")
    void testConnect_可达_true() throws Exception {
        // 本地起一个 ServerSocket 占用空闲端口
        try (ServerSocket ss = new ServerSocket(0)) {
            int port = ss.getLocalPort();

            TestConnectRequest body = new TestConnectRequest();
            body.setIp("127.0.0.1");
            body.setPort(port);
            body.setConnTimeoutMs(2000);

            mockMvc.perform(post("/api/socket/test-connect")
                            .header("satoken", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.reachable").value(true))
                    .andExpect(jsonPath("$.data.message").value("连接成功"));
        }
    }

    @Test
    @DisplayName("testConnect · 空闲端口 · reachable=false + message 含失败原因")
    void testConnect_不可达_false() throws Exception {
        int freePort = pickFreePort();
        TestConnectRequest body = new TestConnectRequest();
        body.setIp("127.0.0.1");
        body.setPort(freePort);
        body.setConnTimeoutMs(1000);

        mockMvc.perform(post("/api/socket/test-connect")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(false))
                .andExpect(jsonPath("$.data.message").exists());
    }

    @Test
    @DisplayName("testConnect · port 非法(0/65536)· 400")
    void testConnect_port非法_400() throws Exception {
        TestConnectRequest body = new TestConnectRequest();
        body.setIp("127.0.0.1");
        body.setPort(0);

        mockMvc.perform(post("/api/socket/test-connect")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400));

        body.setPort(65536);
        mockMvc.perform(post("/api/socket/test-connect")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("testConnect · ip 空 · 400")
    void testConnect_ip空_400() throws Exception {
        TestConnectRequest body = new TestConnectRequest();
        body.setIp("");
        body.setPort(8080);

        mockMvc.perform(post("/api/socket/test-connect")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400));
    }

    private static int pickFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
