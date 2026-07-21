package com.powergateway.service.registry;

import com.powergateway.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * REG-1 · PowerGateway 自注册 Runner
 *
 * <p>时机：Spring 应用完全就绪后（{@link ApplicationReadyEvent}） → 遍历所有已启用的注册中心 client
 * 注册自身实例；上下文关闭时反向注销。
 *
 * <p>服务名 / IP 覆盖 / 端口从 sys_config + server.port 读取，运行时可通过 SystemConfig 页面改。
 * 提供 {@link #reregisterSelf()} 供 Controller「重新注册」按钮兜底调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelfRegistrationRunner {

    private final RegistryFacade facade;
    private final SysConfigService sysConfigService;

    @Value("${server.port:8080}")
    private int port;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        registerIfConfigured("启动自注册");
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        if (!facade.hasAnyConfiguredClient()) return;
        String svcName = readServiceName();
        try {
            facade.deregisterSelfFromAll(svcName);
            log.info("REG-1: 上下文关闭，已从所有注册中心注销 svc={}", svcName);
        } catch (Exception e) {
            log.warn("REG-1: 注销自身失败：{}", e.getMessage());
        }
    }

    /** 供 Controller「重新注册」按钮调用；返回 true=已触发注册，false=无已启用注册中心跳过。 */
    public boolean reregisterSelf() {
        return registerIfConfigured("手动重新注册");
    }

    private boolean registerIfConfigured(String reason) {
        if (!facade.hasAnyConfiguredClient()) {
            log.info("REG-1: {}跳过，未启用任何注册中心", reason);
            return false;
        }
        ServiceInstance self = buildSelf();
        try {
            facade.registerSelfToAll(self);
            log.info("REG-1: {}完成，svc={} ip={} port={}", reason, self.getServiceName(), self.getIp(), self.getPort());
            return true;
        } catch (Exception e) {
            log.warn("REG-1: {}失败：{}", reason, e.getMessage());
            return false;
        }
    }

    public ServiceInstance buildSelf() {
        String svcName = readServiceName();
        String ipOverride = sysConfigService.getString("registry.self.ip.override", "");
        String ip = notEmpty(ipOverride) ? ipOverride : detectLocalIp();
        ServiceInstance si = new ServiceInstance();
        si.setServiceName(svcName);
        si.setIp(ip);
        si.setPort(port);
        return si;
    }

    private String readServiceName() {
        String svcName = sysConfigService.getString("registry.self.service_name", "POWERGATEWAY");
        return notEmpty(svcName) ? svcName : "POWERGATEWAY";
    }

    private String detectLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
