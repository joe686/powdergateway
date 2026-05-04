package com.powergateway.model.dto;

import lombok.Data;
import java.util.Map;

/**
 * 报文头配置 DTO（CHG-002）
 * 用于 channel_config.header_config 和 port_route.header_config 两列的 JSON 映射。
 */
@Data
public class HeaderConfig {

    /** 出向请求的 Content-Type，e.g. "application/json"；null=不设置 */
    private String contentType;

    /** 出向请求的字符集，e.g. "GBK"、"ISO-8859-1"；null/空=不转码（默认 UTF-8） */
    private String charset;

    /** 出向请求自定义 Header（A→B），key=header 名，value=header 值 */
    private Map<String, String> requestHeaders;

    /** 返回给 A 的响应头，key=header 名，value=header 值 */
    private Map<String, String> responseHeaders;
}
