package com.powergateway.service.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FN-11 · 报文体嵌套字段路径工具
 *
 * 语法：{@code a.b.c}（对象嵌套）+ {@code [*]}（数组元素占位）。
 * 示例：
 * <ul>
 *   <li>{@code head.FunctionId} → 读取普通嵌套字段</li>
 *   <li>{@code body.items[*].amount} → 读取数组每个元素的 amount，返回 {@code List}</li>
 *   <li>{@code items[*]} → 直接返回整个数组</li>
 * </ul>
 *
 * 与 CHG-004 的 {@code getNestedValue} 点号路径习惯保持一致，扩展 {@code [*]} 支持数组。
 */
public final class PathExpression {

    /** 数组元素占位符 */
    public static final String ARRAY_MARKER = "[*]";

    private PathExpression() {
    }

    /** 把路径字符串拆分成段。空/null 返回空 list。 */
    public static List<String> tokenize(String path) {
        List<String> tokens = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return tokens;
        }
        StringBuilder buf = new StringBuilder();
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') {
                if (buf.length() > 0) {
                    tokens.add(buf.toString());
                    buf.setLength(0);
                }
                i++;
            } else if (c == '[') {
                if (buf.length() > 0) {
                    tokens.add(buf.toString());
                    buf.setLength(0);
                }
                int end = path.indexOf(']', i);
                if (end < 0) {
                    throw new IllegalArgumentException("路径缺少闭合的 ] : " + path);
                }
                String seg = path.substring(i, end + 1);
                if (!ARRAY_MARKER.equals(seg)) {
                    throw new IllegalArgumentException("目前只支持 [*] 数组标记，收到 " + seg);
                }
                tokens.add(ARRAY_MARKER);
                i = end + 1;
            } else {
                buf.append(c);
                i++;
            }
        }
        if (buf.length() > 0) {
            tokens.add(buf.toString());
        }
        return tokens;
    }

    /** 把 token 列表拼回路径字符串（保持 tokenize 的可逆性）。 */
    public static String join(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            if (ARRAY_MARKER.equals(t)) {
                sb.append(ARRAY_MARKER);
            } else {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ']') {
                    sb.append('.');
                } else if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(t);
            }
        }
        return sb.toString();
    }

    /** 按路径从 Map/List 混合树里取值。路径不存在返回 null；根为 null 返回 null；空路径返回根本身。 */
    public static Object read(Object root, String path) {
        if (root == null) {
            return null;
        }
        List<String> tokens = tokenize(path);
        if (tokens.isEmpty()) {
            return root;
        }
        return readTokens(root, tokens, 0);
    }

    private static Object readTokens(Object node, List<String> tokens, int idx) {
        if (idx >= tokens.size()) {
            return node;
        }
        if (node == null) {
            return null;
        }
        String tok = tokens.get(idx);
        if (ARRAY_MARKER.equals(tok)) {
            if (!(node instanceof List)) {
                return null;
            }
            List<?> list = (List<?>) node;
            // 叶子标记：直接返回整个 List
            if (idx == tokens.size() - 1) {
                return list;
            }
            // 中间标记：对每个元素递归取剩余路径，聚合成新 List
            List<Object> collected = new ArrayList<>(list.size());
            for (Object elem : list) {
                collected.add(readTokens(elem, tokens, idx + 1));
            }
            return collected;
        }
        if (!(node instanceof Map)) {
            return null;
        }
        Object next = ((Map<?, ?>) node).get(tok);
        return readTokens(next, tokens, idx + 1);
    }

    /** 按路径写入值。中间对象/数组按需创建；根为 null 抛 NPE；空路径抛 IAE；数组标记要求 value 是 List。 */
    public static void write(Map<String, Object> root, String path, Object value) {
        Objects.requireNonNull(root, "root 不能为 null");
        List<String> tokens = tokenize(path);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        writeTokens(root, tokens, 0, value);
    }

    @SuppressWarnings("unchecked")
    private static void writeTokens(Object node, List<String> tokens, int idx, Object value) {
        String tok = tokens.get(idx);
        boolean last = (idx == tokens.size() - 1);

        if (ARRAY_MARKER.equals(tok)) {
            // 到达 [*] 时 node 应是 List（上一步已经放置好 List 容器）
            if (!(node instanceof List)) {
                throw new IllegalStateException("期望 List，实际是 " + (node == null ? "null" : node.getClass().getSimpleName()));
            }
            List<Object> list = (List<Object>) node;
            if (!(value instanceof List)) {
                throw new IllegalArgumentException("写入 [*] 路径要求 value 是 List，收到 " + (value == null ? "null" : value.getClass().getSimpleName()));
            }
            List<?> values = (List<?>) value;
            if (last) {
                list.clear();
                list.addAll(values);
                return;
            }
            // 中间 [*]：为 values 中每一个元素创建一个 Map 容器，写入下一层
            list.clear();
            for (Object v : values) {
                Map<String, Object> child = new LinkedHashMap<>();
                list.add(child);
                writeTokens(child, tokens, idx + 1, v);
            }
            return;
        }

        // 普通 key：node 必须是 Map
        if (!(node instanceof Map)) {
            throw new IllegalStateException("期望 Map，实际是 " + (node == null ? "null" : node.getClass().getSimpleName()));
        }
        Map<String, Object> map = (Map<String, Object>) node;

        if (last) {
            map.put(tok, value);
            return;
        }

        String nextTok = tokens.get(idx + 1);
        Object child = map.get(tok);
        if (ARRAY_MARKER.equals(nextTok)) {
            // 下一段是 [*] → 当前 key 存 List
            if (!(child instanceof List)) {
                child = new ArrayList<>();
                map.put(tok, child);
            }
        } else {
            // 下一段是普通 key → 当前 key 存 Map
            if (!(child instanceof Map)) {
                child = new LinkedHashMap<>();
                map.put(tok, child);
            }
        }
        writeTokens(child, tokens, idx + 1, value);
    }
}
