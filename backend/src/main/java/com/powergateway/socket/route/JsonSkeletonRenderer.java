package com.powergateway.socket.route;

import com.powergateway.exception.BusinessException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON 骨架渲染器(v0.3.2 SOCK-5-D · Task 6 · Q17=A)。
 *
 * <p><b>wrap</b>:host flat + bank 数组包 head/body 两种形态 · 按 skeleton 4 路径切换</p>
 * <p><b>unwrap</b>:反向 · 从 bankJson 按路径取业务体 · 或 host flat 直接返</p>
 *
 * <p><b>简化 JSONPath 语法</b>(不引外部依赖):</p>
 * <ul>
 *   <li>{@code $} · 根</li>
 *   <li>{@code $[n]} · 根下第 n 元素(数组索引)</li>
 *   <li>{@code $[n].body} / {@code $.body.list[0]} · 混合 · dot 与 [n] 交替</li>
 * </ul>
 * <p>不支持:过滤 {@code $[?(@.id==1)]} · 通配 {@code $[*]} · 递归 {@code $..} · 走 v0.4.0+ 若需 com.jayway.jsonpath</p>
 */
public class JsonSkeletonRenderer {

    private static final Pattern SEGMENT = Pattern.compile("\\[(\\d+)\\]|\\.?([A-Za-z_][A-Za-z0-9_]*)");

    private JsonSkeletonRenderer() {
    }

    /**
     * 请求侧 wrap:把 body/head 映射写入骨架结构。
     *
     * @param bodyData 业务 body(如 bank 的 bizBody 转 map · 或 host 的整报文 map)
     * @param headData 请求 head(如 bank 的 bizHeader 转 map · 可 null)
     * @param cfg      骨架配置
     * @return 骨架根(flat 模式直接返 bodyData · bank 模式返 [{head,body}] 结构)
     */
    public static Object wrapRequest(Map<String, Object> bodyData, Map<String, Object> headData, JsonSkeletonConfig cfg) {
        if (cfg == null || cfg.isFlat()) {
            // host 扁平:合并 head + body(head 优先)
            if (headData == null || headData.isEmpty()) return bodyData;
            Map<String, Object> merged = new LinkedHashMap<>();
            if (headData != null) merged.putAll(headData);
            if (bodyData != null) merged.putAll(bodyData);
            return merged;
        }
        // 骨架模式
        Object root = new ArrayList<>();
        if (cfg.getRequestBodyPath() != null && !cfg.getRequestBodyPath().isEmpty()) {
            root = setByPath(root, cfg.getRequestBodyPath(), bodyData);
        }
        if (cfg.getRequestHeadPath() != null && !cfg.getRequestHeadPath().isEmpty() && headData != null) {
            root = setByPath(root, cfg.getRequestHeadPath(), headData);
        }
        return root;
    }

    /**
     * 应答侧 unwrap:从骨架结构提取业务体 · list 优先(bank 数组场景)· 否则取 body。
     */
    @SuppressWarnings("unchecked")
    public static Object unwrapResponse(Object bankJson, JsonSkeletonConfig cfg) {
        if (cfg == null || cfg.isFlat()) {
            return bankJson;
        }
        if (cfg.getResponseListPath() != null && !cfg.getResponseListPath().isEmpty()) {
            Object list = getByPath(bankJson, cfg.getResponseListPath());
            if (list != null) return list;
        }
        if (cfg.getResponseBodyPath() != null && !cfg.getResponseBodyPath().isEmpty()) {
            Object body = getByPath(bankJson, cfg.getResponseBodyPath());
            if (body != null) return body;
        }
        return bankJson;
    }

    // ============ 简化 JSONPath 求值/赋值 ============

    /** 沿 path 取值 · 走不通返 null */
    @SuppressWarnings("unchecked")
    public static Object getByPath(Object root, String path) {
        List<Object> segments = parseSegments(path);
        Object cur = root;
        for (Object seg : segments) {
            if (cur == null) return null;
            if (seg instanceof Integer) {
                int idx = (Integer) seg;
                if (!(cur instanceof List)) return null;
                List<Object> list = (List<Object>) cur;
                if (idx < 0 || idx >= list.size()) return null;
                cur = list.get(idx);
            } else {
                String name = (String) seg;
                if (!(cur instanceof Map)) return null;
                cur = ((Map<String, Object>) cur).get(name);
            }
        }
        return cur;
    }

    /** 沿 path 写值 · 中间节点缺失自动创建 · 返回可能改变的根引用 */
    @SuppressWarnings("unchecked")
    public static Object setByPath(Object root, String path, Object value) {
        List<Object> segments = parseSegments(path);
        if (segments.isEmpty()) return value;

        // 若 root 类型与首段不匹配 · 换成匹配的空容器
        Object first = segments.get(0);
        if (first instanceof Integer && !(root instanceof List)) {
            root = new ArrayList<>();
        } else if (first instanceof String && !(root instanceof Map)) {
            root = new LinkedHashMap<>();
        }

        Object cur = root;
        for (int i = 0; i < segments.size(); i++) {
            Object seg = segments.get(i);
            boolean last = (i == segments.size() - 1);
            Object next = null;
            if (!last) {
                Object nextSeg = segments.get(i + 1);
                next = (nextSeg instanceof Integer) ? new ArrayList<>() : new LinkedHashMap<>();
            }
            if (seg instanceof Integer) {
                int idx = (Integer) seg;
                if (!(cur instanceof List)) {
                    throw new BusinessException("JsonSkeleton · 路径 " + path + " 处期望数组 · 收到 " + cur.getClass().getSimpleName());
                }
                List<Object> list = (List<Object>) cur;
                while (list.size() <= idx) list.add(null);
                if (last) {
                    list.set(idx, value);
                } else {
                    Object existing = list.get(idx);
                    if (existing == null) {
                        list.set(idx, next);
                        cur = next;
                    } else {
                        cur = existing;
                    }
                }
            } else {
                String name = (String) seg;
                if (!(cur instanceof Map)) {
                    throw new BusinessException("JsonSkeleton · 路径 " + path + " 处期望对象 · 收到 " + cur.getClass().getSimpleName());
                }
                Map<String, Object> map = (Map<String, Object>) cur;
                if (last) {
                    map.put(name, value);
                } else {
                    Object existing = map.get(name);
                    if (existing == null) {
                        map.put(name, next);
                        cur = next;
                    } else {
                        cur = existing;
                    }
                }
            }
        }
        return root;
    }

    /**
     * 解析路径为段列表:Integer(数组索引)or String(字段名)。
     * 输入 "$[0].body" → [0, "body"];"$.a.b[2].c" → ["a", "b", 2, "c"]
     */
    public static List<Object> parseSegments(String path) {
        List<Object> out = new ArrayList<>();
        if (path == null || path.isEmpty()) return out;
        String p = path.startsWith("$") ? path.substring(1) : path;
        if (p.isEmpty()) return out;
        Matcher m = SEGMENT.matcher(p);
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) {
                throw new BusinessException("JsonSkeleton · JSONPath 语法非法(位置 " + pos + "):" + path);
            }
            String idxStr = m.group(1);
            String name = m.group(2);
            if (idxStr != null) {
                out.add(Integer.parseInt(idxStr));
            } else if (name != null) {
                out.add(name);
            }
            pos = m.end();
        }
        if (pos != p.length()) {
            throw new BusinessException("JsonSkeleton · JSONPath 语法非法(末尾多余字符):" + path);
        }
        return out;
    }
}
