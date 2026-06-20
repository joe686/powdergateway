package com.powergateway.testkit.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内嵌 HTTP Mock 服务器，模拟 PG 的上下游 B 系统。
 * <p>
 * 基于 JDK 内置 {@link com.sun.net.httpserver.HttpServer}，无需额外依赖。
 * <ul>
 *   <li>启动后在 {@code pg-testkit.mock.port}（默认 9999）监听任意路径</li>
 *   <li>按配置的 {@link MockResponseRule} 列表顺序匹配，命中则返回预设响应</li>
 *   <li>未命中任何规则时返回 200 + 空体（兜底）</li>
 *   <li>所有收到的请求记录到内存列表，供 /test/mock-server/requests 查询</li>
 * </ul>
 */
@Slf4j
@Service
public class MockServerService {

    @Value("${pg-testkit.mock.port:9999}")
    private int mockPort;

    private HttpServer httpServer;

    /** 响应规则列表（按顺序匹配） */
    private final List<MockResponseRule> rules = new CopyOnWriteArrayList<>();

    /** 请求记录 */
    private final List<RequestRecord> requestRecords = Collections.synchronizedList(new ArrayList<>());

    private final AtomicLong requestSeq = new AtomicLong(0);

    /**
     * 启动 Mock 服务器。
     * 应用启动时自动调用，也可通过 /test/mock-server/start 手动重启。
     */
    @PostConstruct
    public void start() {
        if (httpServer != null) {
            log.warn("Mock 服务器已在运行（端口 {}）", mockPort);
            return;
        }
        try {
            httpServer = HttpServer.create(new InetSocketAddress(mockPort), 0);
            httpServer.createContext("/", new MockHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            log.info("Mock 服务器已启动，端口 {}", mockPort);
        } catch (IOException e) {
            throw new IllegalStateException("Mock 服务器启动失败，端口 " + mockPort, e);
        }
    }

    /**
     * 停止 Mock 服务器。
     */
    @PreDestroy
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            log.info("Mock 服务器已停止");
        }
    }

    /**
     * 重置所有规则和请求记录。
     */
    public void reset() {
        rules.clear();
        requestRecords.clear();
        requestSeq.set(0);
        log.info("Mock 服务器规则和请求记录已清空");
    }

    /**
     * 添加响应规则（追加到列表末尾）。
     */
    public void addRule(MockResponseRule rule) {
        rules.add(rule);
        log.info("已添加 Mock 规则：{}", rule.getName() == null ? "(未命名)" : rule.getName());
    }

    /**
     * 替换全部规则。
     */
    public void setRules(List<MockResponseRule> newRules) {
        rules.clear();
        if (newRules != null) {
            rules.addAll(newRules);
        }
        log.info("已设置 {} 条 Mock 规则", rules.size());
    }

    public List<MockResponseRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * 获取所有请求记录（按时间倒序）。
     */
    public List<RequestRecord> getRequestRecords() {
        List<RequestRecord> copy = new ArrayList<>(requestRecords);
        Collections.reverse(copy);
        return copy;
    }

    /**
     * 按路径过滤请求记录。
     */
    public List<RequestRecord> getRequestRecordsByPath(String path) {
        List<RequestRecord> result = new ArrayList<>();
        for (RequestRecord r : requestRecords) {
            if (path.equals(r.getPath())) {
                result.add(r);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * 查找匹配的响应规则。
     */
    private MockResponseRule findMatch(String path, String method,
                                       Map<String, String> headers, String body) {
        for (MockResponseRule rule : rules) {
            if (rule.match(path, method, headers, body)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Mock 请求处理器。
     */
    private class MockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String reqPath = exchange.getRequestURI().getPath();
                String reqMethod = exchange.getRequestMethod();
                Map<String, String> reqHeaders = new LinkedHashMap<>();
                exchange.getRequestHeaders().forEach((k, v) ->
                        reqHeaders.put(k, v == null || v.isEmpty() ? "" : v.get(0)));
                String reqBody = readBody(exchange);

                MockResponseRule matched = findMatch(reqPath, reqMethod, reqHeaders, reqBody);

                // 模拟延迟
                if (matched != null && matched.getDelayMs() > 0) {
                    try {
                        Thread.sleep(matched.getDelayMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                int status = matched == null ? 200 : matched.getStatus();
                String contentType = matched == null ? "application/json" : matched.getContentType();
                String respBody = matched == null ? "" : (matched.getBody() == null ? "" : matched.getBody());

                // 写响应头
                exchange.getResponseHeaders().add("Content-Type", contentType);
                if (matched != null && matched.getResponseHeaders() != null) {
                    matched.getResponseHeaders().forEach((k, v) ->
                            exchange.getResponseHeaders().add(k, v));
                }
                byte[] respBytes = respBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, respBytes.length == 0 ? -1 : respBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBytes);
                }

                // 记录请求
                RequestRecord record = new RequestRecord();
                record.setId(requestSeq.incrementAndGet());
                record.setTimestamp(System.currentTimeMillis());
                record.setMethod(reqMethod);
                record.setPath(reqPath);
                record.setHeaders(reqHeaders);
                record.setBody(reqBody);
                record.setMatchedRule(matched == null ? null : matched.getName());
                record.setResponseStatus(status);
                requestRecords.add(record);
            } catch (Exception e) {
                log.error("Mock 处理请求异常", e);
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            }
        }

        private String readBody(HttpExchange exchange) throws IOException {
            java.io.InputStream is = exchange.getRequestBody();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
