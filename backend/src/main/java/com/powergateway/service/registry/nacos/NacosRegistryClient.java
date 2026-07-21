package com.powergateway.service.registry.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.RegistryClient;
import com.powergateway.service.registry.RegistryOperationException;
import com.powergateway.service.registry.ServiceInstance;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * REG-1 · Nacos 注册中心客户端
 *
 * 每个实例对应一条 registry_config 记录，内部持有一个 {@link NamingService}。
 * 由 factory 层（Task 5/6）按 DB 记录批量创建；本类是 POJO，不作为 Spring bean。
 */
@Slf4j
public class NacosRegistryClient implements RegistryClient {

    private final RegistryConfig config;
    private final NamingService namingService;
    private final ConcurrentMap<String, ServiceInstance> lastRegistered = new ConcurrentHashMap<>();

    /** 生产用构造：内部创建 NamingService。password 需由调用方 AES 解密后传入。 */
    public NacosRegistryClient(RegistryConfig config, String decryptedPassword) throws RegistryOperationException {
        this.config = config;
        this.namingService = createNamingService(config, decryptedPassword);
    }

    /** 测试用构造：注入 mock NamingService，跳过真实 SDK 初始化。 */
    NacosRegistryClient(RegistryConfig config, NamingService namingService, String decryptedPassword) {
        this.config = config;
        this.namingService = namingService;
    }

    private static NamingService createNamingService(RegistryConfig config, String decryptedPassword) {
        Properties props = new Properties();
        props.setProperty(PropertyKeyConst.SERVER_ADDR, safe(config.getServerAddr()));
        if (notEmpty(config.getNamespace())) props.setProperty(PropertyKeyConst.NAMESPACE, config.getNamespace());
        if (notEmpty(config.getUsername())) props.setProperty(PropertyKeyConst.USERNAME, config.getUsername());
        if (notEmpty(decryptedPassword)) props.setProperty(PropertyKeyConst.PASSWORD, decryptedPassword);
        try {
            return NacosFactory.createNamingService(props);
        } catch (NacosException e) {
            throw new RegistryOperationException("Nacos NamingService 创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getType() {
        return "nacos";
    }

    @Override
    public String getName() {
        return config.getName();
    }

    @Override
    public boolean isConfigured() {
        return notEmpty(config.getServerAddr()) && namingService != null;
    }

    @Override
    public void register(ServiceInstance self) {
        Instance inst = new Instance();
        inst.setIp(self.getIp());
        inst.setPort(self.getPort());
        inst.setWeight(self.getWeight() <= 0 ? 1.0 : self.getWeight());
        if (self.getMetadata() != null) {
            inst.setMetadata(new LinkedHashMap<>(self.getMetadata()));
        }
        try {
            namingService.registerInstance(self.getServiceName(), groupName(), inst);
            lastRegistered.put(self.getServiceName(), self);
        } catch (NacosException e) {
            throw new RegistryOperationException("Nacos register 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deregister(String serviceName) {
        ServiceInstance last = lastRegistered.remove(serviceName);
        if (last == null) {
            return; // 未曾注册，静默跳过
        }
        try {
            namingService.deregisterInstance(serviceName, groupName(), last.getIp(), last.getPort());
        } catch (NacosException e) {
            throw new RegistryOperationException("Nacos deregister 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ServiceInstance> discover(String serviceName) {
        try {
            List<Instance> instances = namingService.selectInstances(serviceName, groupName(), true);
            if (instances == null || instances.isEmpty()) return new ArrayList<>();
            List<ServiceInstance> result = new ArrayList<>(instances.size());
            for (Instance i : instances) {
                ServiceInstance si = new ServiceInstance();
                si.setServiceName(serviceName);
                si.setIp(i.getIp());
                si.setPort(i.getPort());
                si.setWeight((int) i.getWeight());
                if (i.getMetadata() != null) {
                    si.setMetadata(new LinkedHashMap<>(i.getMetadata()));
                }
                result.add(si);
            }
            return result;
        } catch (NacosException e) {
            log.warn("Nacos discover 失败 svc={}: {}", serviceName, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean heartbeat() {
        try {
            String status = namingService.getServerStatus();
            return "UP".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.warn("Nacos heartbeat 失败 name={}: {}", config.getName(), e.getMessage());
            return false;
        }
    }

    private String groupName() {
        return notEmpty(config.getGroupName()) ? config.getGroupName() : "DEFAULT_GROUP";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
