package com.powergateway.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 根据表名和字段值映射构建 INSERT SQL（M2-4）。
 *
 * <p>生成格式：{@code INSERT INTO table (col1, col2) VALUES (?, ?)}
 * <p>纯工具类，无 Spring 依赖。
 */
public class InsertBuilder {

    public static class SqlResult {
        public final String sql;
        public final List<Object> params;

        public SqlResult(String sql, List<Object> params) {
            this.sql    = sql;
            this.params = params;
        }
    }

    /**
     * 构建 INSERT SQL。
     *
     * @param tableName   目标表名
     * @param fieldValues 有序的列名 → 值映射
     * @return SQL 字符串 + 有序参数列表
     */
    public static SqlResult build(String tableName, Map<String, Object> fieldValues) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (fieldValues == null || fieldValues.isEmpty()) {
            throw new IllegalArgumentException("插入字段不能为空");
        }

        List<String> columns = new ArrayList<>(fieldValues.keySet());
        List<Object> params  = columns.stream().map(fieldValues::get).collect(Collectors.toList());

        String cols         = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

        String sql = "INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")";
        return new SqlResult(sql, params);
    }
}
