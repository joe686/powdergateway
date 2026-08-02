package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * SOCKET 接口配置辅助 API(v0.3.0 SOCK-2 · Task 9)。
 *
 * <p>供前端 SocketConfigStep.vue "测试连接" 按钮调用 · 需登录。</p>
 */
@RestController
@RequestMapping("/api/socket")
@Tag(name = "SOCKET 接口辅助", description = "SOCKET 目标类型配置期辅助 API")
public class SocketController {

    /**
     * 测试到 ip:port 的 TCP 连接是否可达(不发任何应用层数据 · 仅探测三次握手成功)。
     */
    @PostMapping("/test-connect")
    @Operation(summary = "测试 TCP 连接可达性(v0.3.0 SOCK-2)")
    public Result<TestConnectResponse> testConnect(@RequestBody TestConnectRequest req) {
        if (req == null || req.getIp() == null || req.getIp().trim().isEmpty()) {
            throw new BusinessException(400, "ip 字段必填");
        }
        if (req.getPort() <= 0 || req.getPort() > 65535) {
            throw new BusinessException(400, "port 需在 1~65535 · 收到:" + req.getPort());
        }
        int timeoutMs = req.getConnTimeoutMs() > 0 ? req.getConnTimeoutMs() : 3000;

        long start = System.currentTimeMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(req.getIp(), req.getPort()), timeoutMs);
            long cost = System.currentTimeMillis() - start;
            TestConnectResponse resp = new TestConnectResponse();
            resp.setReachable(true);
            resp.setLatencyMs(cost);
            resp.setMessage("连接成功");
            return Result.success(resp);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            TestConnectResponse resp = new TestConnectResponse();
            resp.setReachable(false);
            resp.setLatencyMs(cost);
            resp.setMessage("连接失败:" + e.getClass().getSimpleName() + " · " + e.getMessage());
            return Result.success(resp);
        }
    }

    public static class TestConnectRequest {
        private String ip;
        private int port;
        private int connTimeoutMs = 3000;

        public String getIp() { return ip; }
        public void setIp(String ip) { this.ip = ip; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getConnTimeoutMs() { return connTimeoutMs; }
        public void setConnTimeoutMs(int connTimeoutMs) { this.connTimeoutMs = connTimeoutMs; }
    }

    public static class TestConnectResponse {
        private boolean reachable;
        private long latencyMs;
        private String message;

        public boolean isReachable() { return reachable; }
        public void setReachable(boolean reachable) { this.reachable = reachable; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
