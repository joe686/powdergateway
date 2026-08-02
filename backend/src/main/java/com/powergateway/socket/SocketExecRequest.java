package com.powergateway.socket;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.codec.CharsetSupport;
import com.powergateway.socket.codec.FramingType;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * SOCKET 接口执行请求 DTO(v0.3.0 SOCK-1 · Task 5)。
 *
 * <p>从 {@code interface_config.config_json} 的 {@code socket} 段反解为强类型对象。</p>
 *
 * <p>字段约定见 {@code db/migration-v0.3.0-socket.sql} 头部注释。</p>
 */
public class SocketExecRequest {

    public static final int DEFAULT_CONN_TIMEOUT_MS = 3000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    public static final String DEFAULT_CONNECTION_MODE = "short";

    private String ip;
    private int port;
    private FramingType framing;
    private Charset charset;
    private int connTimeoutMs = DEFAULT_CONN_TIMEOUT_MS;
    private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    private String connectionMode = DEFAULT_CONNECTION_MODE;
    private String requestTemplate;
    private String responseFlattenPrefix = "";

    /**
     * 从 config_json.socket 段(已反序列化为 Map)构造。
     *
     * @param socket socket 子对象 · 必填不能为 null
     * @return 强类型 SocketExecRequest
     * @throws BusinessException 缺必填字段或字段非法
     */
    public static SocketExecRequest fromMap(Map<String, Object> socket) {
        if (socket == null || socket.isEmpty()) {
            throw new BusinessException("SOCKET 接口配置缺 config_json.socket 段");
        }
        SocketExecRequest req = new SocketExecRequest();
        req.ip = requireStringNotBlank(socket, "ip");
        req.port = requireIntPositive(socket, "port");
        req.framing = FramingType.parse(requireStringNotBlank(socket, "framing"));
        req.charset = CharsetSupport.of(requireStringNotBlank(socket, "charset"));
        req.requestTemplate = requireStringNotBlank(socket, "requestTemplate");
        req.connTimeoutMs = optInt(socket, "connTimeoutMs", DEFAULT_CONN_TIMEOUT_MS);
        req.readTimeoutMs = optInt(socket, "readTimeoutMs", DEFAULT_READ_TIMEOUT_MS);
        req.connectionMode = optString(socket, "connectionMode", DEFAULT_CONNECTION_MODE);
        req.responseFlattenPrefix = optString(socket, "responseFlattenPrefix", "");
        return req;
    }

    // ============ 反解 helper ============

    private static String requireStringNotBlank(Map<String, Object> src, String key) {
        Object v = src.get(key);
        if (v == null || v.toString().trim().isEmpty()) {
            throw new BusinessException("SOCKET config 缺必填字段:socket." + key);
        }
        return v.toString().trim();
    }

    private static int requireIntPositive(Map<String, Object> src, String key) {
        Object v = src.get(key);
        if (v == null) {
            throw new BusinessException("SOCKET config 缺必填字段:socket." + key);
        }
        int n;
        try {
            n = (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("SOCKET config 字段 " + key + " 应为整数 · 收到:" + v);
        }
        if (n <= 0) {
            throw new BusinessException("SOCKET config 字段 " + key + " 应为正数 · 收到:" + n);
        }
        return n;
    }

    private static int optInt(Map<String, Object> src, String key, int def) {
        Object v = src.get(key);
        if (v == null) return def;
        try {
            return (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("SOCKET config 字段 " + key + " 应为整数 · 收到:" + v);
        }
    }

    private static String optString(Map<String, Object> src, String key, String def) {
        Object v = src.get(key);
        return (v == null || v.toString().isEmpty()) ? def : v.toString();
    }

    // ============ getters ============

    public String getIp() { return ip; }
    public int getPort() { return port; }
    public FramingType getFraming() { return framing; }
    public Charset getCharset() { return charset; }
    public int getConnTimeoutMs() { return connTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public String getConnectionMode() { return connectionMode; }
    public String getRequestTemplate() { return requestTemplate; }
    public String getResponseFlattenPrefix() { return responseFlattenPrefix; }
}
