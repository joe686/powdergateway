package com.powergateway.socket.outbound;

import com.powergateway.exception.BusinessException;

import java.util.Map;

/**
 * HTTP 出站请求 DTO(v0.3.2 SOCK-5-B · Task 4)。
 *
 * <p>从 {@code interface_config.config_json.outbound} 段反解。</p>
 */
public class HttpOutboundRequest {

    /** 目标应用名(Eureka 注册的 service name)· 如 lcpt-hstc-online-query · 必填 */
    private String applicationName;

    /** 目标路径 · 如 /hstc/query/online/ · 必填 */
    private String path;

    /** HTTP 方法 · 默认 POST */
    private String method = "POST";

    /** 连接超时毫秒 · 默认 3000 */
    private int connTimeoutMs = 3000;

    /** 读超时毫秒 · 默认 10000 */
    private int readTimeoutMs = 10000;

    public static HttpOutboundRequest fromMap(Map<String, Object> outbound) {
        if (outbound == null || outbound.isEmpty()) {
            throw new BusinessException("INBOUND_SOCKET 接口配置缺 config_json.outbound 段");
        }
        HttpOutboundRequest req = new HttpOutboundRequest();
        req.applicationName = requireString(outbound, "applicationName");
        req.path = requireString(outbound, "path");
        req.method = optString(outbound, "method", "POST").toUpperCase();
        req.connTimeoutMs = optInt(outbound, "connTimeoutMs", 3000);
        req.readTimeoutMs = optInt(outbound, "readTimeoutMs", 10000);
        return req;
    }

    private static String requireString(Map<String, Object> src, String key) {
        Object v = src.get(key);
        if (v == null || v.toString().trim().isEmpty()) {
            throw new BusinessException("outbound." + key + " 必填");
        }
        return v.toString().trim();
    }

    private static String optString(Map<String, Object> src, String key, String def) {
        Object v = src.get(key);
        return (v == null || v.toString().isEmpty()) ? def : v.toString();
    }

    private static int optInt(Map<String, Object> src, String key, int def) {
        Object v = src.get(key);
        if (v == null) return def;
        return (v instanceof Number) ? ((Number) v).intValue() : Integer.parseInt(v.toString().trim());
    }

    public String getApplicationName() { return applicationName; }
    public String getPath() { return path; }
    public String getMethod() { return method; }
    public int getConnTimeoutMs() { return connTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
}
