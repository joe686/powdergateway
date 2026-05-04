package com.powergateway.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 查询接口 config_json 的 Java 对象表示。
 * 用于 M2-3 QueryBuilder 和后续单元（M2-5/M2-6）复用。
 */
@Data
public class QueryConfigJson {

    /** 参与查询的表列表，第一张为主表 */
    private List<TableDef> tables;

    /** JOIN 关联关系 */
    private List<JoinDef> joins;

    /** 返回字段列表，为空时 SELECT * */
    private List<FieldDef> fields;

    /** WHERE 条件列表 */
    private List<ConditionDef> conditions;

    /** 字段加工规则（M2-3 暂不处理，预留给后续单元） */
    private List<Object> processRules;

    @Data
    public static class TableDef {
        /** 实际表名 */
        private String name;
        /** SQL 别名 */
        private String alias;
    }

    @Data
    public static class JoinDef {
        /** 左表别名 */
        private String leftTable;
        /** 左表关联列 */
        private String leftCol;
        /** 右表别名 */
        private String rightTable;
        /** 右表关联列 */
        private String rightCol;
        /** JOIN 类型：LEFT / INNER / RIGHT */
        private String type;
    }

    @Data
    public static class FieldDef {
        /** 表别名 */
        private String table;
        /** 列名 */
        private String column;
        /** 输出别名 */
        private String alias;
    }

    @Data
    public static class ConditionDef {
        /** 带表别名的字段，如 "u.username" */
        private String field;
        /** 操作符：EQ / NE / GT / LT / LIKE */
        private String op;
        /** 对应的请求参数名 */
        private String paramKey;
    }
}
