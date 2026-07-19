package com.powergateway.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段公式对外传输对象（UX-C FN-03）。
 * formulaJson 以字符串形式回传，前端自行解析成 FormulaJson。
 */
@Data
public class FieldFormulaDto {
    private Long id;
    private String name;
    private String scene;
    private Long dbConnectionId;
    private String formulaJson;
    private String remark;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
