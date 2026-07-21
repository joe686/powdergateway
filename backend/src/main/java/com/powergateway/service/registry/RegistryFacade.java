package com.powergateway.service.registry;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REG-1 · 注册中心门面
 *
 * 聚合所有已启用 {@link RegistryClient}，业务代码只面向本类。
 * 未配置（{@link RegistryClient#isConfigured()} 返回 false）的 client 自动跳过。
 * choose() 采用 Round Robin 轮询，同一 serviceName 的 cursor 独立。
 */
@Slf4j
@Service
public class RegistryFacade {

    private final List<RegistryClient> clients;
    private final ConcurrentMap<String, ClientHealth> healthMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> rrCursors = new ConcurrentHashMap<>();

    @Autowired
    public RegistryFacade(List<RegistryClient> clients) {
        this.clients = clients == null ? Collections.emptyList() : clients;
    }

    public void registerSelfToAll(ServiceInstance self) {
        for (RegistryClient c : clients) {
            if (!c.isConfigured()) continue;
            try {
                c.register(self);
            } catch (Exception e) {
                log.warn("注册到 {}({}) 失败：{}", c.getType(), c.getName(), e.getMessage());
            }
        }
    }

    public void deregisterSelfFromAll(String serviceName) {
        for (RegistryClient c : clients) {
            if (!c.isConfigured()) continue;
            try {
                c.deregister(serviceName);
            } catch (Exception e) {
                log.warn("从 {}({}) 注销失败：{}", c.getType(), c.getName(), e.getMessage());
            }
        }
    }

    /** 跨所有已配置 client 聚合发现结果。不去重（一个 SVC 在多 registry 就是多实例）。 */
    public List<ServiceInstance> discover(String serviceName) {
        List<ServiceInstance> all = new ArrayList<>();
        for (RegistryClient c : clients) {
            if (!c.isConfigured()) continue;
            try {
                all.addAll(c.discover(serviceName));
            } catch (Exception e) {
                log.warn("在 {}({}) 上发现 {} 失败：{}", c.getType(), c.getName(), serviceName, e.getMessage());
            }
        }
        return all;
    }

    /** Round Robin 选一个实例；无可用实例返回 empty。 */
    public Optional<ServiceInstance> choose(String serviceName) {
        List<ServiceInstance> instances = discover(serviceName);
        if (instances.isEmpty()) return Optional.empty();
        AtomicInteger cursor = rrCursors.computeIfAbsent(serviceName, k -> new AtomicInteger());
        int idx = Math.floorMod(cursor.getAndIncrement(), instances.size());
        return Optional.of(instances.get(idx));
    }

    /** 对所有已配置 client 触发一次心跳，更新健康度记录。由 RegistryHeartbeatScheduler 定期调用（Task 6）。 */
    public void heartbeatAll() {
        for (RegistryClient c : clients) {
            if (!c.isConfigured()) continue;
            final boolean ok = safeHeartbeat(c);
            healthMap.compute(key(c), (k, prev) -> {
                ClientHealth h = prev == null ? new ClientHealth() : prev;
                h.healthy = ok;
                h.consecutiveFails = ok ? 0 : h.consecutiveFails + 1;
                return h;
            });
        }
    }

    private boolean safeHeartbeat(RegistryClient c) {
        try {
            return c.heartbeat();
        } catch (Exception e) {
            log.warn("对 {}({}) 心跳失败：{}", c.getType(), c.getName(), e.getMessage());
            return false;
        }
    }

    /** 是否至少有一个已配置的 client。ServiceUrlResolver 用于判断"注册中心是否启用"。 */
    public boolean hasAnyConfiguredClient() {
        for (RegistryClient c : clients) {
            if (c.isConfigured()) return true;
        }
        return false;
    }

    public List<ClientStatus> statusAll() {
        List<ClientStatus> list = new ArrayList<>(clients.size());
        for (RegistryClient c : clients) {
            ClientStatus s = new ClientStatus();
            s.type = c.getType();
            s.name = c.getName();
            s.configured = c.isConfigured();
            ClientHealth h = healthMap.get(key(c));
            s.healthy = h != null && h.healthy;
            s.consecutiveFails = h == null ? 0 : h.consecutiveFails;
            list.add(s);
        }
        return list;
    }

    private static String key(RegistryClient c) {
        return c.getType() + "::" + c.getName();
    }

    private static class ClientHealth {
        boolean healthy = true;
        int consecutiveFails = 0;
    }

    @Data
    public static class ClientStatus {
        private String type;
        private String name;
        private boolean configured;
        private boolean healthy;
        private int consecutiveFails;
    }
}
