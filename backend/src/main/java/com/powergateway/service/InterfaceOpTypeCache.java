package com.powergateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 接口 opType 本地缓存（BUG-009 修复 / OPT-15 配置热缓存）
 *
 * 作用：避免 PerfStatAspect 在主线程同步查询 interface_config 表获取 opType，
 *      改为从 Caffeine 本地缓存读取，缓存未命中时异步回源并回填。
 *
 * 缓存生命周期：
 *   - 接口发布（publish）：预加载 opType 到缓存
 *   - 接口禁用/删除：清除缓存
 *   - TTL 1 小时：防止长期未访问的接口配置变更后缓存不一致
 */
@Component
public class InterfaceOpTypeCache {

    private static final Logger log = LoggerFactory.getLogger(InterfaceOpTypeCache.class);

    /** 缓存未命中时的占位符，避免反复回源查询不存在的接口 */
    private static final String MISS_PLACEHOLDER = "__MISS__";

    @Autowired
    private InterfaceConfigMapper interfaceConfigMapper;

    private Cache<Long, String> opTypeCache;

    @PostConstruct
    public void init() {
        opTypeCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();
    }

    /**
     * 从缓存读取接口 opType，缓存未命中时同步回源查询并回填（首次调用场景）。
     * 注意：此处回源查询仅在缓存未命中时发生，相比原 PerfStatAspect 每次都查询 DB，
     *      已大幅减少主线程 DB 开销。对于高频接口，缓存命中率接近 100%。
     */
    public String getOpType(Long interfaceId) {
        if (interfaceId == null) {
            return "UNKNOWN";
        }
        String cached = opTypeCache.getIfPresent(interfaceId);
        if (cached != null) {
            return MISS_PLACEHOLDER.equals(cached) ? "UNKNOWN" : cached;
        }
        // 缓存未命中：回源查询并回填
        return loadFromDb(interfaceId);
    }

    /**
     * 同步回源查询并回填缓存
     */
    private String loadFromDb(Long interfaceId) {
        try {
            InterfaceConfig config = interfaceConfigMapper.selectById(interfaceId);
            String opType = config != null && config.getType() != null ? config.getType() : "UNKNOWN";
            opTypeCache.put(interfaceId, opType);
            return opType;
        } catch (Exception e) {
            log.warn("[InterfaceOpTypeCache] 回源查询 interfaceId={} 失败: {}", interfaceId, e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * 预加载接口 opType 到缓存（接口发布时调用）
     */
    public void preload(Long interfaceId) {
        if (interfaceId == null) return;
        try {
            InterfaceConfig config = interfaceConfigMapper.selectById(interfaceId);
            String opType = config != null && config.getType() != null ? config.getType() : "UNKNOWN";
            opTypeCache.put(interfaceId, opType);
            log.debug("[InterfaceOpTypeCache] 预加载 interfaceId={} opType={}", interfaceId, opType);
        } catch (Exception e) {
            log.warn("[InterfaceOpTypeCache] 预加载 interfaceId={} 失败: {}", interfaceId, e.getMessage());
        }
    }

    /**
     * 清除指定接口的缓存（接口禁用/删除/更新时调用）
     */
    public void evict(Long interfaceId) {
        if (interfaceId == null) return;
        opTypeCache.invalidate(interfaceId);
    }
}
