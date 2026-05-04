package com.powergateway.model.dto;

import lombok.Data;

/**
 * 字段映射预览请求：传入测试报文，返回应用映射规则后的字段 Map
 */
@Data
public class PreviewRequest {

    /** 测试报文原始字符串（JSON / XML / CSV / FORM_DATA） */
    private String message;

    /** 报文格式，枚举值同 FormatType：JSON、XML、CSV、FORM_DATA */
    private String format;
}
