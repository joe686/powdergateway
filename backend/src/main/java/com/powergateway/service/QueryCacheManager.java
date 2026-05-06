package com.powergateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.CacheStatDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QueryCacheManager {

    static final String KEY_PREFIX  = "query_cache:";
    static final String LOCK_PREFIX = "lock:query_cache:";
    static final String HIT_PREFIX  = "cache_hit:";
    static final String MISS_PREFIX = "cache_miss:";

    @Autowired
    private Cache<String, Object> localCache;

    @Autowired(required = false)
    @Qualifier("cacheRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    public String buildCacheKey(Long interfaceId, String keyTemplate, Map<String, Object> params) {
        String paramStr;
        if (keyTemplate != null && !keyTemplate.isEmpty()) {
            paramStr = replacePlaceholders(keyTemplate, params);
        } else {
            paramStr = params == null ? "" : params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
        }
        return KEY_PREFIX + interfaceId + ":" + paramStr;
    }

    private String replacePlaceholders(String template, Map<String, Object> params) {
        String result = template;
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                        entry.getValue() == null ? "" : entry.getValue().toString());
            }
        }
        return result;
    }

    public List<Map<String, Object>> executeWithCache(Long interfaceId, InterfaceConfig config,
                                                       Map<String, Object> params,
                                                       Supplier<List<Map<String, Object>>> dbQueryFn) {
        // Task 4 实现
        return dbQueryFn.get();
    }

    public void evict(Long interfaceId) {
        // Task 5 实现
    }

    public void evictAll() {
        // Task 5 实现
    }

    public CacheStatDTO getStats(Long interfaceId) {
        // Task 5 实现
        return new CacheStatDTO(interfaceId, null, null, null, null, 0L, 0L);
    }

    void incrHit(Long interfaceId) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().increment(HIT_PREFIX + interfaceId);
        }
    }

    void incrMiss(Long interfaceId) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().increment(MISS_PREFIX + interfaceId);
        }
    }
}
