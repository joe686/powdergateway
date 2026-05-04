package com.powergateway.utils;

import com.powergateway.exception.BusinessException;

/**
 * M1-1 支持的报文格式枚举
 */
public enum FormatType {
    JSON,
    XML,
    CSV,
    FORM_DATA;

    /**
     * 将字符串解析为枚举值，大小写不敏感，支持连字符（如 "form-data" → FORM_DATA）。
     * 格式不合法时抛 BusinessException(400)，由全局异常处理器转为响应。
     */
    public static FormatType parse(String format) {
        try {
            return valueOf(format.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "不支持的报文格式: " + format);
        }
    }
}
