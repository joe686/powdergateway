package com.powergateway.model.dto;

import lombok.Data;

/**
 * 字段映射规则条目（存于 convert_template.mapping_rule JSON 数组中）
 */
@Data
public class FieldMappingRule {

    /** 源字段名，为 null 时使用 fixedValue */
    private String srcField;

    /** 目标字段名（必填） */
    private String targetField;

    /** 固定值；srcField 为 null 时生效 */
    private String fixedValue;
}
