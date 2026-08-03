package com.powergateway.testkit.eureka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * v0.3.13 CHG-056 · pg-testkit Eureka 心跳续约调度。
 *
 * <p>Netflix Eureka 默认 lease-expiration-duration-in-seconds=90 · lease-renewal-interval-in-seconds=30。
 * 客户端每 30s 发一次 PUT /apps/{APP}/{instId} 续约 · 否则 Server 90s 后 evict。</p>
 *
 * <p>v0.3.11 CHG-054 pg-testkit 只做 register/deregister · 无 renew · 30-90s 就被 evict。
 * v0.3.13 补齐 · 30s 一次全量 renew · 404 自动 re-register(自愈)。</p>
 *
 * <p>fixedDelay 单位 ms · 支持 sys prop 覆盖:{@code pg-testkit.eureka.heartbeat-interval-ms}(默认 30000)。</p>
 *
 * <p>仅 {@code pg-testkit.eureka.enabled=true} 时生效(未 enabled 时 registered 空 · renewAll 空跑无害)。</p>
 */
@Slf4j
@Component
public class HeartbeatScheduler {

    @Autowired private MultiAppSelfRegister register;

    @Scheduled(fixedDelayString = "${pg-testkit.eureka.heartbeat-interval-ms:30000}", initialDelay = 30000)
    public void heartbeat() {
        if (!register.getConfig().isEnabled()) return;
        int size = register.listRegistered().size();
        if (size == 0) return;
        int ok = register.renewAll();
        if (ok < size) {
            log.warn("[pg-testkit] heartbeat renewAll {}/{} · 部分应用未续约成功", ok, size);
        } else {
            log.debug("[pg-testkit] heartbeat renewAll {}/{} · 全绿", ok, size);
        }
    }
}
