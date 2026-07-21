package com.powergateway.service.codec;

/**
 * FN-11 · Excel schemaVersion 与 codec 支持版本不匹配时抛出。
 * 提示用户"请从新版本重新导出"。
 */
public class IncompatibleSchemaException extends RuntimeException {
    public IncompatibleSchemaException(String message) {
        super(message);
    }
}
