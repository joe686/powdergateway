package com.powergateway.service;

import com.powergateway.dao.SysConfigMapper;
import com.powergateway.event.ConfigChangedEvent;
import com.powergateway.model.SysConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SysConfigService {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @Autowired private SysConfigMapper sysConfigMapper;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        List<SysConfig> all = sysConfigMapper.selectList(null);
        cache.clear();
        all.forEach(c -> {
            if (c.getConfigValue() != null) {
                cache.put(c.getConfigKey(), c.getConfigValue());
            }
        });
    }

    public String getString(String key, String defaultVal) {
        String val = cache.get(key);
        return val != null ? val : defaultVal;
    }

    public int getInt(String key, int defaultVal) {
        String val = cache.get(key);
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val.trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    public boolean getBoolean(String key, boolean defaultVal) {
        String val = cache.get(key);
        if (val == null) return defaultVal;
        return Boolean.parseBoolean(val.trim());
    }

    public List<SysConfig> getAll() {
        return sysConfigMapper.selectList(null);
    }

    public void batchUpdate(Map<String, String> updates) {
        if (updates.isEmpty()) return;
        updates.forEach((key, value) -> {
            SysConfig cfg = sysConfigMapper.selectById(key);
            if (cfg == null) {
                cfg = new SysConfig();
                cfg.setConfigKey(key);
                cfg.setConfigValue(value);
                sysConfigMapper.insert(cfg);
            } else {
                cfg.setConfigValue(value);
                sysConfigMapper.updateById(cfg);
            }
            cache.put(key, value);
        });
        eventPublisher.publishEvent(new ConfigChangedEvent(this, updates));
    }
}
