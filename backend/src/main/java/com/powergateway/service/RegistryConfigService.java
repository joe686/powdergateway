package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.RegistryConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.RegistryConfig;
import com.powergateway.model.dto.RegistryConfigSaveRequest;
import com.powergateway.service.registry.RegistryClientRegistrar;
import com.powergateway.service.registry.RegistryFacade;
import com.powergateway.service.registry.SelfRegistrationRunner;
import com.powergateway.service.registry.ServiceInstance;
import com.powergateway.utils.AesUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REG-1 Task 7 · 注册中心配置 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryConfigService {

    public static final String PASSWORD_MASK = "***";

    private final RegistryConfigMapper registryConfigMapper;
    private final AesUtil aesUtil;
    private final RegistryClientRegistrar registrar;
    private final RegistryFacade facade;
    private final SelfRegistrationRunner selfRegistrationRunner;

    /** 列表；password 掩码返回（前端展示用）。 */
    public List<RegistryConfig> list() {
        LambdaQueryWrapper<RegistryConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(RegistryConfig::getDeleted, 0);
        qw.orderByDesc(RegistryConfig::getUpdateTime);
        List<RegistryConfig> all = registryConfigMapper.selectList(qw);
        for (RegistryConfig cfg : all) {
            if (cfg.getPassword() != null && !cfg.getPassword().isEmpty()) {
                cfg.setPassword(PASSWORD_MASK);
            }
        }
        return all;
    }

    public Long save(RegistryConfigSaveRequest req) {
        validate(req);
        RegistryConfig cfg;
        boolean isNew = req.getId() == null;
        if (isNew) {
            cfg = new RegistryConfig();
            cfg.setDeleted(0);
            cfg.setCreateTime(LocalDateTime.now());
        } else {
            cfg = registryConfigMapper.selectById(req.getId());
            if (cfg == null || Integer.valueOf(1).equals(cfg.getDeleted())) {
                throw new BusinessException(404, "注册中心配置不存在: " + req.getId());
            }
        }

        cfg.setType(req.getType().toLowerCase());
        cfg.setName(req.getName());
        cfg.setServerAddr(req.getServerAddr());
        cfg.setNamespace(req.getNamespace());
        cfg.setGroupName(req.getGroupName() == null || req.getGroupName().isEmpty() ? "DEFAULT_GROUP" : req.getGroupName());
        cfg.setUsername(req.getUsername());
        cfg.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        cfg.setRegisterSelf(req.getRegisterSelf() == null ? 1 : req.getRegisterSelf());
        cfg.setServiceName(req.getServiceName());
        cfg.setExtraMetadata(req.getExtraMetadata());
        cfg.setUpdateTime(LocalDateTime.now());

        // 密码处理：null / 空 / "***" 均视为"不修改"（新增时 null/空则存空串）
        if (req.getPassword() != null && !req.getPassword().isEmpty() && !PASSWORD_MASK.equals(req.getPassword())) {
            cfg.setPassword(aesUtil.encrypt(req.getPassword()));
        } else if (isNew) {
            cfg.setPassword("");
        }

        if (isNew) {
            registryConfigMapper.insert(cfg);
        } else {
            registryConfigMapper.updateById(cfg);
        }

        registrar.reload();
        return cfg.getId();
    }

    public void delete(Long id) {
        RegistryConfig cfg = registryConfigMapper.selectById(id);
        if (cfg == null) throw new BusinessException(404, "注册中心配置不存在: " + id);
        // MyBatis-Plus @TableLogic 触发软删除：走 deleteById 而非 updateById（后者会跳过逻辑删除字段）
        registryConfigMapper.deleteById(id);
        registrar.reload();
    }

    /** 测试连通：调 registrar 重装 + 找对应 client 触发 heartbeat。 */
    public TestConnectionResult testConnection(Long id) {
        RegistryConfig cfg = registryConfigMapper.selectById(id);
        if (cfg == null) return TestConnectionResult.fail("配置不存在");
        registrar.reload();
        // 简化：从 facade.statusAll() 找对应 name+type 的健康度
        for (RegistryFacade.ClientStatus s : facade.statusAll()) {
            if (s.getName().equals(cfg.getName()) && s.getType().equalsIgnoreCase(cfg.getType())) {
                if (!s.isConfigured()) return TestConnectionResult.fail("client 未配置或初始化失败");
                // 主动触发一次心跳
                facade.heartbeatAll();
                for (RegistryFacade.ClientStatus s2 : facade.statusAll()) {
                    if (s2.getName().equals(cfg.getName()) && s2.getType().equalsIgnoreCase(cfg.getType())) {
                        return s2.isHealthy() ? TestConnectionResult.ok("连接正常") : TestConnectionResult.fail("心跳未响应");
                    }
                }
            }
        }
        return TestConnectionResult.fail("client 未装配（可能 SDK 依赖缺失，需 -Pregistry-nacos 打包）");
    }

    /** 跨所有 client 发现指定服务名的实例列表（预览用）。 */
    public List<ServicePreview> discoverPreview(String serviceName) {
        List<ServicePreview> list = new ArrayList<>();
        if (serviceName == null || serviceName.isEmpty()) return list;
        for (ServiceInstance i : facade.discover(serviceName)) {
            ServicePreview p = new ServicePreview();
            p.setServiceName(i.getServiceName());
            p.setIp(i.getIp());
            p.setPort(i.getPort());
            p.setScheme(i.getScheme());
            p.setMetadata(i.getMetadata());
            list.add(p);
        }
        return list;
    }

    /** 供 Controller「重新注册」按钮：触发 SelfRegistrationRunner 重新注册。 */
    public boolean reregisterSelf() {
        return selfRegistrationRunner.reregisterSelf();
    }

    private void validate(RegistryConfigSaveRequest req) {
        if (req.getType() == null || req.getType().trim().isEmpty()) {
            throw new BusinessException(400, "type 不能为空（nacos / eureka）");
        }
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException(400, "name 不能为空");
        }
        if (req.getServerAddr() == null || req.getServerAddr().trim().isEmpty()) {
            throw new BusinessException(400, "serverAddr 不能为空");
        }
    }

    @Data
    public static class TestConnectionResult {
        private boolean ok;
        private String message;
        static TestConnectionResult ok(String msg) { TestConnectionResult r = new TestConnectionResult(); r.ok = true; r.message = msg; return r; }
        static TestConnectionResult fail(String msg) { TestConnectionResult r = new TestConnectionResult(); r.ok = false; r.message = msg; return r; }
    }

    @Data
    public static class ServicePreview {
        private String serviceName;
        private String ip;
        private int port;
        private String scheme;
        private Map<String, String> metadata;
    }
}
