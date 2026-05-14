package com.powergateway.service;

import com.powergateway.event.ConfigChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class ConfigChangeListener {

    @Autowired
    private ObjectProvider<StringRedisTemplate> redisProvider;

    @EventListener
    public void onConfigChanged(ConfigChangedEvent event) {
        if (!event.getChangedEntries().containsKey("cache.template.ttl")) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        Set<String> keys = redis.keys("template:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
            log.info("cache.template.ttl changed, evicted {} template cache entries", keys.size());
        }
    }
}
