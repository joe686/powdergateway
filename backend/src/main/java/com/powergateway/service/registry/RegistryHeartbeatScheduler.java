package com.powergateway.service.registry;

import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REG-1 · 心跳调度 + 失败告警
 *
 * 定期触发 {@link RegistryFacade#heartbeatAll()}，读取 statusAll 判断连续失败次数，
 * 超过 sys_config.registry.heartbeat.fail.threshold（默认 3）→ 写 perf_alert（alertType=REGISTRY_HEARTBEAT_FAIL）。
 *
 * 每个 client 只在"从健康转为连续失败"的临界写一次告警；恢复健康后清空标记，下次连续失败重新告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryHeartbeatScheduler {

    private final RegistryFacade facade;
    private final SysConfigService sysConfigService;
    private final PerfAlertMapper perfAlertMapper;

    private final Set<String> alertedClients = ConcurrentHashMap.newKeySet();

    /** 间隔默认 5000ms；可由 sys_config.registry.heartbeat.interval.seconds 影响，但 fixedDelay 只在启动时读一次，运行时改配置需重启。 */
    @Scheduled(fixedDelayString = "${registry.heartbeat.interval.ms:5000}")
    public void tick() {
        if (!facade.hasAnyConfiguredClient()) return;

        try {
            facade.heartbeatAll();
        } catch (Exception e) {
            log.warn("REG-1 心跳调度异常：{}", e.getMessage());
            return;
        }

        int threshold = sysConfigService.getInt("registry.heartbeat.fail.threshold", 3);
        for (RegistryFacade.ClientStatus s : facade.statusAll()) {
            if (!s.isConfigured()) continue;
            String key = s.getType() + "::" + s.getName();
            if (s.getConsecutiveFails() >= threshold) {
                if (alertedClients.add(key)) {
                    writeAlert(s, threshold);
                }
            } else if (s.isHealthy()) {
                alertedClients.remove(key);
            }
        }
    }

    private void writeAlert(RegistryFacade.ClientStatus s, int threshold) {
        try {
            PerfAlert alert = new PerfAlert();
            alert.setAlertType("REGISTRY_HEARTBEAT_FAIL");
            alert.setAlertValue(new BigDecimal(s.getConsecutiveFails()));
            alert.setThreshold(new BigDecimal(threshold));
            alert.setMessage("注册中心 " + s.getName() + "(" + s.getType() + ") 心跳连续失败 "
                    + s.getConsecutiveFails() + " 次");
            alert.setCheckTime(LocalDateTime.now());
            alert.setResolved(0);
            perfAlertMapper.insert(alert);
            log.warn("REG-1 心跳告警已写 perf_alert：{}", alert.getMessage());
        } catch (Exception e) {
            log.warn("REG-1 告警写入失败：{}", e.getMessage());
        }
    }
}
