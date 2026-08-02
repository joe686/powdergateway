package com.powergateway.testkit.socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCP Socket Mock 服务端(v0.3.0 SOCK-4 · pg-testkit)。
 *
 * <p>纯 java.net.ServerSocket + 阻塞 IO · 简化实现 · 支持 XML_BOUNDARY 分帧 + UTF-8。</p>
 *
 * <p><b>启动</b>:socket-mock.enabled=true 时 @PostConstruct 起线程池监听端口 · @PreDestroy 优雅关闭。</p>
 * <p><b>协议</b>:短连接 · 收请求(读到 XML 根标签结束边界)· 提取 &lt;FunctionId&gt; 匹配 rule · 返 responseFile 内容 · 关闭连接。</p>
 *
 * <p>Canonical codec 实现见 {@code backend.socket.codec.XmlBoundaryCodec} · pg-testkit 内嵌简化版避免模块耦合。</p>
 */
@Component
public class SocketMockServer {

    private static final Logger log = LoggerFactory.getLogger(SocketMockServer.class);

    private static final Pattern FUNCTION_ID_PATTERN =
            Pattern.compile("<FunctionId>([^<]+)</FunctionId>", Pattern.DOTALL);

    private static final Pattern ROOT_TAG_PATTERN =
            Pattern.compile("<([A-Za-z_][A-Za-z0-9_-]*)(?:\\s[^>]*)?>", Pattern.DOTALL);

    @Autowired
    private SocketMockConfig config;

    private ServerSocket serverSocket;
    private ExecutorService acceptExecutor;
    private ExecutorService handlerExecutor;
    private volatile boolean running = false;

    // 缓存 responseFile 内容 · 避免每次读文件
    private final Map<String, byte[]> responseCache = new HashMap<>();

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            log.info("SOCK-4 · SocketMockServer 未启用(socket-mock.enabled=false)· 跳过启动");
            return;
        }
        try {
            serverSocket = new ServerSocket(config.getPort());
            running = true;
            preloadResponses();
            acceptExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "sock-mock-accept");
                t.setDaemon(true);
                return t;
            });
            handlerExecutor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "sock-mock-handler");
                t.setDaemon(true);
                return t;
            });
            acceptExecutor.submit(this::acceptLoop);
            log.info("SOCK-4 · SocketMockServer 启动:port={} rules={} 条", config.getPort(), config.getRules().size());
        } catch (IOException e) {
            log.error("SOCK-4 · SocketMockServer 启动失败:{}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignore) {
        }
        if (acceptExecutor != null) acceptExecutor.shutdownNow();
        if (handlerExecutor != null) {
            handlerExecutor.shutdown();
            try {
                handlerExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("SOCK-4 · SocketMockServer 已停止");
    }

    private void preloadResponses() {
        for (SocketMockRule rule : config.getRules()) {
            try {
                byte[] content = readClasspath(rule.getResponseFile());
                responseCache.put(rule.getFunctionId(), content);
                log.info("SOCK-4 · rule 加载:functionId={} responseFile={} bytes={}",
                        rule.getFunctionId(), rule.getResponseFile(), content.length);
            } catch (Exception e) {
                log.error("SOCK-4 · rule 加载失败:functionId={} responseFile={} · {}",
                        rule.getFunctionId(), rule.getResponseFile(), e.getMessage());
            }
        }
    }

    private byte[] readClasspath(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                handlerExecutor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running) log.warn("SOCK-4 · accept 异常:{}", e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket c = client) {
            c.setSoTimeout(5000);
            byte[] request = readXmlBoundary(c);
            if (request == null) {
                log.warn("SOCK-4 · 未收到完整 XML 请求 · 连接来自 {}", c.getRemoteSocketAddress());
                return;
            }
            String reqXml = new String(request, StandardCharsets.UTF_8);
            String functionId = extractFunctionId(reqXml);
            byte[] response;
            if (functionId != null && responseCache.containsKey(functionId)) {
                response = responseCache.get(functionId);
                log.info("SOCK-4 · 匹配 rule:functionId={} 返 {} bytes", functionId, response.length);
            } else {
                response = ("<error>FunctionId not found: " + functionId + "</error>").getBytes(StandardCharsets.UTF_8);
                log.warn("SOCK-4 · 未匹配 rule · functionId={}", functionId);
            }
            c.getOutputStream().write(response);
            c.getOutputStream().flush();
        } catch (Exception e) {
            log.warn("SOCK-4 · 处理请求异常:{}", e.getMessage());
        }
    }

    /**
     * 读到 XML 根标签结束边界(与 backend XmlBoundaryDecoder 语义一致)。
     * 简化实现:先读一批扫描根标签名 · 再持续读到 &lt;/根名&gt;。
     */
    static byte[] readXmlBoundary(Socket c) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        String rootTag = null;
        String endMark = null;
        while (true) {
            int n = c.getInputStream().read(chunk);
            if (n < 0) break;
            buf.write(chunk, 0, n);
            String current = new String(buf.toByteArray(), StandardCharsets.UTF_8);
            if (rootTag == null) {
                // 跳过 <?xml ... ?> 声明 · 找第一个元素标签
                String scan = current;
                int decl = scan.indexOf("<?xml");
                if (decl >= 0) {
                    int declEnd = scan.indexOf("?>", decl);
                    if (declEnd < 0) continue;
                    scan = scan.substring(declEnd + 2);
                }
                Matcher m = ROOT_TAG_PATTERN.matcher(scan);
                if (!m.find()) continue;
                rootTag = m.group(1);
                endMark = "</" + rootTag + ">";
            }
            if (current.contains(endMark)) {
                int endIdx = current.indexOf(endMark) + endMark.length();
                byte[] all = current.substring(0, endIdx).getBytes(StandardCharsets.UTF_8);
                return all;
            }
        }
        return buf.size() > 0 ? buf.toByteArray() : null;
    }

    static String extractFunctionId(String xml) {
        Matcher m = FUNCTION_ID_PATTERN.matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }
}
