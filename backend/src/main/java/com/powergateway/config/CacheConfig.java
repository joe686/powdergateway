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
     * 改为直接依赖注入 — Spring 容器中确实存在 RedisConnectionFactory 时才会调用本 Bean，无需条件守护。
     */
    @Bean("cacheRedisTemplate")
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
