package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 注册中心配置实体，对应 registry_config 表（REG-1）
 *
 * 一条记录代表一个注册中心（如"内部 Nacos"、"部门 Eureka"），支持同时启用多个。
 * password 字段以 AES 加密存储，与 db_connection.password 处理方式一致。
 */
@Data
@TableName("registry_config")
public class RegistryConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 注册中心类型：nacos / eureka */
    private String type;

    /** 用户起的别名，仅用于展示区分（如"内部 Nacos"） */
    private String name;

    /** 服务端地址：nacos 用 host:port（逗号分隔多台）；eureka 用 http://host:port/eureka/ */
    private String serverAddr;

    /** Nacos 命名空间，可空 */
    private String namespace;

    /** Nacos 分组，默认 DEFAULT_GROUP */
    private String groupName;

    /** 认证用户名，可空 */
    private String username;

    /** 认证密码，AES 加密存储；对外接口一律用"***"占位表示"未修改" */
    private String password;

    /** 是否启用（1 启用 / 0 停用） */
    private Integer enabled;

    /** 是否把 PG 自身注册进去（1 是 / 0 否） */
    private Integer registerSelf;

    /** 自注册服务名，覆盖 sys_config.registry.self.service_name */
    private String serviceName;

    /** 注册时携带的额外元数据 JSON 字符串 */
    private String extraMetadata;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
