package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class DeleteConfigJson {

    private List<TableDeleteConfig> tables;

    @Data
    public static class TableDeleteConfig {
        private String tableName;
        private List<ConditionItem> conditions;
    }

    /**
     * 单表条件项。DELETE 每张表独立持有自己的条件列表，无需 tableName 字段区分，
     * 与 UpdateConfigJson.ConditionConfig（多表共享列表）不同。
     */
    @Data
    public static class ConditionItem {
        /** WHERE 字段名 */
        private String field;
        /** 操作符：EQ / NE / GT / LT / LIKE */
        private String op;
        /** 从请求参数 Map 中取值的 key */
        private String paramKey;
    }
}
