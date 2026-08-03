package com.powergateway.testkit.eureka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * v0.3.11 CHG-054 · pg-testkit 多应用自注册生命周期管理。
 *
 * <p>启动时({@link PostConstruct})按配置批量注册 · 关闭时({@link PreDestroy})全量 deregister。</p>
 *
 * <p>默认关闭({@code pg-testkit.eureka.enabled=false}) · 用户显式开启 + 提供 Eureka Server 地址后
 * 生效。</p>
 */
@Slf4j
@Component
public class MultiAppSelfRegister {

    @Autowired private EurekaAppConfig config;

    private EurekaClient client;

    /** 已成功注册的实例记录 · 供 API 查询 + deregister 遍历 */
    private final List<EurekaAppConfig.AppInstance> registered = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.info("[pg-testkit] Eureka 自注册未启用(pg-testkit.eureka.enabled=false) · 跳过");
            return;
        }
        if (config.getApplications() == null || config.getApplications().isEmpty()) {
            log.warn("[pg-testkit] Eureka 自注册启用但 applications 空 · 跳过");
            return;
        }
        client = new EurekaClient(config.getServerUrl());
        log.info("[pg-testkit] Eureka 自注册启动 · server={} · applications 数={}", config.getServerUrl(), config.getApplications().size());
        registerAll();
    }

    @PreDestroy
    public void shutdown() {
        if (registered.isEmpty()) return;
        log.info("[pg-testkit] Eureka 关闭 · deregister {} 个实例", registered.size());
        for (EurekaAppConfig.AppInstance app : registered) {
            client.deregister(app);
        }
        registered.clear();
    }

    /** 手工触发全量注册(供 /test/eureka/register-all 调用) · 返成功数 */
    public int registerAll() {
        if (client == null && config.isEnabled()) {
            client = new EurekaClient(config.getServerUrl());
        }
        if (client == null) return 0;
        int ok = 0;
        for (EurekaAppConfig.AppInstance app : config.getApplications()) {
            if (client.register(app)) {
                if (!registered.contains(app)) registered.add(app);
                ok++;
            }
        }
        return ok;
    }

    /** 手工触发全量注销(供 /test/eureka/deregister-all 调用) · 返成功数 */
    public int deregisterAll() {
        if (client == null) return 0;
        int ok = 0;
        for (EurekaAppConfig.AppInstance app : registered) {
            if (client.deregister(app)) ok++;
        }
        registered.clear();
        return ok;
    }

    /** 手工注册单个应用(按名称匹配 config.applications) · 未找到返 false */
    public boolean registerOne(String name) {
        if (client == null && config.isEnabled()) {
            client = new EurekaClient(config.getServerUrl());
        }
        if (client == null) return false;
        return config.getApplications().stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst()
                .map(app -> {
                    boolean ok = client.register(app);
                    if (ok && !registered.contains(app)) registered.add(app);
                    return ok;
                })
                .orElse(false);
    }

    /** 已注册应用列表快照(供 /test/eureka/list 返回) */
    public List<Map<String, Object>> listRegistered() {
        return registered.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getName());
            m.put("ip", a.getIp());
            m.put("port", a.getPort());
            m.put("scheme", a.getScheme());
            return m;
        }).collect(Collectors.toList());
    }

    public EurekaAppConfig getConfig() { return config; }
}
