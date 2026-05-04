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

    /** 接口类型：SELECT / INSERT / UPDATE / DELETE */
    private String type;

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

    @TableLogic
    private Integer deleted;

    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
