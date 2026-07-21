package com.powergateway.service.registry;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * REG-1 · service:// 协议 URL 解析器
 *
 * <p>业务代码写 {@code service://SERVICE_NAME/path?scheme=https}，运行时经本类解析为
 * 具体 {@code http[s]://ip:port/path}。
 *
 * <p>解析规则：
 * <ul>
 *   <li>{@code http:// / https:// / 相对路径} → 原样返回（直连场景）</li>
 *   <li>{@code service://SVC/path} → 通过 {@link RegistryFacade#choose(String)} 拿实例 + 拼接</li>
 *   <li>Facade 无 client → 抛 {@link RegistryNotEnabledException}</li>
 *   <li>服务名找不到实例（且缓存已过期） → 抛 {@link ServiceInstanceNotFoundException}</li>
 * </ul>
 *
 * <p>本地缓存：Caffeine 30s TTL，用于兜底注册中心短暂抖动。
 */
@Slf4j
@Service
public class ServiceUrlResolver {

    private static final String SERVICE_PROTOCOL = "service://";

    private final RegistryFacade facade;
    private final Cache<String, ServiceInstance> cache;

    public ServiceUrlResolver(RegistryFacade facade) {
        this.facade = facade;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(1024)
                .build();
    }

    /**
     * 解析 URL 为具体可访问的地址。
     *
     * @throws RegistryNotEnabledException     遇 service:// 协议但 registry facade 无可用 client
     * @throws ServiceInstanceNotFoundException 服务名找不到任何实例且缓存已空
     */
    public String resolve(String url) {
        if (url == null || url.isEmpty() || !url.startsWith(SERVICE_PROTOCOL)) {
            return url;
        }
        Parsed p = parseServiceUrl(url);
        ServiceInstance instance = chooseInstance(p.serviceName);
        String scheme = p.scheme != null ? p.scheme : (instance.getScheme() == null || instance.getScheme().isEmpty() ? "http" : instance.getScheme());
        String path = p.path == null ? "" : p.path;
        return scheme + "://" + instance.getIp() + ":" + instance.getPort() + path;
    }

    /** 校验一个 URL 是否能被解析（不实际调 registry.choose，用于 saveRoute 时校验配置合法性）。 */
    public boolean canResolve(String url) {
        if (url == null || url.isEmpty() || !url.startsWith(SERVICE_PROTOCOL)) {
            return true;
        }
        Parsed p = parseServiceUrl(url);
        if (!facade.hasAnyConfiguredClient()) return false;
        Optional<ServiceInstance> chosen = facade.choose(p.serviceName);
        if (chosen.isPresent()) return true;
        // 兜底看缓存
        return cache.getIfPresent(p.serviceName) != null;
    }

    private ServiceInstance chooseInstance(String serviceName) {
        if (!facade.hasAnyConfiguredClient()) {
            throw new RegistryNotEnabledException("未启用任何注册中心，无法解析 service://" + serviceName);
        }
        Optional<ServiceInstance> chosen = facade.choose(serviceName);
        if (chosen.isPresent()) {
            cache.put(serviceName, chosen.get());
            return chosen.get();
        }
        ServiceInstance cached = cache.getIfPresent(serviceName);
        if (cached != null) {
            log.warn("注册中心未发现 {}，回退到 30s 本地缓存实例 {}:{}", serviceName, cached.getIp(), cached.getPort());
            return cached;
        }
        throw new ServiceInstanceNotFoundException("注册中心未发现服务 " + serviceName + " 的可用实例，且本地缓存已过期");
    }

    private Parsed parseServiceUrl(String url) {
        String rest = url.substring(SERVICE_PROTOCOL.length()); // SVC/path?scheme=xx
        String queryPart = null;
        int qIdx = rest.indexOf('?');
        if (qIdx >= 0) {
            queryPart = rest.substring(qIdx + 1);
            rest = rest.substring(0, qIdx);
        }
        int slashIdx = rest.indexOf('/');
        String svc; String path;
        if (slashIdx < 0) {
            svc = rest; path = "";
        } else {
            svc = rest.substring(0, slashIdx);
            path = rest.substring(slashIdx);
        }
        Parsed p = new Parsed();
        p.serviceName = svc;
        p.path = path;
        if (queryPart != null) {
            for (String kv : queryPart.split("&")) {
                int eq = kv.indexOf('=');
                if (eq < 0) continue;
                String k = kv.substring(0, eq);
                String v = kv.substring(eq + 1);
                if ("scheme".equalsIgnoreCase(k)) p.scheme = v;
            }
        }
        return p;
    }

    private static class Parsed {
        String serviceName;
        String path;
        String scheme; // 可空
    }
}
