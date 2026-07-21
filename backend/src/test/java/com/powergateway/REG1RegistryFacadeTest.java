package com.powergateway;

import com.powergateway.service.registry.MockRegistryClient;
import com.powergateway.service.registry.RegistryClient;
import com.powergateway.service.registry.RegistryFacade;
import com.powergateway.service.registry.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REG-1 Task 2 · RegistryFacade 门面聚合逻辑测试
 *
 * 纯单元测试（无 Spring 上下文）：手工构造 Facade + 2 个 MockRegistryClient 验证行为。
 */
@ActiveProfiles("test")
class REG1RegistryFacadeTest {

    private MockRegistryClient nacos;
    private MockRegistryClient eureka;
    private RegistryFacade facade;

    @BeforeEach
    void setUp() {
        nacos = new MockRegistryClient("nacos", "内部Nacos");
        eureka = new MockRegistryClient("eureka", "部门Eureka");
        facade = new RegistryFacade(Arrays.asList(nacos, eureka));
    }

    // ============ registerSelfToAll ============

    @Test
    void registerSelfToAll_每个已配置client都被调用() {
        ServiceInstance self = instance("POWERGATEWAY", "10.0.0.1", 8080);
        facade.registerSelfToAll(self);

        assertThat(nacos.registerCalls.get()).isEqualTo(1);
        assertThat(eureka.registerCalls.get()).isEqualTo(1);
    }

    @Test
    void registerSelfToAll_跳过未配置的client() {
        nacos.setConfigured(false);
        ServiceInstance self = instance("POWERGATEWAY", "10.0.0.1", 8080);
        facade.registerSelfToAll(self);

        assertThat(nacos.registerCalls.get()).isEqualTo(0);
        assertThat(eureka.registerCalls.get()).isEqualTo(1);
    }

    @Test
    void registerSelfToAll_全部未配置_不抛异常() {
        nacos.setConfigured(false);
        eureka.setConfigured(false);
        facade.registerSelfToAll(instance("POWERGATEWAY", "10.0.0.1", 8080));
        // 没有异常即可
    }

    // ============ deregisterSelfFromAll ============

    @Test
    void deregisterSelfFromAll_每个已配置client都被调用() {
        facade.deregisterSelfFromAll("POWERGATEWAY");
        assertThat(nacos.deregisterCalls.get()).isEqualTo(1);
        assertThat(eureka.deregisterCalls.get()).isEqualTo(1);
    }

    // ============ discover ============

    @Test
    void discover_跨client聚合() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 9001));
        eureka.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.2", 9002));

        List<ServiceInstance> found = facade.discover("CBS_SVC");
        assertThat(found).hasSize(2);
        Set<String> ips = new HashSet<>();
        for (ServiceInstance si : found) {
            ips.add(si.getIp());
        }
        assertThat(ips).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    void discover_跳过未配置client() {
        nacos.setConfigured(false);
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 9001));
        eureka.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.2", 9002));

        List<ServiceInstance> found = facade.discover("CBS_SVC");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getIp()).isEqualTo("10.0.0.2");
        assertThat(nacos.discoverCalls.get()).isEqualTo(0);
    }

    @Test
    void discover_无实例_返回空列表() {
        assertThat(facade.discover("UNKNOWN_SVC")).isEmpty();
    }

    // ============ choose ============

    @Test
    void choose_有实例_返回其中一个() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 9001));
        Optional<ServiceInstance> chosen = facade.choose("CBS_SVC");
        assertThat(chosen).isPresent();
        assertThat(chosen.get().getIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void choose_无实例_返回empty() {
        assertThat(facade.choose("UNKNOWN")).isEmpty();
    }

    @Test
    void choose_轮询_返回不同实例() {
        nacos.preload("CBS_SVC",
                instance("CBS_SVC", "10.0.0.1", 9001),
                instance("CBS_SVC", "10.0.0.2", 9002),
                instance("CBS_SVC", "10.0.0.3", 9003));

        Set<String> pickedIps = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            pickedIps.add(facade.choose("CBS_SVC").orElseThrow(AssertionError::new).getIp());
        }
        // 3 实例调用 6 次，Round Robin 应至少覆盖 3 个不同 IP
        assertThat(pickedIps).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2", "10.0.0.3");
    }

    @Test
    void choose_不同服务名_cursor独立() {
        nacos.preload("SVC_A", instance("SVC_A", "1.1.1.1", 8001));
        nacos.preload("SVC_B", instance("SVC_B", "2.2.2.2", 8002));

        assertThat(facade.choose("SVC_A").orElseThrow(AssertionError::new).getIp()).isEqualTo("1.1.1.1");
        assertThat(facade.choose("SVC_B").orElseThrow(AssertionError::new).getIp()).isEqualTo("2.2.2.2");
    }

    // ============ statusAll ============

    @Test
    void statusAll_返回所有client状态() {
        nacos.setHeartbeatOk(true);
        eureka.setHeartbeatOk(false);
        // 触发一次心跳更新
        facade.heartbeatAll();

        List<RegistryFacade.ClientStatus> statuses = facade.statusAll();
        assertThat(statuses).hasSize(2);

        RegistryFacade.ClientStatus s1 = statuses.stream().filter(s -> s.getName().equals("内部Nacos")).findFirst().orElseThrow(AssertionError::new);
        RegistryFacade.ClientStatus s2 = statuses.stream().filter(s -> s.getName().equals("部门Eureka")).findFirst().orElseThrow(AssertionError::new);
        assertThat(s1.isHealthy()).isTrue();
        assertThat(s2.isHealthy()).isFalse();
    }

    // ============ heartbeatAll ============

    @Test
    void heartbeatAll_每个已配置client都被调用() {
        facade.heartbeatAll();
        assertThat(nacos.heartbeatCalls.get()).isEqualTo(1);
        assertThat(eureka.heartbeatCalls.get()).isEqualTo(1);
    }

    // ============ 空 client 列表 ============

    @Test
    void 空client列表_choose返回empty_不崩溃() {
        RegistryFacade empty = new RegistryFacade(Collections.emptyList());
        assertThat(empty.choose("ANY")).isEmpty();
        empty.registerSelfToAll(instance("POWERGATEWAY", "10.0.0.1", 8080));
        empty.deregisterSelfFromAll("POWERGATEWAY");
        empty.heartbeatAll();
        assertThat(empty.statusAll()).isEmpty();
    }

    // ============ 辅助 ============

    private static ServiceInstance instance(String name, String ip, int port) {
        ServiceInstance si = new ServiceInstance();
        si.setServiceName(name);
        si.setIp(ip);
        si.setPort(port);
        si.setScheme("http");
        return si;
    }
}
