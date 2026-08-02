package com.powergateway.socket.route;

import java.util.Map;

/**
 * JSON 骨架配置(v0.3.2 SOCK-5-D · Task 6 · Q17=A)。
 *
 * <p>解决 host 与 bank 场景的 JSON 结构差异:</p>
 * <ul>
 *   <li><b>host 扁平模式</b>:4 路径全空 · wrap/unwrap 直接返 flatMap · 与 v0.3.0 出站语义一致</li>
 *   <li><b>bank 数组包 head/body</b>:配 $[0].body + $[0].head 骨架 · 收发都按此形态</li>
 * </ul>
 *
 * <p>路径语法简化(不引入 jsonpath 依赖):支持 {@code $} 根 + {@code [n]} 数组索引 + {@code .name} 字段。
 * 复杂 JSONPath(过滤/通配)不支持 · v0.4.0+ 若需可换 com.jayway.jsonpath。</p>
 */
public class JsonSkeletonConfig {

    /** 请求 body 写入位置 · 如 $[0].body */
    private String requestBodyPath;
    /** 请求 head 写入位置 · 如 $[0].head · 空则 head 字段与 body 平铺 */
    private String requestHeadPath;
    /** 应答 body 读取位置 · 如 $[0].body */
    private String responseBodyPath;
    /** 应答 list 读取位置(可选)· 如 $[0].body.list · 空则从 responseBodyPath 全取 */
    private String responseListPath;

    public static JsonSkeletonConfig fromMap(Map<String, Object> map) {
        JsonSkeletonConfig cfg = new JsonSkeletonConfig();
        if (map == null || map.isEmpty()) return cfg;
        cfg.requestBodyPath = optString(map, "requestBodyPath");
        cfg.requestHeadPath = optString(map, "requestHeadPath");
        cfg.responseBodyPath = optString(map, "responseBodyPath");
        cfg.responseListPath = optString(map, "responseListPath");
        return cfg;
    }

    /** 是否为扁平模式(4 路径全空 · host 场景)*/
    public boolean isFlat() {
        return isBlank(requestBodyPath) && isBlank(requestHeadPath)
                && isBlank(responseBodyPath) && isBlank(responseListPath);
    }

    private static String optString(Map<String, Object> src, String key) {
        Object v = src.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public String getRequestBodyPath() { return requestBodyPath; }
    public void setRequestBodyPath(String v) { this.requestBodyPath = v; }
    public String getRequestHeadPath() { return requestHeadPath; }
    public void setRequestHeadPath(String v) { this.requestHeadPath = v; }
    public String getResponseBodyPath() { return responseBodyPath; }
    public void setResponseBodyPath(String v) { this.responseBodyPath = v; }
    public String getResponseListPath() { return responseListPath; }
    public void setResponseListPath(String v) { this.responseListPath = v; }
}
