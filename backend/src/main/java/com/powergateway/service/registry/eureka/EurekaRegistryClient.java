package com.powergateway.service.registry.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.RegistryClient;
import com.powergateway.service.registry.ServiceInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * REG-1 · Eureka 注册中心客户端（"老系统兼容"方向）
 *
 * <p>聚焦 discover 场景（消费方）：外部老系统通常注册在 Eureka，我方读取实例列表用于 dispatch。
 * register/deregister 走本地缓存 + log 占位 —— Netflix Eureka 的自注册流程依赖
 * ApplicationInfoManager + DiscoveryClient 组合手动装配，非常繁琐；实际部署中"我方注册到 Eureka"
 * 是稀有场景，因此暂不做深度实现，Task 6 自注册流程若涉及可扩展。
 */
@Slf4j
public class EurekaRegistryClient implements RegistryClient {

    private final RegistryConfig config;
    private final EurekaClient eurekaClient;
    private final ConcurrentMap<String, ServiceInstance> lastRegistered = new ConcurrentHashMap<>();

    /** 生产用构造：需上层提供 EurekaClient（由 factory 层构造，Task 5/6 集成）。 */
    public EurekaRegistryClient(RegistryConfig config, EurekaClient eurekaClient) {
        this.config = config;
        this.eurekaClient = eurekaClient;
    }

    @Override
    public String getType() {
        return "eureka";
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public boolean isConfigured() {
        return notEmpty(config.getServerAddr()) && eurekaClient != null;
    }

    @Override
    public void register(ServiceInstance self) {
        // Netflix Eureka 的注册通过 ApplicationInfoManager 触发，非当前接口范式；
        // 本类目前只缓存实例信息用于 deregister 匹配，真实注册留待 Task 6 深度集成。
        lastRegistered.put(self.getServiceName(), self);
        log.info("Eureka register 占位（未调 SDK）: svc={} ip={}:{}",
                self.getServiceName(), self.getIp(), self.getPort());
    }

    @Override
    public void deregister(String serviceName) {
        ServiceInstance last = lastRegistered.remove(serviceName);
        if (last == null) return;
        log.info("Eureka deregister 占位（未调 SDK）: svc={}", serviceName);
    }

    @Override
    public List<ServiceInstance> discover(String serviceName) {
        try {
            Application app = eurekaClient.getApplication(serviceName);
            if (app == null) return new ArrayList<>();
            List<InstanceInfo> instances = app.getInstances();
            if (instances == null || instances.isEmpty()) return new ArrayList<>();
            List<ServiceInstance> result = new ArrayList<>(instances.size());
            for (InstanceInfo info : instances) {
                if (info.getStatus() != InstanceInfo.InstanceStatus.UP) continue;
                ServiceInstance si = new ServiceInstance();
                si.setServiceName(serviceName);
                si.setIp(info.getIPAddr());
                si.setPort(info.getPort());
                si.setScheme(info.isPortEnabled(InstanceInfo.PortType.SECURE) ? "https" : "http");
                if (info.getMetadata() != null) {
                    si.setMetadata(new LinkedHashMap<>(info.getMetadata()));
                }
                result.add(si);
            }
            return result;
        } catch (Exception e) {
            log.warn("Eureka discover 失败 svc={}: {}", serviceName, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean heartbeat() {
        try {
            Applications apps = eurekaClient.getApplications();
            return apps != null;
        } catch (Exception e) {
            log.warn("Eureka heartbeat 失败 name={}: {}", config.getName(), e.getMessage());
            return false;
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
