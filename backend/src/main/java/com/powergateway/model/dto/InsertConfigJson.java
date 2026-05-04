package com.powergateway.model.dto;

import lombok.Data;

import java.util.List;

/**
 * INSERT 接口的 config_json 结构（M2-4）。
 *
 * <pre>
 * {
 *   "tables": [
 *     {
 *       "tableName": "orders",
 *       "fields": [
 *         {"column": "user_id",      "sourceType": "REQUEST", "paramKey":    "userId"},
 *         {"column": "remark",       "sourceType": "CONST",   "constValue":  "默认备注"},
 *         {"column": "total_amount", "sourceType": "CALC",    "expression":  "price * qty"}
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 */
@Data
public class InsertConfigJson {

    /** 最多3张表 */
    private List<TableInsertConfig> tables;

    @Data
    public static class TableInsertConfig {
        private String tableName;
        private List<FieldInsertConfig> fields;
    }

    @Data
    public static class FieldInsertConfig {
        /** 目标列名 */
        private String column;

        /** 数据来源类型：REQUEST / CONST / CALC */
        private String sourceType;

        /** sourceType=REQUEST 时，对应请求参数的 key */
        private String paramKey;

        /** sourceType=CONST 时，固定值 */
        private String constValue;

        /** sourceType=CALC 时，四则运算表达式（操作数为数字或请求参数名） */
        private String expression;
    }
}
