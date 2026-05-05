package com.powergateway.utils;

import com.powergateway.model.dto.DeleteConfigJson.ConditionItem;

import java.util.*;

public class DeleteBuilder {

    public static SqlResult build(String tableName,
                                   List<ConditionItem> conditions,
                                   Map<String, Object> params) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件不能为空");
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ");
        List<Object> bindParams = new ArrayList<>();

        for (int i = 0; i < conditions.size(); i++) {
            ConditionItem cond = conditions.get(i);
            if (i > 0) sql.append(" AND ");
            sql.append(cond.getField()).append(opToSql(cond.getOp()));
            bindParams.add(params.get(cond.getParamKey()));
        }

        return new SqlResult(sql.toString(), bindParams);
    }

    /**
     * 构建 WHERE 子句，供 deletePreview 和 executeDelete 共用，避免重复循环逻辑。
     * @param bindParams 输出参数，方法内追加绑定值
     * @return " WHERE field op ? AND ..." 格式字符串
     */
    public static String buildWhere(List<ConditionItem> conditions,
                                     Map<String, Object> params,
                                     List<Object> bindParams) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件不能为空");
        }
        StringBuilder where = new StringBuilder(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            ConditionItem cond = conditions.get(i);
            if (i > 0) where.append(" AND ");
            where.append(cond.getField()).append(opToSql(cond.getOp()));
            bindParams.add(params.get(cond.getParamKey()));
        }
        return where.toString();
    }

    /** public — InterfaceConfigService（不同包）复用此方法构建 WHERE 子句 */
    public static String opToSql(String op) {
        if (op == null) return " = ?";
        switch (op) {
            case "EQ":   return " = ?";
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
