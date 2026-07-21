package com.powergateway.service.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.RegistryConfigMapper;
import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.nacos.NacosRegistryClient;
import com.powergateway.utils.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * REG-1 Task 7 · 从 registry_config 表装配 RegistryClient 到 Facade
 *
 * <p>启动时（{@link ApplicationReadyEvent}）遍历 enabled=1 && deleted=0 的记录，
 * 按 type 分发到 {@link NacosRegistryClient} / 未来的 EurekaRegistryClient 构造，
 * 单条 client 构造失败仅 warn 不中断（其他 client 仍继续装配）。
 *
 * <p>Controller 增删改 registry_config 后调 {@link #reload()} 触发全量重装。
 *
 * <p>@Order(1) 保证在 {@link SelfRegistrationRunner}（@Order 默认 = LOWEST）之前完成。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RegistryClientRegistrar {

    private final RegistryConfigMapper registryConfigMapper;
    private final RegistryFacade facade;
    @Lazy
    @Autowired
    private AesUtil aesUtil;

    @Value("${powergateway.aes.key:PowerGateway128K}")
    private String fallbackAesKey;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    /** 供 Controller 触发的重新装配：先清空 Facade 现有 client，再从 DB 重读装配。 */
    public synchronized void reload() {
        facade.clearAllClients();
        LambdaQueryWrapper<RegistryConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(RegistryConfig::getEnabled, 1);
        qw.eq(RegistryConfig::getDeleted, 0);
        List<RegistryConfig> configs = registryConfigMapper.selectList(qw);
        int ok = 0, fail = 0;
        for (RegistryConfig cfg : configs) {
            try {
                RegistryClient client = buildClient(cfg);
                if (client != null) {
                    facade.addClient(client);
                    ok++;
                    log.info("REG-1: 装配 client 成功 type={} name={}", cfg.getType(), cfg.getName());
                }
            } catch (Exception e) {
                fail++;
                log.warn("REG-1: 装配 client 失败 type={} name={}: {}", cfg.getType(), cfg.getName(), e.getMessage());
            }
        }
        log.info("REG-1: Registry client reload 完成，成功 {} 失败 {}", ok, fail);
    }

    private RegistryClient buildClient(RegistryConfig cfg) {
        String type = cfg.getType() == null ? "" : cfg.getType().trim().toLowerCase();
        switch (type) {
            case "nacos":
                return new NacosRegistryClient(cfg, decryptPassword(cfg.getPassword()));
            case "eureka":
                // Eureka 自动装配 v1 未实现（EurekaClient 构造依赖 ApplicationInfoManager，需大量样板）
                // 客户如需 Eureka 通过 discover 消费老系统，可在后续版本或独立 factory 里手工装配
                log.warn("REG-1: type=eureka 的注册中心 v1 暂未自动装配（{}），跳过", cfg.getName());
                return null;
            default:
                log.warn("REG-1: 未知 registry type={} name={}，跳过", cfg.getType(), cfg.getName());
                return null;
        }
    }

    private String decryptPassword(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return "";
        try {
            return aesUtil.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("REG-1: 注册中心密码解密失败，尝试原文回退：{}", e.getMessage());
            return encrypted;
        }
    }
}
