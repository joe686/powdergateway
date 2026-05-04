package com.powergateway.model.dto;

import lombok.Data;

/**
 * 渠道配置保存请求 DTO（M1-4）
 * id 为空时新增，id 有值时更新。
 */
@Data
public class ChannelSaveRequest {

    /** 渠道 id（更新时必传，新增时为 null） */
    private Long id;

    /** 渠道编码，全局唯一 */
    private String channelCode;

    /** 渠道名称 */
    private String channelName;

    /** 报文中用于识别渠道的字段名 */
    private String identifyField;

    /** 关联的转换模板 id */
    private Long templateId;

    /** 报文头配置（可选），序列化后存 channel_config.header_config */
    private HeaderConfig headerConfig;
}
