package com.powergateway.service.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REG-1 · 测试专用内存注册中心
 *
 * 用于 Facade / Resolver / Dispatch 集成测试，替代真实 Nacos/Eureka。
 * 记录调用次数便于测试断言；支持通过 setConfigured(false) 模拟"未配置"状态。
 */
public class MockRegistryClient implements RegistryClient {

    private final String type;
    private final String name;
    private boolean configured = true;
    private boolean heartbeatOk = true;
    private final Map<String, List<ServiceInstance>> registered = new HashMap<>();

    public final AtomicInteger registerCalls = new AtomicInteger();
    public final AtomicInteger deregisterCalls = new AtomicInteger();
    public final AtomicInteger discoverCalls = new AtomicInteger();
    public final AtomicInteger heartbeatCalls = new AtomicInteger();

    public MockRegistryClient(String type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    public void setConfigured(boolean configured) {
        this.configured = configured;
    }

    public void setHeartbeatOk(boolean ok) {
        this.heartbeatOk = ok;
    }

    @Override
    public void register(ServiceInstance self) {
        registerCalls.incrementAndGet();
        registered.computeIfAbsent(self.getServiceName(), k -> new ArrayList<>()).add(self);
    }

    @Override
    public void deregister(String serviceName) {
        deregisterCalls.incrementAndGet();
        registered.remove(serviceName);
    }

    @Override
    public List<ServiceInstance> discover(String serviceName) {
        discoverCalls.incrementAndGet();
        return registered.getOrDefault(serviceName, Collections.emptyList());
    }

    @Override
    public boolean heartbeat() {
        heartbeatCalls.incrementAndGet();
        return heartbeatOk;
    }

    /** 测试便利：预先塞入一批实例 */
    public void preload(String serviceName, ServiceInstance... instances) {
        List<ServiceInstance> list = registered.computeIfAbsent(serviceName, k -> new ArrayList<>());
        Collections.addAll(list, instances);
    }
}
