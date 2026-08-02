package com.powergateway.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * PG 功能号 → interfaceId 路由服务(v0.3.1 CR-007 Task 1.3)。
 *
 * <p>Redis 缓存 · TTL 5 min(命中)· 60 s(negative caching 避免高频未命中打 DB)。</p>
 * <p>Redis 不可用时透明 fallback 到 DB · 与 DictMappingService 一致范式。</p>
 */
@Service
public class FunctionIdRouteService {

    private static final Logger log = LoggerFactory.getLogger(FunctionIdRouteService.class);

    static final String CACHE_KEY_PREFIX = "route:pg-function-id:";
    static final int HIT_TTL_SECONDS = 300;
    static final int MISS_TTL_SECONDS = 60;
    static final String CACHE_MISS_SENTINEL = "__NULL__";

    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /**
     * PG 功能号 → interfaceId。
     *
     * @param pgFunctionId PG 内部功能号(如 PG-181345)
     * @return 命中返 interfaceId · 未命中返 null
     */
    public Long lookup(String pgFunctionId) {
        if (pgFunctionId == null || pgFunctionId.trim().isEmpty()) {
            return null;
        }
        String fnId = pgFunctionId.trim();
        String cacheKey = CACHE_KEY_PREFIX + fnId;

        Long cached = tryReadCache(cacheKey);
        if (cached != null) {
            return cached == -1L ? null : cached;
        }

        Long id = queryFromDb(fnId);
        writeCache(cacheKey, id);
        return id;
    }

    /**
     * 失效指定 pgFunctionId 缓存(接口保存/删除时调用)。
     */
    public void invalidate(String pgFunctionId) {
        if (stringRedisTemplate == null || pgFunctionId == null || pgFunctionId.trim().isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + pgFunctionId.trim());
        } catch (Exception e) {
            log.warn("CR-007 · Redis invalidate 失败:{}", e.getMessage());
        }
    }

    /**
     * @return -1L 表示 negative cache 命中(DB 已确认无匹配) · 正 Long 表示命中 interfaceId · null 表示未缓存
     */
    private Long tryReadCache(String cacheKey) {
        if (stringRedisTemplate == null) return null;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached == null) return null;
            if (CACHE_MISS_SENTINEL.equals(cached)) return -1L;
            return Long.parseLong(cached);
        } catch (NumberFormatException e) {
            log.warn("CR-007 · Redis 缓存值格式非法 · key={} · 忽略走 DB", cacheKey);
            return null;
        } catch (Exception e) {
            log.warn("CR-007 · Redis 读缓存失败 · fallback DB · {}", e.getMessage());
            return null;
        }
    }

    private Long queryFromDb(String fnId) {
        LambdaQueryWrapper<InterfaceConfig> q = new LambdaQueryWrapper<>();
        q.eq(InterfaceConfig::getFunctionId, fnId)
                .select(InterfaceConfig::getId)
                .last("LIMIT 1");
        InterfaceConfig cfg = interfaceConfigMapper.selectOne(q);
        return cfg == null ? null : cfg.getId();
    }

    private void writeCache(String cacheKey, Long id) {
        if (stringRedisTemplate == null) return;
        try {
            if (id == null) {
                stringRedisTemplate.opsForValue().set(cacheKey, CACHE_MISS_SENTINEL,
                        MISS_TTL_SECONDS, TimeUnit.SECONDS);
            } else {
                stringRedisTemplate.opsForValue().set(cacheKey, id.toString(),
                        HIT_TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("CR-007 · Redis 写缓存失败:{}", e.getMessage());
        }
    }
}
