package com.powergateway.service.registry;

/**
 * REG-1 · 未启用任何注册中心，但业务代码请求解析 service:// 协议 URL 时抛出。
 */
public class RegistryNotEnabledException extends RuntimeException {
    public RegistryNotEnabledException(String message) {
        super(message);
    }
}
