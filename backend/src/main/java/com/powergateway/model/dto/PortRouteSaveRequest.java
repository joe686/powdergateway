package com.powergateway.model.dto;

import lombok.Data;

/**
 * 端口路由新增/更新请求 DTO（M1-7）
 */
@Data
public class PortRouteSaveRequest {

    /** 为空时表示新增，非空时表示更新 */
    private Long id;

    /** 渠道编码（必填） */
    private String channelCode;

    /** 目标端口完整 URL（必填） */
    private String portAddress;

    /** HTTP 方法：GET/POST/PUT/DELETE，默认 POST */
    private String portMethod = "POST";

    /** 连接超时（ms），默认 3000 */
    private Integer timeout = 3000;

    /** 失败重试次数，默认 3 */
    private Integer retryCount = 3;

    /** 请求方向转换模板 ID（A→B），null 则原样转发 */
    private Long requestTemplateId;

    /** 应答方向转换模板 ID（B→A），null 则透传 */
    private Long responseTemplateId;

    /** 报文头配置（可选），序列化后存 port_route.header_config，覆盖渠道默认值 */
    private HeaderConfig headerConfig;

    /** 功能号（UX-D），可空 */
    private String functionCode;

    /** 功能号中文名（UX-D），可空 */
    private String functionName;
}
