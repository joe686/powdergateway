package com.powergateway.testkit.mock;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Mock 响应规则配置。
 * <p>
 * 用于配置 Mock 服务器收到匹配请求后返回的响应。
 * 匹配条件全部为 null 时表示默认兜底规则。
 */
@Data
public class MockResponseRule {

    /** 规则名称（便于管理，可不填） */
    private String name;

    /** 匹配的 URL 路径（精确匹配，null 表示不限） */
    private String path;

    /** 匹配的 HTTP 方法（GET/POST/...，null 表示不限） */
    private String method;

    /** 匹配的请求头（KV，null 表示不限） */
    private Map<String, String> headers;

    /** 匹配的请求体包含的子串（null 表示不限） */
    private String bodyContains;

    /** 响应状态码，默认 200 */
    private int status = 200;

    /** 响应 Content-Type，默认 application/json */
    private String contentType = "application/json";

    /** 响应体 */
    private String body;

    /** 响应头 */
    private Map<String, String> responseHeaders;

    /** 响应延迟（毫秒），用于模拟超时场景 */
    private long delayMs = 0;

    /**
     * 判断请求是否匹配此规则。
     * 所有非 null 条件均需满足（AND 关系）。
     */
    public boolean match(String reqPath, String reqMethod,
                         Map<String, String> reqHeaders, String reqBody) {
        if (path != null && !path.equals(reqPath)) {
            return false;
        }
        if (method != null && !method.equalsIgnoreCase(reqMethod)) {
            return false;
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                String actual = reqHeaders == null ? null : reqHeaders.get(e.getKey());
                if (!e.getValue().equalsIgnoreCase(actual == null ? "" : actual)) {
                    return false;
                }
            }
        }
        if (bodyContains != null && (reqBody == null || !reqBody.contains(bodyContains))) {
            return false;
        }
        return true;
    }
}
