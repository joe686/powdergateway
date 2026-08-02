package com.powergateway.socket.inbound;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.codec.CharsetSupport;
import com.powergateway.socket.codec.FramingType;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * SOCKET 入站配置(v0.3.2 SOCK-5-A · Task 2)。
 *
 * <p>从 {@code interface_config.config_json.inbound} 段反解 · 与 v0.3.0 出站 SocketExecRequest 类似。</p>
 */
public class SocketInboundConfig {

    public static final int DEFAULT_READ_TIMEOUT_MS = 30000;
    public static final int DEFAULT_MAX_CONNECTIONS = 100;
    public static final String DEFAULT_CONNECTION_MODE = "short";

    /** 监听端口 · 必填 · 1-65535 */
    private int port;

    /** 分帧策略 · 必填 · Q5=C 三选一 */
    private FramingType framing;

    /** 编码 · 必填 · Q6=B 双选 */
    private Charset charset;

    /** 读超时毫秒 · 默认 30000(入站等渠道方发送 + 联机应答完成) */
    private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;

    /** 最大并发连接数 · 默认 100 */
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;

    /** connectionMode 契约:仅 short 实装(long/pooled 预留) · Q20=C */
    private String connectionMode = DEFAULT_CONNECTION_MODE;

    /** FunctionId 提取 XPath · 默认 //FunctionId(bank 与 host 都用此位置) */
    private String functionIdXPath = "//FunctionId";

    public static SocketInboundConfig fromMap(Map<String, Object> inbound) {
        if (inbound == null || inbound.isEmpty()) {
            throw new BusinessException("INBOUND_SOCKET 接口配置缺 config_json.inbound 段");
        }
        SocketInboundConfig cfg = new SocketInboundConfig();
        cfg.port = requireIntPositive(inbound, "port");
        if (cfg.port > 65535) {
            throw new BusinessException("port 需在 1~65535 · 收到:" + cfg.port);
        }
        cfg.framing = FramingType.parse(requireStringNotBlank(inbound, "framing"));
        cfg.charset = CharsetSupport.of(requireStringNotBlank(inbound, "charset"));
        cfg.readTimeoutMs = optInt(inbound, "readTimeoutMs", DEFAULT_READ_TIMEOUT_MS);
        cfg.maxConnections = optInt(inbound, "maxConnections", DEFAULT_MAX_CONNECTIONS);
        cfg.connectionMode = optString(inbound, "connectionMode", DEFAULT_CONNECTION_MODE);
        cfg.functionIdXPath = optString(inbound, "functionIdXPath", "//FunctionId");
        return cfg;
    }

    private static String requireStringNotBlank(Map<String, Object> src, String key) {
        Object v = src.get(key);
        if (v == null || v.toString().trim().isEmpty()) {
            throw new BusinessException("INBOUND config 缺必填字段:inbound." + key);
        }
        return v.toString().trim();
    }

    private static int requireIntPositive(Map<String, Object> src, String key) {
        Object v = src.get(key);
        if (v == null) throw new BusinessException("INBOUND config 缺必填字段:inbound." + key);
        int n = (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim());
        if (n <= 0) throw new BusinessException("inbound." + key + " 应为正数 · 收到:" + n);
        return n;
    }

    private static int optInt(Map<String, Object> src, String key, int def) {
        Object v = src.get(key);
        if (v == null) return def;
        return (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim());
    }

    private static String optString(Map<String, Object> src, String key, String def) {
        Object v = src.get(key);
        return (v == null || v.toString().isEmpty()) ? def : v.toString();
    }

    public int getPort() { return port; }
    public FramingType getFraming() { return framing; }
    public Charset getCharset() { return charset; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public int getMaxConnections() { return maxConnections; }
    public String getConnectionMode() { return connectionMode; }
    public String getFunctionIdXPath() { return functionIdXPath; }
}
