package com.powergateway.socket.exception;

import com.powergateway.exception.BusinessException;

/**
 * TCP Socket 连接失败(v0.3.0 SOCK-1)。
 *
 * <p>连接不上目标 host:port · 通常配置错或目标未启动 · 走 GlobalExceptionHandler 转 Result。</p>
 */
public class SocketConnectException extends BusinessException {

    public SocketConnectException(String message) {
        super(502, message);
    }

    public SocketConnectException(String message, Throwable cause) {
        super(502, message, cause);
    }
}
