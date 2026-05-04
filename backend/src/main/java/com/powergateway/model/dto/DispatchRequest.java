package com.powergateway.model.dto;

import lombok.Data;

/**
 * 端口分发请求 DTO（M1-7）
 * <p>
 * 通过报文中的渠道标识字段自动路由到对应的端口路由配置。
 */
@Data
public class DispatchRequest {

    /** 源报文字符串（必填） */
    private String message;

    /** 源报文格式：JSON/XML/CSV/FORM_DATA（必填） */
    private String srcFormat;
}
