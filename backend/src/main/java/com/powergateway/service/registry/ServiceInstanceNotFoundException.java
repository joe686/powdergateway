package com.powergateway.service.registry;

/**
 * REG-1 · 通过注册中心找不到指定服务名的可用实例时抛出（且本地缓存也已过期）。
 */
public class ServiceInstanceNotFoundException extends RuntimeException {
    public ServiceInstanceNotFoundException(String message) {
        super(message);
    }
}
