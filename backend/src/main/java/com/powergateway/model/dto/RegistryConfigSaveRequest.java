package com.powergateway.model.dto;

import lombok.Data;

/**
 * 注册中心配置新增/更新请求 DTO（REG-1）
 *
 * password 传"***"或空字符串表示"不修改（沿用 DB 已加密值）"，
 * 与 M2-1 DbConnectionSaveRequest 的处理方式一致。
 */
@Data
public class RegistryConfigSaveRequest {

    /** 为空表示新增，非空表示更新 */
    private Long id;

    /** 注册中心类型：nacos / eureka（必填） */
    private String type;

    /** 用户起的别名（必填） */
    private String name;

    /** 服务端地址（必填） */
    private String serverAddr;

    /** Nacos 命名空间，可空 */
    private String namespace;

    /** Nacos 分组，可空，默认 DEFAULT_GROUP */
    private String groupName = "DEFAULT_GROUP";

    /** 认证用户名，可空 */
    private String username;

    /** 认证密码明文；服务端加密后存入 DB；传"***"表示不修改 */
    private String password;

    /** 是否启用，默认 1（启用） */
    private Integer enabled = 1;

    /** 是否把 PG 自身注册进去，默认 1（是） */
    private Integer registerSelf = 1;

    /** 自注册服务名，可空则回退到 sys_config.registry.self.service_name */
    private String serviceName;

    /** 注册时携带的额外元数据 JSON 字符串，可空 */
    private String extraMetadata;
}
