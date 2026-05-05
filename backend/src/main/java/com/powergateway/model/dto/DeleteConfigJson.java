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

    @Data
    public static class ConditionItem {
        private String field;
        /** EQ / NE / GT / LT / LIKE */
        private String op;
        private String paramKey;
    }
}
