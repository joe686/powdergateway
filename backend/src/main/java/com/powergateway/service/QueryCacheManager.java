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

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> executeWithCache(Long interfaceId, InterfaceConfig config,
                                                       Map<String, Object> params,
                                                       Supplier<List<Map<String, Object>>> dbQueryFn) {
        if (!Integer.valueOf(1).equals(config.getCacheEnabled())) {
            return dbQueryFn.get();
        }

        String cacheKey = buildCacheKey(interfaceId, config.getCacheKeyTemplate(), params);

        // 1. Caffeine
        Object localVal = localCache.getIfPresent(cacheKey);
        if (localVal != null) {
            incrHit(interfaceId);
            return (List<Map<String, Object>>) localVal;
        }

        // 2. Redis
        if (redisTemplate != null) {
            Object redisVal = redisTemplate.opsForValue().get(cacheKey);
            if (redisVal != null) {
                localCache.put(cacheKey, redisVal);
                incrHit(interfaceId);
                return (List<Map<String, Object>>) redisVal;
            }
        }

        // 3. 分布式锁防击穿
        String lockKey = LOCK_PREFIX + cacheKey;
        boolean locked = false;
        if (redisTemplate != null) {
            locked = Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMillis(3000)));
        }

        List<Map<String, Object>> result;
        try {
            if (!locked && redisTemplate != null) {
                try { Thread.sleep(50); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                Object retryVal = redisTemplate.opsForValue().get(cacheKey);
                if (retryVal != null) {
                    incrHit(interfaceId);
                    return (List<Map<String, Object>>) retryVal;
                }
            }

            result = dbQueryFn.get();

            int ttl = config.getCacheTtlSeconds() != null ? config.getCacheTtlSeconds() : 300;
            if (redisTemplate != null && ttl > 0) {
                redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(ttl));
            }
            localCache.put(cacheKey, result);

        } finally {
            if (locked && redisTemplate != null) {
                redisTemplate.delete(lockKey);
            }
        }

        incrMiss(interfaceId);
        return result;
    }

    public void evict(Long interfaceId) {
        String prefix = KEY_PREFIX + interfaceId + ":";
        localCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        log.info("[M2-10] evict interfaceId={}", interfaceId);
    }

    public void evictAll() {
        localCache.invalidateAll();
        if (redisTemplate != null) {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        log.info("[M2-10] evictAll executed");
    }

    public CacheStatDTO getStats(Long interfaceId) {
        long hit = 0, miss = 0;
        if (redisTemplate != null) {
            Object h = redisTemplate.opsForValue().get(HIT_PREFIX + interfaceId);
            Object m = redisTemplate.opsForValue().get(MISS_PREFIX + interfaceId);
            hit  = h != null ? Long.parseLong(h.toString()) : 0;
            miss = m != null ? Long.parseLong(m.toString()) : 0;
        }
        return new CacheStatDTO(interfaceId, null, null, null, null, hit, miss);
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
