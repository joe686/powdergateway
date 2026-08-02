package com.powergateway.socket.exception;

import com.powergateway.exception.BusinessException;

/**
 * TCP Socket 读超时(v0.3.0 SOCK-1)。
 *
 * <p>连接建立成功但在 readTimeoutMs 内没收到应答 · 走 GlobalExceptionHandler 转 Result。</p>
 */
public class SocketTimeoutException extends BusinessException {

    public SocketTimeoutException(String message) {
        super(504, message);
    }

    public SocketTimeoutException(String message, Throwable cause) {
        super(504, message, cause);
    }
}
