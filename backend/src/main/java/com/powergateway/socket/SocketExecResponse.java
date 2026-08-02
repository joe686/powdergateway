package com.powergateway.socket;

import java.util.Map;

/**
 * SOCKET 接口执行响应 DTO(v0.3.0 SOCK-1 · Task 6)。
 *
 * <p>由 {@link SocketExecutor#execute} 返回 · 作为 exec 接口 JSON 响应体。</p>
 */
public class SocketExecResponse {

    /** 原始 XML 应答报文(字符串 · 已按 charset 解码) */
    private String rawXml;

    /** 扁平化后的字段 Map(嵌套 {a:{b:"v"}} → {"a.b":"v"}) */
    private Map<String, String> flattened;

    /** 端到端延时毫秒(发送 → 收到应答) */
    private long latencyMs;

    public SocketExecResponse() {
    }

    public SocketExecResponse(String rawXml, Map<String, String> flattened, long latencyMs) {
        this.rawXml = rawXml;
        this.flattened = flattened;
        this.latencyMs = latencyMs;
    }

    public String getRawXml() { return rawXml; }
    public void setRawXml(String rawXml) { this.rawXml = rawXml; }

    public Map<String, String> getFlattened() { return flattened; }
    public void setFlattened(Map<String, String> flattened) { this.flattened = flattened; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
}
