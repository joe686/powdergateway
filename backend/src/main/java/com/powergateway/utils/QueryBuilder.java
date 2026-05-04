package com.powergateway.utils;

import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.model.dto.QueryConfigJson.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据 QueryConfigJson 构建 SELECT SQL 语句。
 *
 * <ul>
 *   <li>支持单表和多表 LEFT/INNER/RIGHT JOIN</li>
 *   <li>支持条件操作符：EQ / NE / GT / LT / LIKE</li>
 *   <li>结果最多返回 LIMIT 10 行（预览用途）</li>
 *   <li>纯工具类，无 Spring 依赖，M2-3 新增，M2-4/5/6 可复用</li>
 * </ul>
 */
public class QueryBuilder {

    public static final int DEFAULT_LIMIT = 10;

    // ─── 公开 API ─────────────────────────────────────────────────────────────

    public static class SqlResult {
        public final String sql;
        public final List<Object> params;

        public SqlResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /**
     * 根据配置和运行时参数构建 SQL。
     *
     * @param config 配置对象（来自 interface_config.config_json 反序列化）
     * @param params 运行时参数（key=conditionDef.paramKey）
     * @return SQL 字符串 + 有序参数列表（对应 ? 占位符）
     */
    public static SqlResult build(QueryConfigJson config, Map<String, Object> params) {
        if (config == null || config.getTables() == null || config.getTables().isEmpty()) {
            throw new IllegalArgumentException("配置中至少需要一张表");
        }

        StringBuilder sql = new StringBuilder();
        List<Object> paramValues = new ArrayList<>();

        appendSelect(sql, config.getFields());
        appendFrom(sql, config.getTables().get(0));
        appendJoins(sql, config.getTables(), config.getJoins());
        appendWhere(sql, config.getConditions(), params, paramValues);
        sql.append(" LIMIT ").append(DEFAULT_LIMIT);

        return new SqlResult(sql.toString(), paramValues);
    }

    // ─── 私有方法 ─────────────────────────────────────────────────────────────

    private static void appendSelect(StringBuilder sql, List<FieldDef> fields) {
        sql.append("SELECT ");
        if (fields == null || fields.isEmpty()) {
            sql.append("*");
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sql.append(", ");
            FieldDef f = fields.get(i);
            sql.append(f.getTable()).append(".").append(f.getColumn());
            if (f.getAlias() != null && !f.getAlias().isEmpty()) {
                sql.append(" AS ").append(f.getAlias());
            }
        }
    }

    private static void appendFrom(StringBuilder sql, TableDef mainTable) {
        sql.append(" FROM ").append(mainTable.getName());
        if (mainTable.getAlias() != null && !mainTable.getAlias().isEmpty()) {
            sql.append(" ").append(mainTable.getAlias());
        }
    }

    private static void appendJoins(StringBuilder sql, List<TableDef> tables, List<JoinDef> joins) {
        if (joins == null || joins.isEmpty()) return;
        for (JoinDef join : joins) {
            String rightTableName = resolveTableName(tables, join.getRightTable());
            String joinType = join.getType() != null ? join.getType().toUpperCase() : "LEFT";
            sql.append(" ").append(joinType).append(" JOIN ")
               .append(rightTableName).append(" ").append(join.getRightTable())
               .append(" ON ")
               .append(join.getLeftTable()).append(".").append(join.getLeftCol())
               .append(" = ")
               .append(join.getRightTable()).append(".").append(join.getRightCol());
        }
    }

    private static void appendWhere(StringBuilder sql, List<ConditionDef> conditions,
                                    Map<String, Object> params, List<Object> paramValues) {
        if (conditions == null || conditions.isEmpty()) return;

        List<String> clauses = new ArrayList<>();
        for (ConditionDef cond : conditions) {
            Object value = params != null ? params.get(cond.getParamKey()) : null;
            if (value == null) continue; // 参数未提供则忽略该条件
            clauses.add(buildClause(cond.getField(), cond.getOp(), value, paramValues));
        }
        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", clauses));
        }
    }

    /** 将别名映射回实际表名 */
    private static String resolveTableName(List<TableDef> tables, String alias) {
        return tables.stream()
                .filter(t -> alias.equals(t.getAlias()))
                .map(TableDef::getName)
                .findFirst()
                .orElse(alias);
    }

    /** 根据操作符构建单个条件子句，同时向 paramValues 追加绑定值 */
    private static String buildClause(String field, String op, Object value, List<Object> paramValues) {
        switch (op.toUpperCase()) {
            case "NE":
                paramValues.add(value);
                return field + " <> ?";
            case "GT":
                paramValues.add(value);
                return field + " > ?";
            case "LT":
                paramValues.add(value);
                return field + " < ?";
            case "LIKE":
                paramValues.add("%" + value + "%");
                return field + " LIKE ?";
            case "EQ":
            default:
                paramValues.add(value);
                return field + " = ?";
        }
    }
}
