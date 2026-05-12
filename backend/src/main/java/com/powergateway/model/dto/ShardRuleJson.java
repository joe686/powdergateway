package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ShardRuleJson {

    private String routingField;
    private FieldLookupConfig fieldLookup;
    private AlgorithmConfig algorithm;
    /** 取模路由分段列表（MODULO 使用） */
    private List<DbSegment> dbSegments;
    /** 范围路由列表（RANGE 使用） */
    private List<ShardItem> shards;

    @Data
    public static class FieldLookupConfig {
        private Long dbConnectionId;
        private String table;
        private String conditionColumn;
        private String conditionParamKey;
        private String targetColumn;
    }

    @Data
    public static class AlgorithmConfig {
        /** MODULO 或 RANGE */
        private String type;
        /** MODULO 时必填 */
        private Integer divisor;
    }

    @Data
    public static class DbSegment {
        private Long dbConnectionId;
        private String tablePrefix;
        private int indexStart;
        private int indexEnd;
        /** 0=不补零；2=两位补零（orders_03）；以此类推 */
        private Integer indexPadding;
    }

    @Data
    public static class ShardItem {
        private Long rangeStart;
        private Long rangeEnd;
        private Long dbConnectionId;
        private String tableName;
    }
}
