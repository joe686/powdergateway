package com.powergateway.testkit.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试数据生成工厂。
 * <p>
 * 构造 PG 后端各 API 所需的请求体 JSON（以 Map 形式，由调用方序列化）。
 * 不依赖 backend 任何 DTO 类，避免耦合。
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    // ─────────────────────── 登录 ───────────────────────

    public static Map<String, Object> loginAdmin() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", "admin");
        m.put("password", "Admin@123");
        return m;
    }

    // ─────────────────────── 转换模板 ───────────────────────

    /**
     * 构造保存转换模板请求体。
     *
     * @param name        模板名称
     * @param srcFormat   源格式（JSON/XML/CSV/FORM_DATA）
     * @param targetFormat 目标格式
     * @param mappingRules 字段映射规则列表，每个元素含 srcField/targetField/fixedValue
     */
    public static Map<String, Object> saveTemplate(String name, String srcFormat, String targetFormat,
                                                   List<Map<String, Object>> mappingRules) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("srcFormat", srcFormat);
        m.put("targetFormat", targetFormat);
        m.put("mappingRules", mappingRules == null ? new ArrayList<>() : mappingRules);
        return m;
    }

    /** 构造一条字段映射规则 */
    public static Map<String, Object> mappingRule(String srcField, String targetField) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("srcField", srcField);
        r.put("targetField", targetField);
        return r;
    }

    /** 构造一条固定值映射规则 */
    public static Map<String, Object> fixedValueRule(String targetField, String fixedValue) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("srcField", null);
        r.put("targetField", targetField);
        r.put("fixedValue", fixedValue);
        return r;
    }

    // ─────────────────────── 渠道配置 ───────────────────────

    public static Map<String, Object> saveChannel(String channelCode, String channelName,
                                                  String identifyField, Long templateId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channelCode", channelCode);
        m.put("channelName", channelName);
        m.put("identifyField", identifyField);
        m.put("templateId", templateId);
        return m;
    }

    // ─────────────────────── 端口路由 ───────────────────────

    /**
     * 构造保存端口路由请求体。
     *
     * @param channelCode         渠道码
     * @param portAddress         目标 URL（通常是 Mock 服务器地址）
     * @param requestTemplateId  请求方向模板 ID（A→B）
     * @param responseTemplateId 应答方向模板 ID（B→A），可为 null
     */
    public static Map<String, Object> savePortRoute(String channelCode, String portAddress,
                                                    Long requestTemplateId, Long responseTemplateId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channelCode", channelCode);
        m.put("portAddress", portAddress);
        m.put("portMethod", "POST");
        m.put("timeout", 3000);
        m.put("retryCount", 1);
        m.put("requestTemplateId", requestTemplateId);
        if (responseTemplateId != null) {
            m.put("responseTemplateId", responseTemplateId);
        }
        return m;
    }

    // ─────────────────────── 数据库连接 ───────────────────────

    public static Map<String, Object> saveDbConnection(String name, String dbType, String url,
                                                       String username, String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("dbType", dbType);
        m.put("url", url);
        m.put("username", username);
        m.put("password", password);
        m.put("env", "dev");
        m.put("poolSize", 10);
        m.put("timeout", 3000);
        return m;
    }

    // ─────────────────────── 接口配置 ───────────────────────

    /**
     * 构造查询接口配置请求体（简化版，实际 9 步向导字段更多）。
     */
    public static Map<String, Object> saveQueryInterface(String name, Long dbConnectionId,
                                                         String tableName, List<String> columns) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("table", tableName);
        config.put("columns", columns);
        config.put("conditions", new ArrayList<>());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", "SELECT");
        m.put("dbConnectionId", dbConnectionId);
        m.put("configJson", config);
        return m;
    }

    // ─────────────────────── 报文转换调用 ───────────────────────

    public static Map<String, Object> convertWithTemplate(Long templateId, String message, String srcFormat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templateId", templateId);
        m.put("message", message);
        m.put("srcFormat", srcFormat);
        return m;
    }

    public static Map<String, Object> convertByChannel(String message, String srcFormat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        m.put("srcFormat", srcFormat);
        return m;
    }

    // ─────────────────────── 端口分发 ───────────────────────

    public static Map<String, Object> dispatch(String channelCode, String message, String srcFormat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("channelCode", channelCode);
        m.put("message", message);
        m.put("srcFormat", srcFormat);
        return m;
    }

    // ─────────────────────── 用户 ───────────────────────

    public static Map<String, Object> saveUser(String username, String password, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", username);
        m.put("password", password);
        m.put("role", role);
        return m;
    }
}
