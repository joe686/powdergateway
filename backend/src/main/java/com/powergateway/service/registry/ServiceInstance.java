package com.powergateway.service.registry;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REG-1 · 注册中心服务实例信息
 *
 * 由 register/discover 双向传输：注册时描述"我是谁"，发现时描述"找到了谁"。
 */
@Data
public class ServiceInstance {

    /** 服务名，如 POWERGATEWAY / CBS_SYSTEM */
    private String serviceName;

    /** 实例 IP，如 10.0.0.1 */
    private String ip;

    /** 实例端口 */
    private int port;

    /** 协议方案，http / https，默认 http */
    private String scheme = "http";

    /** 权重，v1 忽略，v2 用于加权负载均衡 */
    private int weight = 1;

    /** 额外元数据（version、product、interfaces 列表等） */
    private Map<String, String> metadata = new LinkedHashMap<>();
}
