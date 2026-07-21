package com.powergateway.service.registry;

import java.util.List;

/**
 * REG-1 · 注册中心客户端抽象
 *
 * NacosRegistryClient / EurekaRegistryClient 各自实现此接口，
 * RegistryFacade 通过 List 注入所有已启用的 client 聚合调用。
 */
public interface RegistryClient {

    /** 注册中心类型，nacos / eureka / mock */
    String getType();

    /** 用户起的别名，仅用于日志和状态展示 */
    String getName();

    /** 是否已配置好可用（server_addr 有效、连通性 ok）；未配置的 client 会被 Facade 跳过 */
    boolean isConfigured();

    /** 把本机注册到该注册中心 */
    void register(ServiceInstance self);

    /** 从该注册中心注销指定服务名的本机实例 */
    void deregister(String serviceName);

    /** 按服务名发现实例列表；找不到返回空 List */
    List<ServiceInstance> discover(String serviceName);

    /** 主动心跳探活，成功返回 true；SDK 内部维护心跳时可只做一次 getServerStatus 探活 */
    boolean heartbeat();
}
