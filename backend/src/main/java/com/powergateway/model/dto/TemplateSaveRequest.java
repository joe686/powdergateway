package com.powergateway.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存/更新转换模板的请求体
 */
@Data
public class TemplateSaveRequest {

    /** 为 null 表示新增，有值表示基于该版本更新 */
    private Long id;

    private String name;

    /** 源格式：JSON / XML / CSV / FORM_DATA */
    private String srcFormat;

    /** 目标格式：JSON / XML / CSV / FORM_DATA */
    private String targetFormat;

    /** 字段映射规则列表 */
    private List<FieldMappingRule> mappingRules;

    /** 功能号（UX-D），可空 */
    private String functionCode;
}
