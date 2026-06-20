package com.powergateway.testkit.assertkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;

/**
 * PG 专用断言工具集。
 * <p>
 * 封装对 PG 后端响应、数据库状态、审计记录的常用断言。
 * 基于 JUnit 5 {@link Assertions}，可直接在测试类中使用。
 * <p>
 * 位于 src/test/java（依赖 JUnit，仅测试期可用）。
 */
public final class PgAssertions {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PgAssertions() {
    }

    // ─────────────────────── 响应格式断言 ───────────────────────

    /**
     * 断言 PG 统一响应体格式：{ "code": 200, "message": "...", "data": ... }
     */
    public static JsonNode assertPgResult(String responseBody, int expectedCode) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        Assertions.assertTrue(root.has("code"), "响应体缺少 code 字段");
        Assertions.assertEquals(expectedCode, root.get("code").asInt(),
                "响应 code 不匹配，message=" + root.path("message").asText());
        Assertions.assertTrue(root.has("message"), "响应体缺少 message 字段");
        return root;
    }

    /** 断言响应成功（code=200）并返回 data 节点 */
    public static JsonNode assertSuccess(String responseBody) throws Exception {
        JsonNode root = assertPgResult(responseBody, 200);
        return root.get("data");
    }

    /** 断言响应体 data 节点包含指定字段 */
    public static void assertDataContains(String responseBody, String field) throws Exception {
        JsonNode data = assertSuccess(responseBody);
        Assertions.assertTrue(data.has(field), "响应 data 缺少字段：" + field);
    }

    /** 断言响应体包含指定文本（用于报文转换结果校验） */
    public static void assertBodyContains(String responseBody, String expectedText) {
        Assertions.assertTrue(responseBody.contains(expectedText),
                "响应体未包含预期文本：" + expectedText + "，实际：" + responseBody);
    }

    // ─────────────────────── 数据库断言 ───────────────────────

    /**
     * 断言 SQL 查询结果至少有一条记录。
     *
     * @param rows JDBC 查询返回的行列表（每行为 Map）
     */
    public static void assertHasRows(List<Map<String, Object>> rows) {
        Assertions.assertFalse(rows == null || rows.isEmpty(), "预期至少有一条记录，实际为空");
    }

    /**
     * 断言 SQL 查询结果记录数等于预期。
     */
    public static void assertRowCount(List<Map<String, Object>> rows, int expected) {
        int actual = rows == null ? 0 : rows.size();
        Assertions.assertEquals(expected, actual, "记录数不匹配");
    }

    /**
     * 断言某行某字段等于预期值。
     */
    public static void assertFieldValue(Map<String, Object> row, String field, Object expected) {
        Assertions.assertNotNull(row, "查询结果行为空");
        Assertions.assertTrue(row.containsKey(field), "行中缺少字段：" + field);
        Assertions.assertEquals(String.valueOf(expected), String.valueOf(row.get(field)),
                "字段 " + field + " 值不匹配");
    }

    /**
     * 断言密码字段为 BCrypt 密文（以 $2a$/$2b$ 开头）。
     */
    public static void assertBcryptPassword(String password) {
        Assertions.assertNotNull(password, "密码为空");
        Assertions.assertTrue(password.startsWith("$2a$") || password.startsWith("$2b$"),
                "密码不是 BCrypt 密文：" + password);
    }

    /**
     * 断言密码字段为 AES 密文（非明文，长度 > 16）。
     */
    public static void assertAesEncrypted(String encrypted) {
        Assertions.assertNotNull(encrypted, "加密字段为空");
        Assertions.assertTrue(encrypted.length() > 16, "AES 密文长度异常：" + encrypted);
        Assertions.assertFalse(encrypted.matches("^[a-zA-Z]+://.*"), "疑似明文 URL/连接串：" + encrypted);
    }

    // ─────────────────────── Mock 请求断言 ───────────────────────

    /**
     * 断言 Mock 服务器收到的请求数量。
     */
    public static void assertMockRequestCount(int actual, int expected) {
        Assertions.assertEquals(expected, actual, "Mock 收到的请求数不匹配");
    }

    /**
     * 断言 Mock 收到的请求体包含指定文本。
     */
    public static void assertMockBodyContains(String mockBody, String expected) {
        Assertions.assertNotNull(mockBody, "Mock 请求体为空");
        Assertions.assertTrue(mockBody.contains(expected),
                "Mock 请求体未包含预期文本：" + expected + "，实际：" + mockBody);
    }
}
