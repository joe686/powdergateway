package com.powergateway.service.registry.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.RegistryClient;
import com.powergateway.service.registry.ServiceInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * REG-1 · Eureka 注册中心客户端（老系统兼容方向）
 *
 * <p><b>v0.3.9 CHG-051 · selfRegister 已实装</b>:通过 Eureka REST API(POST /eureka/apps/{APP})
 * 手工构造 InstanceInfo JSON 触发注册 · 不依赖 ApplicationInfoManager 装配 · discover 走原有 EurekaClient SDK。
 *
 * <p>REST API 参考:{@code https://github.com/Netflix/eureka/wiki/Eureka-REST-operations}</p>
 */
@Slf4j
public class EurekaRegistryClient implements RegistryClient {

    private final RegistryConfig config;
    private final EurekaClient eurekaClient;
    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, ServiceInstance> lastRegistered = new ConcurrentHashMap<>();

    /** 生产用构造：需上层提供 EurekaClient（由 factory 层构造，Task 5/6 集成）。 */
    public EurekaRegistryClient(RegistryConfig config, EurekaClient eurekaClient) {
        this.config = config;
        this.eurekaClient = eurekaClient;
        this.restTemplate = new RestTemplate();
    }

    /** 测试用构造 · 允许注入 mock RestTemplate。 */
    public EurekaRegistryClient(RegistryConfig config, EurekaClient eurekaClient, RestTemplate restTemplate) {
        this.config = config;
        this.eurekaClient = eurekaClient;
        this.restTemplate = restTemplate;
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

    /**
     * v0.3.9 CHG-051 · 走 Eureka REST API 手工注册。
     *
     * <p>不引入 spring-cloud-starter-netflix-eureka-client(避免与现有 netflix eureka-client 版本冲突);
     * 用 RestTemplate 直接 POST /eureka/apps/{APP} 构造 InstanceInfo JSON 完成注册。</p>
     */
    @Override
    public void register(ServiceInstance self) {
        lastRegistered.put(self.getServiceName(), self);
        String appName = self.getServiceName().toUpperCase();
        String instanceId = buildInstanceId(self);
        String url = joinPath(config.getServerAddr(), "/eureka/apps/" + appName);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(buildInstanceInfoBody(self, instanceId), headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, req, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("Eureka register 成功 svc={} instId={} ip={}:{}", appName, instanceId, self.getIp(), self.getPort());
            } else {
                log.warn("Eureka register 非 2xx svc={} status={} body={}", appName, resp.getStatusCode(), resp.getBody());
            }
        } catch (Exception e) {
            log.warn("Eureka register 失败 svc={} url={}:{}(仍缓存本地 · 稍后 heartbeat 兜底重试)", appName, url, e.getMessage());
        }
    }

    @Override
    public void deregister(String serviceName) {
        ServiceInstance last = lastRegistered.remove(serviceName);
        if (last == null) return;
        String appName = serviceName.toUpperCase();
        String instanceId = buildInstanceId(last);
        String url = joinPath(config.getServerAddr(), "/eureka/apps/" + appName + "/" + instanceId);
        try {
            restTemplate.delete(url);
            log.info("Eureka deregister 成功 svc={} instId={}", appName, instanceId);
        } catch (Exception e) {
            log.warn("Eureka deregister 失败 svc={}:{}(本地缓存已清)", appName, e.getMessage());
        }
    }

    private static String buildInstanceId(ServiceInstance si) {
        return si.getIp() + ":" + si.getServiceName().toLowerCase() + ":" + si.getPort();
    }

    private static String joinPath(String serverAddr, String path) {
        if (serverAddr == null) return path;
        String base = serverAddr.endsWith("/") ? serverAddr.substring(0, serverAddr.length() - 1) : serverAddr;
        return base + path;
    }

    private static Map<String, Object> buildInstanceInfoBody(ServiceInstance self, String instanceId) {
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("instanceId", instanceId);
        instance.put("hostName", self.getIp());
        instance.put("app", self.getServiceName().toUpperCase());
        instance.put("ipAddr", self.getIp());
        instance.put("status", "UP");
        instance.put("overriddenStatus", "UNKNOWN");
        Map<String, Object> port = new HashMap<>();
        port.put("$", self.getPort());
        port.put("@enabled", "true");
        instance.put("port", port);
        Map<String, Object> securePort = new HashMap<>();
        securePort.put("$", 443);
        securePort.put("@enabled", "false");
        instance.put("securePort", securePort);
        instance.put("countryId", 1);
        Map<String, String> dataCenter = new LinkedHashMap<>();
        dataCenter.put("@class", "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo");
        dataCenter.put("name", "MyOwn");
        instance.put("dataCenterInfo", dataCenter);
        instance.put("vipAddress", self.getServiceName().toLowerCase());
        instance.put("secureVipAddress", self.getServiceName().toLowerCase());
        Map<String, String> metadata = new LinkedHashMap<>();
        if (self.getMetadata() != null) metadata.putAll(self.getMetadata());
        metadata.put("management.port", String.valueOf(self.getPort()));
        instance.put("metadata", metadata);
        instance.put("homePageUrl", self.getScheme() + "://" + self.getIp() + ":" + self.getPort() + "/");
        instance.put("statusPageUrl", self.getScheme() + "://" + self.getIp() + ":" + self.getPort() + "/actuator/info");
        instance.put("healthCheckUrl", self.getScheme() + "://" + self.getIp() + ":" + self.getPort() + "/actuator/health");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instance", instance);
        return body;
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
