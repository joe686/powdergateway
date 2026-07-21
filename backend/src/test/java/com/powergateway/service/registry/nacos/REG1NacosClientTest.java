package com.powergateway.service.registry.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.powergateway.model.RegistryConfig;
import com.powergateway.service.registry.RegistryOperationException;
import com.powergateway.service.registry.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REG-1 Task 3 · NacosRegistryClient 单元测试（Mockito mock NamingService）
 *
 * 测试目标：验证 SDK 参数正确传递 + 异常包装 + isConfigured 判空。
 * 由于 Nacos SDK 是 test scope 默认可用；生产打包需 -Pregistry-nacos 才带 SDK。
 */
@ActiveProfiles("test")
class REG1NacosClientTest {

    private NamingService namingService;
    private RegistryConfig config;
    private NacosRegistryClient client;

    @BeforeEach
    void setUp() {
        namingService = mock(NamingService.class);
        config = new RegistryConfig();
        config.setType("nacos");
        config.setName("内部Nacos");
        config.setServerAddr("127.0.0.1:8848");
        config.setGroupName("DEFAULT_GROUP");
        config.setNamespace("public");
        client = new NacosRegistryClient(config, namingService, "plain-password");
    }

    // ============ 基础属性 ============

    @Test
    void getType_返回nacos() {
        assertThat(client.getType()).isEqualTo("nacos");
    }

    @Test
    void getName_返回config的name() {
        assertThat(client.getName()).isEqualTo("内部Nacos");
    }

    @Test
    void isConfigured_serverAddr合法_返回true() {
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_serverAddr为空_返回false() {
        config.setServerAddr("");
        NacosRegistryClient bad = new NacosRegistryClient(config, namingService, "");
        assertThat(bad.isConfigured()).isFalse();
    }

    // ============ register ============

    @Test
    void register_传参正确() throws Exception {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("version", "0.1.0");
        ServiceInstance self = new ServiceInstance();
        self.setServiceName("POWERGATEWAY");
        self.setIp("10.0.0.1");
        self.setPort(8080);
        self.setWeight(1);
        self.setMetadata(meta);

        client.register(self);

        ArgumentCaptor<Instance> captor = ArgumentCaptor.forClass(Instance.class);
        verify(namingService).registerInstance(eq("POWERGATEWAY"), eq("DEFAULT_GROUP"), captor.capture());
        Instance inst = captor.getValue();
        assertThat(inst.getIp()).isEqualTo("10.0.0.1");
        assertThat(inst.getPort()).isEqualTo(8080);
        assertThat(inst.getWeight()).isEqualTo(1.0);
        assertThat(inst.getMetadata()).containsEntry("version", "0.1.0");
    }

    @Test
    void register_NacosException_转RegistryOperationException() throws Exception {
        doThrow(new NacosException(500, "server down"))
                .when(namingService).registerInstance(anyString(), anyString(), any(Instance.class));

        assertThatThrownBy(() -> client.register(sampleInstance()))
                .isInstanceOf(RegistryOperationException.class)
                .hasMessageContaining("register")
                .hasCauseInstanceOf(NacosException.class);
    }

    // ============ deregister ============

    @Test
    void deregister_传参正确() throws Exception {
        // deregister 只知道 serviceName，实际上 Nacos 需要 ip:port 才能定位实例
        // 我们的契约是"注销当前 PG 自己"，因此 deregister 内部记住上次 register 的实例
        ServiceInstance self = sampleInstance();
        client.register(self);
        client.deregister("POWERGATEWAY");

        verify(namingService).deregisterInstance(eq("POWERGATEWAY"), eq("DEFAULT_GROUP"),
                eq("10.0.0.1"), eq(8080));
    }

    @Test
    void deregister_未曾注册_不抛异常且不调SDK() throws Exception {
        client.deregister("SOME_OTHER");
        verify(namingService, org.mockito.Mockito.never())
                .deregisterInstance(anyString(), anyString(), anyString(), anyInt());
    }

    // ============ discover ============

    @Test
    void discover_返回实例列表() throws Exception {
        Instance nacosInst1 = buildNacosInstance("10.0.0.1", 9001, "u1");
        Instance nacosInst2 = buildNacosInstance("10.0.0.2", 9002, "u2");
        when(namingService.selectInstances(eq("CBS_SVC"), eq("DEFAULT_GROUP"), anyBoolean()))
                .thenReturn(Arrays.asList(nacosInst1, nacosInst2));

        List<ServiceInstance> found = client.discover("CBS_SVC");
        assertThat(found).hasSize(2);
        assertThat(found.get(0).getIp()).isEqualTo("10.0.0.1");
        assertThat(found.get(0).getPort()).isEqualTo(9001);
        assertThat(found.get(0).getMetadata()).containsEntry("user", "u1");
        assertThat(found.get(1).getIp()).isEqualTo("10.0.0.2");
    }

    @Test
    void discover_NacosException_返回空List_不抛异常() throws Exception {
        when(namingService.selectInstances(anyString(), anyString(), anyBoolean()))
                .thenThrow(new NacosException(500, "network"));
        assertThat(client.discover("ANY")).isEmpty();
    }

    // ============ heartbeat ============

    @Test
    void heartbeat_serverUp_返回true() {
        when(namingService.getServerStatus()).thenReturn("UP");
        assertThat(client.heartbeat()).isTrue();
    }

    @Test
    void heartbeat_serverDown_返回false() {
        when(namingService.getServerStatus()).thenReturn("DOWN");
        assertThat(client.heartbeat()).isFalse();
    }

    @Test
    void heartbeat_SDK抛异常_返回false() {
        when(namingService.getServerStatus()).thenThrow(new RuntimeException("network"));
        assertThat(client.heartbeat()).isFalse();
    }

    // ============ 辅助 ============

    private ServiceInstance sampleInstance() {
        ServiceInstance si = new ServiceInstance();
        si.setServiceName("POWERGATEWAY");
        si.setIp("10.0.0.1");
        si.setPort(8080);
        si.setMetadata(Collections.emptyMap());
        return si;
    }

    private Instance buildNacosInstance(String ip, int port, String userTag) {
        Instance i = new Instance();
        i.setIp(ip);
        i.setPort(port);
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("user", userTag);
        i.setMetadata(meta);
        return i;
    }
}
