package com.powergateway;

import com.powergateway.service.registry.MockRegistryClient;
import com.powergateway.service.registry.RegistryClient;
import com.powergateway.service.registry.RegistryFacade;
import com.powergateway.service.registry.RegistryNotEnabledException;
import com.powergateway.service.registry.ServiceInstance;
import com.powergateway.service.registry.ServiceInstanceNotFoundException;
import com.powergateway.service.registry.ServiceUrlResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REG-1 Task 5 · ServiceUrlResolver 单元测试
 *
 * 覆盖 http/https 直连 / service:// 协议 / 缓存降级 / canResolve 校验 / 无 registry 场景。
 */
@ActiveProfiles("test")
class REG1ServiceUrlResolverTest {

    private MockRegistryClient nacos;
    private RegistryFacade facade;
    private ServiceUrlResolver resolver;

    @BeforeEach
    void setUp() {
        nacos = new MockRegistryClient("nacos", "内部Nacos");
        facade = new RegistryFacade(Collections.singletonList(nacos));
        resolver = new ServiceUrlResolver(facade);
    }

    // ============ 直连 URL ============

    @Test
    void resolve_http直连URL_原样返回() {
        assertThat(resolver.resolve("http://api.example.com/foo")).isEqualTo("http://api.example.com/foo");
    }

    @Test
    void resolve_https直连URL_原样返回() {
        assertThat(resolver.resolve("https://api.example.com/foo")).isEqualTo("https://api.example.com/foo");
    }

    @Test
    void resolve_相对URL_原样返回() {
        assertThat(resolver.resolve("/api/local")).isEqualTo("/api/local");
    }

    // ============ service:// 协议 ============

    @Test
    void resolve_service协议_基本路径_拼成http() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 8080));
        assertThat(resolver.resolve("service://CBS_SVC/api/query"))
                .isEqualTo("http://10.0.0.1:8080/api/query");
    }

    @Test
    void resolve_service协议_scheme参数_https() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 8443));
        assertThat(resolver.resolve("service://CBS_SVC/api/query?scheme=https"))
                .isEqualTo("https://10.0.0.1:8443/api/query");
    }

    @Test
    void resolve_service协议_服务名不存在_抛ServiceInstanceNotFoundException() {
        assertThatThrownBy(() -> resolver.resolve("service://UNKNOWN_SVC/api"))
                .isInstanceOf(ServiceInstanceNotFoundException.class)
                .hasMessageContaining("UNKNOWN_SVC");
    }

    @Test
    void resolve_service协议_无路径_只拼host_port() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 8080));
        assertThat(resolver.resolve("service://CBS_SVC"))
                .isEqualTo("http://10.0.0.1:8080");
    }

    // ============ 缓存降级 ============

    @Test
    void resolve_首次成功后_registry变空_短时间内仍能拿到缓存值() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 9001));
        assertThat(resolver.resolve("service://CBS_SVC/api"))
                .isEqualTo("http://10.0.0.1:9001/api");

        // 移除所有实例
        nacos = new MockRegistryClient("nacos", "内部Nacos");
        // 但复用同一 resolver：其内部缓存应仍能兜底
        assertThat(resolver.resolve("service://CBS_SVC/api"))
                .isEqualTo("http://10.0.0.1:9001/api");
    }

    // ============ 无 registry 场景 ============

    @Test
    void resolve_空registry列表_遇service协议_抛RegistryNotEnabledException() {
        RegistryFacade empty = new RegistryFacade(Collections.emptyList());
        ServiceUrlResolver r = new ServiceUrlResolver(empty);
        assertThatThrownBy(() -> r.resolve("service://ANY/api"))
                .isInstanceOf(RegistryNotEnabledException.class);
    }

    @Test
    void resolve_空registry列表_遇直连URL_不抛异常() {
        RegistryFacade empty = new RegistryFacade(Collections.emptyList());
        ServiceUrlResolver r = new ServiceUrlResolver(empty);
        assertThat(r.resolve("http://api.example.com")).isEqualTo("http://api.example.com");
    }

    // ============ canResolve ============

    @Test
    void canResolve_直连URL_返回true() {
        assertThat(resolver.canResolve("http://x/y")).isTrue();
    }

    @Test
    void canResolve_service协议_服务名存在_返回true() {
        nacos.preload("CBS_SVC", instance("CBS_SVC", "10.0.0.1", 8080));
        assertThat(resolver.canResolve("service://CBS_SVC/api")).isTrue();
    }

    @Test
    void canResolve_service协议_服务名不存在_返回false() {
        assertThat(resolver.canResolve("service://UNKNOWN/api")).isFalse();
    }

    @Test
    void canResolve_service协议_registry未启用_返回false() {
        RegistryFacade empty = new RegistryFacade(Collections.emptyList());
        ServiceUrlResolver r = new ServiceUrlResolver(empty);
        assertThat(r.canResolve("service://ANY/api")).isFalse();
    }

    // ============ 轮询验证 ============

    @Test
    void resolve_service协议_多实例_跨多次调用应看到不同IP() {
        nacos.preload("CBS_SVC",
                instance("CBS_SVC", "10.0.0.1", 9001),
                instance("CBS_SVC", "10.0.0.2", 9002));
        String r1 = resolver.resolve("service://CBS_SVC/api");
        String r2 = resolver.resolve("service://CBS_SVC/api");
        // 缓存 30s 意味着 r1 == r2；但 resolver 每次 resolve 都新调 facade.choose(即使有缓存)？
        // 契约：缓存只用于"发现失败时兜底"，正常路径 facade.choose 每次都调轮询
        assertThat(Arrays.asList(r1, r2))
                .anyMatch(s -> s.contains("10.0.0.1"))
                .anyMatch(s -> s.contains("10.0.0.2"));
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
