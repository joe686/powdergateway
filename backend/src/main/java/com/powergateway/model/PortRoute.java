package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 端口分发路由表实体类，对应 port_route（M1-7）
 */
@Data
@TableName("port_route")
public class PortRoute {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码，关联 channel_config.channel_code */
    private String channelCode;

    /** 目标端口完整 URL（含协议和路径） */
    private String portAddress;

    /** HTTP 方法：GET/POST/PUT/DELETE，默认 POST */
    private String portMethod;

    /** 连接超时（ms），默认 3000 */
    private Integer timeout;

    /** 失败重试次数，默认 3 */
    private Integer retryCount;

    /** 请求方向转换模板ID（A→B），null 则原样转发 */
    private Long requestTemplateId;

    /** 应答方向转换模板ID（B→A），null 则透传 */
    private Long responseTemplateId;

    /** 端口路由级别报文头配置，JSON 字符串，CHG-002 */
    @TableField("header_config")
    private String headerConfigRaw;

    /** 功能号（UX-D），软唯一（代码层校验），可空 */
    private String functionCode;

    /** 功能号中文名（UX-D），可空 */
    private String functionName;

    /** 反序列化后的报文头配置对象（不映射数据库列） */
    @TableField(exist = false)
    private com.powergateway.model.dto.HeaderConfig headerConfig;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
