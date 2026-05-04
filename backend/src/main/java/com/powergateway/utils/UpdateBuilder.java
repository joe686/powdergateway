package com.powergateway.utils;

import com.powergateway.model.dto.UpdateConfigJson.ConditionConfig;

import java.util.*;

/**
 * UPDATE SQL 构建器（M2-5）。
 * 调用方按 tableName 过滤 conditions 后传入（只传该表的条件）。
 */
public class UpdateBuilder {

    public static SqlResult build(String tableName,
                                   Map<String, Object> fieldValues,
                                   List<ConditionConfig> conditions,
                                   Map<String, Object> params) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            throw new IllegalArgumentException("修改字段不能为空");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件不能为空");
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        List<Object> bindParams = new ArrayList<>();

        Iterator<Map.Entry<String, Object>> it = fieldValues.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            sql.append(entry.getKey()).append(" = ?");
            bindParams.add(entry.getValue());
            if (it.hasNext()) sql.append(", ");
        }

        sql.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            ConditionConfig cond = conditions.get(i);
            if (i > 0) sql.append(" AND ");
            sql.append(cond.getField()).append(opToSql(cond.getOp()));
            bindParams.add(params.get(cond.getParamKey()));
        }

        return new SqlResult(sql.toString(), bindParams);
    }

    private static String opToSql(String op) {
        if (op == null) return " = ?";
        switch (op) {
            case "NE":   return " <> ?";
            case "GT":   return " > ?";
            case "LT":   return " < ?";
            case "LIKE": return " LIKE ?";
            default:     return " = ?";
        }
    }

    public static class SqlResult {
        public final String sql;
        public final List<Object> params;

        public SqlResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
