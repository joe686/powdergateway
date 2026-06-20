package com.powergateway.testkit.mock;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock 服务器收到的请求记录。
 * <p>
 * 每次收到请求都会记录一条，供 AI 通过 /test/mock-server/requests 查询验证。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestRecord {

    /** 自增序号 */
    private long id;

    /** 收到时间戳（毫秒） */
    private long timestamp;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String path;

    /** 请求头 */
    private Map<String, String> headers = new LinkedHashMap<>();

    /** 请求体 */
    private String body;

    /** 匹配的规则名称（未匹配则为 null） */
    private String matchedRule;

    /** 实际返回的状态码 */
    private int responseStatus;
}
