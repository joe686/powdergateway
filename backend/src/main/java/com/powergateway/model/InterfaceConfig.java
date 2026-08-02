package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口配置表实体类，对应 interface_config
 * config_json 以 JSON 字符串存储完整接口配置，业务层用 Jackson 反序列化
 */
@Data
@TableName("interface_config")
public class InterfaceConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 发布后的访问路径 */
    private String path;

    /** 接口类型：SELECT / INSERT / UPDATE / DELETE / SOCKET(v0.3.0 SOCK-1) */
    private String type;

    /** v0.3.1 CR-007 · PG 内部功能号(建议 PG- 前缀 · 与渠道 functionId 区分)· 可空 · 唯一 */
    private String functionId;

    /** 关联数据库连接 db_connection.id */
    private Long dbConnectionId;

    /** 完整接口配置 JSON 字符串（表、字段、条件、加工规则等） */
    private String configJson;

    /** 关联分库分表配置 shard_config.id */
    private Long shardConfigId;

    /** 是否允许批量删除：0=否，1=是 */
    private Integer allowBatchDelete;

    /** 状态：draft / published / disabled */
    private String status;

    /** 是否记录 SQL 日志：1=是，0=否 */
    private Integer logEnabled;

    private Integer cacheEnabled;
    private Integer cacheTtlSeconds;
    private String  cacheKeyTemplate;

    /** FN-06 用户默认响应格式，默认 "JSON" */
    private String responseFormat;
    /** FN-06 自定义响应头 JSON 字符串 */
    private String responseHeaders;

    @TableLogic
    private Integer deleted;

    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
