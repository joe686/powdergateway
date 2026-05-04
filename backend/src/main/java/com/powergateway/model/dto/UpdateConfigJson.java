package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

/**
 * UPDATE 接口的 config_json 结构（M2-5）。
 *
 * 升级点：conditions 当前为共享列表（方案B），通过 tableName 字段区分目标表。
 * 未来可扩展为每表独立条件组，JSON Schema 无需变更。
 */
@Data
public class UpdateConfigJson {

    /** 修改字段配置，最多3张表 */
    private List<TableUpdateConfig> tables;

    /**
     * 修改条件（共享条件集）。
     * 每条含 tableName，映射到对应表的 WHERE 子句。
     * 至少一张表的条件字段必须是主键或唯一索引，否则保存报错。
     */
    private List<ConditionConfig> conditions;

    @Data
    public static class TableUpdateConfig {
        private String tableName;
        /** 复用 InsertConfigJson.FieldInsertConfig 结构（REQUEST/CONST/CALC） */
        private List<InsertConfigJson.FieldInsertConfig> fields;
    }

    @Data
    public static class ConditionConfig {
        /** 目标表名 */
        private String tableName;
        /** WHERE 字段名 */
        private String field;
        /** 操作符：EQ / NE / GT / LT / LIKE */
        private String op;
        /** 从请求参数中取值的 key */
        private String paramKey;
    }
}
