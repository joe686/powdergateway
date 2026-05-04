package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道配置表实体类，对应 channel_config
 */
@Data
@TableName("channel_config")
public class ChannelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码，唯一 */
    private String channelCode;

    private String channelName;

    /** 报文中用于识别渠道的字段名 */
    private String identifyField;

    /** 关联 convert_template.id */
    private Long templateId;

    /** 渠道级别报文头配置，JSON 字符串，CHG-002 */
    @TableField("header_config")
    private String headerConfigRaw;

    /** 反序列化后的报文头配置对象（不映射数据库列） */
    @TableField(exist = false)
    private com.powergateway.model.dto.HeaderConfig headerConfig;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
