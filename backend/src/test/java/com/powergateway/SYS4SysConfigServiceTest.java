package com.powergateway;

import com.powergateway.dao.SysConfigMapper;
import com.powergateway.event.ConfigChangedEvent;
import com.powergateway.model.SysConfig;
import com.powergateway.service.SysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SYS4SysConfigServiceTest {

    @Autowired private SysConfigService sysConfigService;
    @Autowired private SysConfigMapper sysConfigMapper;
    @Autowired private EventCapture eventCapture;

    @TestConfiguration
    static class TestConfig {
        @Bean
        EventCapture eventCapture() { return new EventCapture(); }
    }

    static class EventCapture implements ApplicationListener<ConfigChangedEvent> {
        final List<ConfigChangedEvent> events = new ArrayList<>();
        @Override
        public void onApplicationEvent(ConfigChangedEvent event) { events.add(event); }
        void reset() { events.clear(); }
    }

    @BeforeEach
    void resetCache() {
        sysConfigService.init();
        eventCapture.reset();
    }

    @Test
    void getInt_正常读取() {
        assertThat(sysConfigService.getInt("cache.query.ttl", 0)).isEqualTo(300);
    }

    @Test
    void getInt_key不存在_返回默认值() {
        assertThat(sysConfigService.getInt("no.such.key", 99)).isEqualTo(99);
    }

    @Test
    void getBoolean_返回true() {
        assertThat(sysConfigService.getBoolean("log_menu_enabled", false)).isTrue();
    }

    @Test
    void getAll_返回含新列的列表() {
        List<SysConfig> all = sysConfigService.getAll();
        assertThat(all).isNotEmpty();
        assertThat(all.get(0).getGroupName()).isNotNull();
        assertThat(all.get(0).getValueType()).isNotNull();
    }

    @Test
    void batchUpdate_更新内存缓存() {
        sysConfigService.batchUpdate(Collections.singletonMap("cache.query.ttl", "999"));
        assertThat(sysConfigService.getInt("cache.query.ttl", 0)).isEqualTo(999);
    }

    @Test
    void batchUpdate_写入DB() {
        sysConfigService.batchUpdate(Collections.singletonMap("cache.query.ttl", "888"));
        SysConfig cfg = sysConfigMapper.selectById("cache.query.ttl");
        assertThat(cfg.getConfigValue()).isEqualTo("888");
    }

    @Test
    void batchUpdate_发布ConfigChangedEvent() {
        sysConfigService.batchUpdate(Collections.singletonMap("alert_fail_rate", "10"));
        assertThat(eventCapture.events).hasSize(1);
        assertThat(eventCapture.events.get(0).getChangedEntries())
            .containsEntry("alert_fail_rate", "10");
    }

    @Test
    void batchUpdate_空Map_不发布事件() {
        sysConfigService.batchUpdate(Collections.emptyMap());
        assertThat(eventCapture.events).isEmpty();
    }
}
