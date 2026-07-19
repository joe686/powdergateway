package com.powergateway.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Optional;
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
     * 之前用 @ConditionalOnBean 在 backend 启动顺序敏感时会判定 RedisConnectionFactory 还未注册，
     * 导致 Bean 不创建、QueryCacheManager.redisTemplate 注入 null、hit/miss 计数器永远是 0。
     * 现在加 @ConditionalOnBean(RedisConnectionFactory.class)：
     * - 测试环境（@ActiveProfiles("test")）中 Redis 自动配置被排除，RedisConnectionFactory 不存在，此 Bean 不创建
     * - 生产环境中 Redis 自动配置活跃，RedisConnectionFactory 存在，此 Bean 正常创建
     * - QueryCacheManager 中 redisTemplate 用 @Autowired(required = false) 修饰，无论是否创建都不会报错
     */
    @Bean("cacheRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
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
