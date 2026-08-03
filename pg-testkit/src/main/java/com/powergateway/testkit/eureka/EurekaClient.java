package com.powergateway.testkit.eureka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.3.11 CHG-054 · pg-testkit 独立 Eureka REST 客户端。
 *
 * <p>不引 spring-cloud-starter-netflix-eureka-client(避免版本冲突) · 与 backend
 * EurekaRegistryClient 保持一致的手工 REST 调用(POST /apps/{APP} + InstanceInfo JSON)。</p>
 *
 * <p>Netflix Eureka 惯例:serverUrl 以 /eureka 或 /eureka/ 结尾 · 拼接时补 /apps/... 前缀。</p>
 */
@Slf4j
public class EurekaClient {

    private final String serverUrl;
    private final RestTemplate restTemplate;

    public EurekaClient(String serverUrl) {
        this(serverUrl, new RestTemplate());
    }

    /** 测试用构造 · 允许注入 mock RestTemplate。 */
    public EurekaClient(String serverUrl, RestTemplate restTemplate) {
        this.serverUrl = serverUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * 注册一个实例到 Eureka Server。
     *
     * @return true 若返 2xx · false 否则
     */
    public boolean register(EurekaAppConfig.AppInstance app) {
        String appName = app.getName().toUpperCase();
        String instanceId = buildInstanceId(app);
        String url = joinPath(serverUrl, "/apps/" + appName);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(buildInstanceInfoBody(app, instanceId), headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, req, String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            if (ok) {
                log.info("[pg-testkit] Eureka register OK app={} inst={} url={}", appName, instanceId, url);
            } else {
                log.warn("[pg-testkit] Eureka register 非 2xx app={} status={} body={}", appName, resp.getStatusCode(), resp.getBody());
            }
            return ok;
        } catch (Exception e) {
            log.warn("[pg-testkit] Eureka register 失败 app={} url={}: {}", appName, url, e.getMessage());
            return false;
        }
    }

    /**
     * v0.3.13 CHG-056 · 续约(Netflix Eureka lease renewal · 每 30s 一次)。
     *
     * <p>PUT /apps/{APP}/{instanceId} · 语义:</p>
     * <ul>
     *   <li>200 OK - 续约成功 · 返 {@link RenewResult#RENEWED}</li>
     *   <li>404 Not Found - 实例已被 Server evict · 返 {@link RenewResult#NOT_FOUND}(调方应重新 register)</li>
     *   <li>其他 - 返 {@link RenewResult#FAILED} · log warn</li>
     * </ul>
     */
    public RenewResult renew(EurekaAppConfig.AppInstance app) {
        String appName = app.getName().toUpperCase();
        String instanceId = buildInstanceId(app);
        String url = joinPath(serverUrl, "/apps/" + appName + "/" + instanceId);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.PUT, HttpEntity.EMPTY, String.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                log.debug("[pg-testkit] Eureka renew OK app={} inst={}", appName, instanceId);
                return RenewResult.RENEWED;
            }
            log.warn("[pg-testkit] Eureka renew 非 2xx app={} status={}", appName, resp.getStatusCode());
            return RenewResult.FAILED;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("[pg-testkit] Eureka renew 404 · 需 re-register app={} inst={}", appName, instanceId);
                return RenewResult.NOT_FOUND;
            }
            log.warn("[pg-testkit] Eureka renew 客户端错 app={} status={}: {}", appName, e.getStatusCode(), e.getMessage());
            return RenewResult.FAILED;
        } catch (Exception e) {
            log.warn("[pg-testkit] Eureka renew 失败 app={}: {}", appName, e.getMessage());
            return RenewResult.FAILED;
        }
    }

    public enum RenewResult { RENEWED, NOT_FOUND, FAILED }

    public boolean deregister(EurekaAppConfig.AppInstance app) {
        String appName = app.getName().toUpperCase();
        String instanceId = buildInstanceId(app);
        String url = joinPath(serverUrl, "/apps/" + appName + "/" + instanceId);
        try {
            restTemplate.delete(url);
            log.info("[pg-testkit] Eureka deregister OK app={} inst={}", appName, instanceId);
            return true;
        } catch (Exception e) {
            log.warn("[pg-testkit] Eureka deregister 失败 app={}: {}", appName, e.getMessage());
            return false;
        }
    }

    private static String buildInstanceId(EurekaAppConfig.AppInstance app) {
        return app.getIp() + ":" + app.getName().toLowerCase() + ":" + app.getPort();
    }

    private static String joinPath(String base, String path) {
        if (base == null) return path;
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return b + path;
    }

    /** 与 backend EurekaRegistryClient.buildInstanceInfoBody 保持一致(Netflix 规范) */
    private static Map<String, Object> buildInstanceInfoBody(EurekaAppConfig.AppInstance app, String instanceId) {
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("instanceId", instanceId);
        instance.put("hostName", app.getIp());
        instance.put("app", app.getName().toUpperCase());
        instance.put("ipAddr", app.getIp());
        instance.put("status", "UP");
        instance.put("overriddenStatus", "UNKNOWN");
        Map<String, Object> port = new HashMap<>();
        port.put("$", app.getPort());
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
        instance.put("vipAddress", app.getName().toLowerCase());
        instance.put("secureVipAddress", app.getName().toLowerCase());
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("management.port", String.valueOf(app.getPort()));
        metadata.put("source", "pg-testkit");
        instance.put("metadata", metadata);
        String home = app.getScheme() + "://" + app.getIp() + ":" + app.getPort() + "/";
        instance.put("homePageUrl", home);
        instance.put("statusPageUrl", home + "actuator/info");
        instance.put("healthCheckUrl", home + "actuator/health");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instance", instance);
        return body;
    }
}
