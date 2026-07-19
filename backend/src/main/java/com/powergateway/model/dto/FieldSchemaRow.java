package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FN-07 字段清单 Excel 单行数据模型（请求字段 / 响应字段均用此结构）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldSchemaRow {
    private Integer index;      // 序号，从 1 开始
    private String fieldName;   // 英文字段名
    private String comment;     // 中文含义（表结构注释）
    private String dataType;    // VARCHAR / INT / DATETIME
    private String length;      // 长度
    private String required;    // Y/N
    private String source;      // REQUEST / CONST=xx / CALC=xx / -
}
