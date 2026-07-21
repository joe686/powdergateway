package com.powergateway.service.registry;

/**
 * REG-1 · 注册中心操作失败（SDK 层面的 register / deregister / discover 抛异常）时统一包装。
 * Facade 捕获后记 warn 日志，业务链路不中断。
 */
public class RegistryOperationException extends RuntimeException {
    public RegistryOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RegistryOperationException(String message) {
        super(message);
    }
}
