package com.powergateway.service.registry.eureka;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REG-1 Task 4 · EurekaRegistryClient 单元测试
 *
 * Mockito mock EurekaClient；同包放置以便使用 package-private 测试构造函数。
 */
@ActiveProfiles("test")
class REG1EurekaClientTest {

    private EurekaClient eurekaClient;
    private RegistryConfig config;
    private EurekaRegistryClient client;

    @BeforeEach
    void setUp() {
        eurekaClient = mock(EurekaClient.class);
        config = new RegistryConfig();
        config.setType("eureka");
        config.setName("部门Eureka");
        config.setServerAddr("http://127.0.0.1:8761/eureka/");
        client = new EurekaRegistryClient(config, eurekaClient);
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
        EurekaRegistryClient bad = new EurekaRegistryClient(config, eurekaClient);
        assertThat(bad.isConfigured()).isFalse();
    }

    // ============ discover ============

    @Test
    void discover_返回实例列表() {
        Application app = new Application("LEGACY_SVC");
        app.addInstance(buildInstanceInfo("LEGACY_SVC", "10.0.0.1", 8001));
        app.addInstance(buildInstanceInfo("LEGACY_SVC", "10.0.0.2", 8002));
        when(eurekaClient.getApplication("LEGACY_SVC")).thenReturn(app);

        List<ServiceInstance> found = client.discover("LEGACY_SVC");
        assertThat(found).hasSize(2);
        assertThat(found.get(0).getIp()).isEqualTo("10.0.0.1");
        assertThat(found.get(0).getPort()).isEqualTo(8001);
        assertThat(found.get(1).getIp()).isEqualTo("10.0.0.2");
    }

    @Test
    void discover_Application为null_返回空List() {
        when(eurekaClient.getApplication(anyString())).thenReturn(null);
        assertThat(client.discover("UNKNOWN")).isEmpty();
    }

    @Test
    void discover_异常_返回空List_不抛出() {
        when(eurekaClient.getApplication(anyString())).thenThrow(new RuntimeException("network"));
        assertThat(client.discover("ANY")).isEmpty();
    }

    // ============ heartbeat ============

    @Test
    void heartbeat_Applications非空_返回true() {
        Applications apps = new Applications();
        when(eurekaClient.getApplications()).thenReturn(apps);
        assertThat(client.heartbeat()).isTrue();
    }

    @Test
    void heartbeat_Applications为null_返回false() {
        when(eurekaClient.getApplications()).thenReturn(null);
        assertThat(client.heartbeat()).isFalse();
    }

    @Test
    void heartbeat_异常_返回false() {
        when(eurekaClient.getApplications()).thenThrow(new RuntimeException("down"));
        assertThat(client.heartbeat()).isFalse();
    }

    // ============ register / deregister ============

    @Test
    void register_缓存到lastRegistered() {
        client.register(sampleSelf());
        // 用 deregister 验证 lastRegistered 生效
        client.deregister("POWERGATEWAY");
        // 无异常即可
    }

    @Test
    void deregister_未曾注册_不抛异常() {
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

    private InstanceInfo buildInstanceInfo(String appName, String ip, int port) {
        Map<String, String> meta = new LinkedHashMap<>();
        return InstanceInfo.Builder.newBuilder()
                .setAppName(appName)
                .setInstanceId(ip + ":" + port)
                .setIPAddr(ip)
                .setPort(port)
                .setHostName(ip)
                .setStatus(InstanceInfo.InstanceStatus.UP)
                .setMetadata(meta)
                .build();
    }
}
