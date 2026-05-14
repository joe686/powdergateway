package com.powergateway.job;

import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.SysConfig;
import com.powergateway.service.PerfStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 接口执行性能告警定时任务（SYS-2）
 *
 * 每分钟检查最近1分钟的 perf_stat 数据：
 *   - 失败率超过 alert_fail_rate（sys_config，默认5%）时写 FAIL_RATE 告警记录
 *   - 平均响应时间超过 alert_response_ms（sys_config，默认1000ms）时写 AVG_RESPONSE 告警记录
 *   - checkAndAlert() 为公开方法，供测试直接触发，不依赖定时器
 */
@Component
public class PerfAlertJob {

    private static final String KEY_FAIL_RATE   = "alert_fail_rate";
    private static final String KEY_RESPONSE_MS  = "alert_response_ms";
    private static final double DEFAULT_FAIL_RATE   = 5.0;
    private static final int    DEFAULT_RESPONSE_MS  = 1000;

    @Autowired private PerfStatService perfStatService;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void scheduled() {
        checkAndAlert();
    }

    /** 公开方法，供测试直接触发检查 */
    public void checkAndAlert() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusMinutes(1);

        Map<String, Object> stat = perfStatService.statBetween(from, to);
        // statBetween 始终返回单行 COUNT(*) 结果，理论上不为 null；此处作为 PerfStatService 接口变更时的安全兜底
        if (stat == null) return;

        Object totalObj = getVal(stat, "total");
        if (totalObj == null) return;
        long total = ((Number) totalObj).longValue();
        if (total == 0) return;

        double failRate    = readDouble(KEY_FAIL_RATE, DEFAULT_FAIL_RATE);
        int    responseMs  = readInt(KEY_RESPONSE_MS, DEFAULT_RESPONSE_MS);

        Object failObj = getVal(stat, "failCount");
        Object avgObj  = getVal(stat, "avgMs");
        long failCount  = failObj instanceof Number ? ((Number) failObj).longValue() : 0L;
        double avgMs    = avgObj  instanceof Number ? ((Number) avgObj).doubleValue() : 0.0;

        double actualFailRate = (double) failCount / total * 100;

        if (actualFailRate > failRate) {
            insertAlert("FAIL_RATE",
                    BigDecimal.valueOf(actualFailRate).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(failRate),
                    String.format("失败率 %.2f%% 超过阈值 %.2f%%", actualFailRate, failRate));
        }
        if (avgMs > responseMs) {
            insertAlert("AVG_RESPONSE",
                    BigDecimal.valueOf(avgMs).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(responseMs),
                    String.format("平均响应时间 %.0fms 超过阈值 %dms", avgMs, responseMs));
        }
    }

    private void insertAlert(String type, BigDecimal value, BigDecimal threshold, String message) {
        PerfAlert alert = new PerfAlert();
        alert.setAlertType(type);
        alert.setAlertValue(value);
        alert.setThreshold(threshold);
        alert.setMessage(message);
        alert.setCheckTime(LocalDateTime.now());
        alert.setResolved(0);
        perfAlertMapper.insert(alert);
    }

    private double readDouble(String key, double defaultVal) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg == null || cfg.getConfigValue() == null) return defaultVal;
        try { return Double.parseDouble(cfg.getConfigValue().trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private int readInt(String key, int defaultVal) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg == null || cfg.getConfigValue() == null) return defaultVal;
        try { return Integer.parseInt(cfg.getConfigValue().trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private Object getVal(Map<String, Object> map, String alias) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias)) return e.getValue();
        }
        return null;
    }
}
