package com.powergateway.service.codec;

/**
 * FN-11 · Excel 结构不符合 codec 约定时抛出（缺 sheet / 缺列 / 行数据不合法等）。
 */
public class InvalidExcelStructureException extends RuntimeException {
    public InvalidExcelStructureException(String message) {
        super(message);
    }

    public InvalidExcelStructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
