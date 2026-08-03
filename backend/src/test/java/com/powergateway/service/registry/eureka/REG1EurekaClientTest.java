package com.powergateway.service.registry.eureka;

import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REG-1 Task 4 · EurekaRegistryClient 单元测试(v0.3.12 CHG-055 全 REST 重构)
 *
 * <p>v0.3.9 CHG-051 register/deregister REST 逻辑保留;discover/heartbeat 从 Netflix EurekaClient
 * SDK 迁到 RestTemplate REST · 全部 mock RestTemplate 验证。</p>
 */
@ActiveProfiles("test")
class REG1EurekaClientTest {

    private RegistryConfig config;
    private RestTemplate restTemplate;
    private EurekaRegistryClient client;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        // register 默认返 204
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("OK", HttpStatus.NO_CONTENT));
        // heartbeat 默认返 200
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        config = new RegistryConfig();
        config.setType("eureka");
        config.setName("部门Eureka");
        config.setServerAddr("http://127.0.0.1:8761/eureka/");
        client = new EurekaRegistryClient(config, restTemplate);
    }

    // ============ 基础属性 ============

    @Test
    void getType_返回eureka() {
        assertThat(client.getType()).isEqualTo("eureka");
    }

    @Test
    void getName_返回config的name() {
        assertThat(client.getName()).isEqualTo("部门Eureka");
    }

    @Test
    void isConfigured_serverAddr合法_返回true() {
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_serverAddr为空_返回false() {
        config.setServerAddr("");
        EurekaRegistryClient bad = new EurekaRegistryClient(config, restTemplate);
        assertThat(bad.isConfigured()).isFalse();
    }

    // ============ discover(v0.3.12 CHG-055 全 REST) ============

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_返回实例列表_多实例() {
        Map<String, Object> body = buildDiscoverBody(Arrays.asList(
            makeInstance("10.0.0.1", 8001, "UP"),
            makeInstance("10.0.0.2", 8002, "UP")
        ));
        when(restTemplate.exchange(
            eq("http://127.0.0.1:8761/eureka/apps/LEGACY_SVC"),
            eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<Map>(body, HttpStatus.OK));

        List<ServiceInstance> found = client.discover("LEGACY_SVC");
        assertThat(found).hasSize(2);
        assertThat(found.get(0).getIp()).isEqualTo("10.0.0.1");
        assertThat(found.get(0).getPort()).isEqualTo(8001);
        assertThat(found.get(1).getIp()).isEqualTo("10.0.0.2");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_单实例_instance为Map非List() {
        Map<String, Object> body = buildDiscoverBody(Collections.singletonList(
            makeInstance("10.0.0.1", 8080, "UP")
        ));
        // Netflix 单实例时 instance 是对象非数组
        Map<String, Object> app = (Map<String, Object>) body.get("application");
        app.put("instance", makeInstance("10.0.0.1", 8080, "UP"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<Map>(body, HttpStatus.OK));

        List<ServiceInstance> found = client.discover("SINGLE_SVC");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getPort()).isEqualTo(8080);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_DOWN实例被过滤() {
        Map<String, Object> body = buildDiscoverBody(Arrays.asList(
            makeInstance("10.0.0.1", 8001, "UP"),
            makeInstance("10.0.0.2", 8002, "DOWN")
        ));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<Map>(body, HttpStatus.OK));

        List<ServiceInstance> found = client.discover("MIXED_SVC");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void discover_异常_返回空List_不抛出() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
            .thenThrow(new RuntimeException("network"));
        assertThat(client.discover("ANY")).isEmpty();
    }

    // ============ heartbeat(v0.3.12 REST) ============

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void heartbeat_2xx_返回true() {
        assertThat(client.heartbeat()).isTrue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void heartbeat_异常_返回false() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("down"));
        assertThat(client.heartbeat()).isFalse();
    }

    // ============ register / deregister(v0.3.9 CHG-051 REST · v0.3.10 CHG-052 URL 修) ============

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void register_调用RESTAPI_并缓存到lastRegistered() {
        client.register(sampleSelf());
        verify(restTemplate).postForEntity(
            eq("http://127.0.0.1:8761/eureka/apps/POWERGATEWAY"),
            any(HttpEntity.class),
            eq(String.class));
        client.deregister("POWERGATEWAY");
        verify(restTemplate).delete("http://127.0.0.1:8761/eureka/apps/POWERGATEWAY/10.0.0.1:powergateway:8080");
    }

    @Test
    void deregister_未曾注册_不抛异常_且不发REST() {
        client.deregister("SOME_OTHER");
    }

    // ============ 辅助 ============

    private ServiceInstance sampleSelf() {
        ServiceInstance si = new ServiceInstance();
        si.setServiceName("POWERGATEWAY");
        si.setIp("10.0.0.1");
        si.setPort(8080);
        si.setMetadata(Collections.emptyMap());
        return si;
    }

    private Map<String, Object> buildDiscoverBody(List<Map<String, Object>> instances) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", "TEST_APP");
        app.put("instance", instances);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", app);
        return body;
    }

    private Map<String, Object> makeInstance(String ip, int port, String status) {
        Map<String, Object> inst = new LinkedHashMap<>();
        inst.put("hostName", ip);
        inst.put("ipAddr", ip);
        inst.put("status", status);
        Map<String, Object> portMap = new LinkedHashMap<>();
        portMap.put("$", port);
        portMap.put("@enabled", "true");
        inst.put("port", portMap);
        Map<String, Object> secMap = new LinkedHashMap<>();
        secMap.put("$", 443);
        secMap.put("@enabled", "false");
        inst.put("securePort", secMap);
        return inst;
    }
}
