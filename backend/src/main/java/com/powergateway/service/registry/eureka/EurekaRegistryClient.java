package com.powergateway.service.registry.eureka;

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
 * REG-1 · Eureka 注册中心客户端(全 REST · v0.3.12 重构)
 *
 * <p>v0.3.9 CHG-051 加了 register/deregister REST · 但 discover 仍走 Netflix EurekaClient SDK(需
 * ApplicationInfoManager 装配 · RegistryClientRegistrar 因此 case eureka return null 未自动装配)。
 *
 * <p><b>v0.3.12 CHG-055 · 全 REST 化</b>:discover 也走 REST(GET /apps/{APP} + Accept: application/json)
 * 完全去除 Netflix SDK 依赖 · RegistryClientRegistrar 可 new EurekaRegistryClient(cfg, new RestTemplate())
 * 直接装配 · v0.3.9 的 register 功能真正可用。</p>
 *
 * <p>REST API 参考:{@code https://github.com/Netflix/eureka/wiki/Eureka-REST-operations}</p>
 */
@Slf4j
public class EurekaRegistryClient implements RegistryClient {

    private final RegistryConfig config;
    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, ServiceInstance> lastRegistered = new ConcurrentHashMap<>();

    /** 生产用构造。 */
    public EurekaRegistryClient(RegistryConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    /** 单参构造 · 默认新建 RestTemplate。 */
    public EurekaRegistryClient(RegistryConfig config) {
        this(config, new RestTemplate());
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
        return notEmpty(config.getServerAddr());
    }

    /**
     * v0.3.9 CHG-051 · 走 Eureka REST API 手工注册。
     * POST /apps/{APP} + body: InstanceInfo JSON。
     */
    @Override
    public void register(ServiceInstance self) {
        lastRegistered.put(self.getServiceName(), self);
        String appName = self.getServiceName().toUpperCase();
        String instanceId = buildInstanceId(self);
        String url = joinPath(config.getServerAddr(), "/apps/" + appName);
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
            log.warn("Eureka register 失败 svc={} url={}:{}(仍缓存本地 · heartbeat 兜底重试)", appName, url, e.getMessage());
        }
    }

    @Override
    public void deregister(String serviceName) {
        ServiceInstance last = lastRegistered.remove(serviceName);
        if (last == null) return;
        String appName = serviceName.toUpperCase();
        String instanceId = buildInstanceId(last);
        String url = joinPath(config.getServerAddr(), "/apps/" + appName + "/" + instanceId);
        try {
            restTemplate.delete(url);
            log.info("Eureka deregister 成功 svc={} instId={}", appName, instanceId);
        } catch (Exception e) {
            log.warn("Eureka deregister 失败 svc={}:{}(本地缓存已清)", appName, e.getMessage());
        }
    }

    /**
     * v0.3.12 CHG-055 · discover 走 REST · GET /apps/{APP} + Accept: application/json。
     * 解析响应 application.instance[] 转 List&lt;ServiceInstance&gt; · 只返 status=UP。
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<ServiceInstance> discover(String serviceName) {
        String appName = serviceName.toUpperCase();
        String url = joinPath(config.getServerAddr(), "/apps/" + appName);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<Map> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, req, Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) return new ArrayList<>();
            Map<String, Object> body = resp.getBody();
            Map<String, Object> app = (Map<String, Object>) body.get("application");
            if (app == null) return new ArrayList<>();
            Object instanceObj = app.get("instance");
            if (instanceObj == null) return new ArrayList<>();
            List<Map<String, Object>> instances;
            if (instanceObj instanceof List) {
                instances = (List<Map<String, Object>>) instanceObj;
            } else if (instanceObj instanceof Map) {
                instances = new ArrayList<>();
                instances.add((Map<String, Object>) instanceObj);
            } else {
                return new ArrayList<>();
            }
            List<ServiceInstance> result = new ArrayList<>(instances.size());
            for (Map<String, Object> inst : instances) {
                if (!"UP".equalsIgnoreCase(String.valueOf(inst.get("status")))) continue;
                ServiceInstance si = new ServiceInstance();
                si.setServiceName(serviceName);
                si.setIp(String.valueOf(inst.get("ipAddr")));
                Object portObj = inst.get("port");
                int port = 0;
                if (portObj instanceof Map) {
                    Object dollar = ((Map<String, Object>) portObj).get("$");
                    if (dollar != null) port = Integer.parseInt(String.valueOf(dollar));
                }
                si.setPort(port);
                Object secureObj = inst.get("securePort");
                boolean secure = false;
                if (secureObj instanceof Map) {
                    secure = Boolean.parseBoolean(String.valueOf(((Map<String, Object>) secureObj).get("@enabled")));
                }
                si.setScheme(secure ? "https" : "http");
                Object metaObj = inst.get("metadata");
                if (metaObj instanceof Map) {
                    Map<String, Object> raw = (Map<String, Object>) metaObj;
                    Map<String, String> meta = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : raw.entrySet()) meta.put(e.getKey(), String.valueOf(e.getValue()));
                    si.setMetadata(meta);
                }
                result.add(si);
            }
            return result;
        } catch (Exception e) {
            log.warn("Eureka discover 失败 svc={} url={}: {}", appName, url, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * v0.3.12 CHG-055 · heartbeat 用 GET /apps · 返 2xx 即通。
     * 原 SDK 版本用 getApplications() · 现全 REST 直调根路径。
     */
    @Override
    public boolean heartbeat() {
        String url = joinPath(config.getServerAddr(), "/apps");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, req, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Eureka heartbeat 失败 name={} url={}: {}", config.getName(), url, e.getMessage());
            return false;
        }
    }

    private static String buildInstanceId(ServiceInstance si) {
        return si.getIp() + ":" + si.getServiceName().toLowerCase() + ":" + si.getPort();
    }

    /**
     * v0.3.10 CHG-052 · URL 拼接:约定 serverAddr 以 /eureka/ 结尾(Netflix 惯例)· path 从 /apps/... 开始 · 不重复 /eureka。
     */
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

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
