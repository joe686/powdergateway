package com.powergateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * cacheRedisTemplate 用于 M2-10 双层缓存。
     * 历史事故（CHG-012 E2E-7）：曾用 @ConditionalOnBean(RedisConnectionFactory.class)，
     * backend 启动顺序敏感时判定 false → Bean 不创建 → 生产缓存永远 miss，故去掉。
     * 生产环境 RedisConnectionFactory 由 Spring Boot Redis 自动配置提供，一定存在。
     * 测试环境（@ActiveProfiles("test")）Redis 自动配置被 application-test.yml 排除，
     * RedisConnectionFactory 不存在，用 @Profile("!test") 让此 Bean 在测试环境不注册
     * （比 @ConditionalOnBean 更精确，不受启动顺序影响）。
     * QueryCacheManager 中 redisTemplate 用 @Autowired(required = false)，允许 null。
     */
    @Bean("cacheRedisTemplate")
    @Profile("!test")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "pg.cache.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisTemplate<String, Object> cacheRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
