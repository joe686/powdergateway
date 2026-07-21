package com.powergateway;

import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.service.SysConfigService;
import com.powergateway.service.registry.MockRegistryClient;
import com.powergateway.service.registry.RegistryFacade;
import com.powergateway.service.registry.RegistryHeartbeatScheduler;
import com.powergateway.service.registry.SelfRegistrationRunner;
import com.powergateway.service.registry.ServiceInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * REG-1 Task 6 · 自注册 + 心跳 + 告警集成测试
 *
 * SpringBootTest 起完整上下文以拿到真实 SysConfigService（读 registry.* KV）+ PerfAlertMapper。
 * 通过反射注入 MockRegistryClient 到 Facade，避开真实 Nacos/Eureka 依赖。
 */
@SpringBootTest
@ActiveProfiles("test")
class REG1HeartbeatAlertTest {

    @Autowired private RegistryFacade facade;
    @Autowired private SysConfigService sysConfigService;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SelfRegistrationRunner selfRegistrationRunner;

    private MockRegistryClient nacos;

    @BeforeEach
    void setUp() {
        nacos = new MockRegistryClient("nacos", "TestNacos");
        // 反射把 mock client 塞进 Facade —— 必须用可变 List，因为后续 Task 7 有 addClient/clearAll 操作
        List<com.powergateway.service.registry.RegistryClient> mutable = new CopyOnWriteArrayList<>();
        mutable.add(nacos);
        ReflectionTestUtils.setField(facade, "clients", mutable);
    }

    @AfterEach
    void tearDown() {
        // 恢复 Facade 到空状态，避免污染其他测试类共享的 Spring 单例
        ReflectionTestUtils.setField(facade, "clients", new CopyOnWriteArrayList<>());
    }

    // ============ SelfRegistrationRunner ============

    @Test
    void reregisterSelf_已配置注册中心_触发注册() {
        boolean triggered = selfRegistrationRunner.reregisterSelf();
        assertThat(triggered).isTrue();
        assertThat(nacos.registerCalls.get()).isEqualTo(1);
    }

    @Test
    void reregisterSelf_registry未启用_返回false() {
        ReflectionTestUtils.setField(facade, "clients", new CopyOnWriteArrayList<>());
        boolean triggered = selfRegistrationRunner.reregisterSelf();
        assertThat(triggered).isFalse();
    }

    @Test
    void buildSelf_从sys_config读取serviceName() {
        ServiceInstance self = selfRegistrationRunner.buildSelf();
        assertThat(self.getServiceName()).isEqualTo("POWERGATEWAY");
        assertThat(self.getPort()).isGreaterThan(0);
        assertThat(self.getIp()).isNotBlank();
    }

    // ============ RegistryHeartbeatScheduler ============

    @Test
    void tick_心跳成功_不写告警() {
        nacos.setHeartbeatOk(true);
        PerfAlertMapper mockMapper = mock(PerfAlertMapper.class);
        RegistryHeartbeatScheduler scheduler = new RegistryHeartbeatScheduler(facade, sysConfigService, mockMapper);

        scheduler.tick();

        verify(mockMapper, times(0)).insert(any(PerfAlert.class));
    }

    @Test
    void tick_心跳连续失败达阈值_写一次告警() {
        nacos.setHeartbeatOk(false);
        PerfAlertMapper mockMapper = mock(PerfAlertMapper.class);
        RegistryHeartbeatScheduler scheduler = new RegistryHeartbeatScheduler(facade, sysConfigService, mockMapper);

        // 默认 threshold=3，需连续 3 次失败
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        verify(mockMapper, times(1)).insert(any(PerfAlert.class));
    }

    @Test
    void tick_连续失败已告警后_不重复告警() {
        nacos.setHeartbeatOk(false);
        PerfAlertMapper mockMapper = mock(PerfAlertMapper.class);
        RegistryHeartbeatScheduler scheduler = new RegistryHeartbeatScheduler(facade, sysConfigService, mockMapper);

        // 触发告警
        scheduler.tick(); scheduler.tick(); scheduler.tick();
        // 再多几次心跳失败
        scheduler.tick(); scheduler.tick();

        verify(mockMapper, times(1)).insert(any(PerfAlert.class));
    }

    @Test
    void tick_心跳恢复后再次连续失败_可再次告警() {
        PerfAlertMapper mockMapper = mock(PerfAlertMapper.class);
        RegistryHeartbeatScheduler scheduler = new RegistryHeartbeatScheduler(facade, sysConfigService, mockMapper);

        // 先连续失败 → 告警
        nacos.setHeartbeatOk(false);
        scheduler.tick(); scheduler.tick(); scheduler.tick();
        verify(mockMapper, times(1)).insert(any(PerfAlert.class));

        // 恢复健康 → 清空告警标记
        nacos.setHeartbeatOk(true);
        scheduler.tick();

        // 再次连续失败 → 再告警一次
        nacos.setHeartbeatOk(false);
        scheduler.tick(); scheduler.tick(); scheduler.tick();
        verify(mockMapper, times(2)).insert(any(PerfAlert.class));
    }

    @Test
    void tick_registry未启用_直接返回_不查告警() {
        ReflectionTestUtils.setField(facade, "clients", new CopyOnWriteArrayList<>());
        PerfAlertMapper mockMapper = mock(PerfAlertMapper.class);
        RegistryHeartbeatScheduler scheduler = new RegistryHeartbeatScheduler(facade, sysConfigService, mockMapper);

        scheduler.tick();
        verify(mockMapper, times(0)).insert(any(PerfAlert.class));
    }

    // ============ 端到端：告警写真实 DB ============

    @Test
    void 告警写入真实DB_可查询到() {
        nacos.setHeartbeatOk(false);
        RegistryHeartbeatScheduler scheduler = new RegistryHeartbeatScheduler(facade, sysConfigService, perfAlertMapper);

        long before = perfAlertMapper.selectCount(null);
        scheduler.tick(); scheduler.tick(); scheduler.tick();
        long after = perfAlertMapper.selectCount(null);

        assertThat(after - before).isEqualTo(1);
    }
}
