package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.SysConfig;
import com.powergateway.model.dto.AlertConfigRequest;
import com.powergateway.model.dto.StatsSummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口执行性能统计异步写入与查询服务（SYS-2）
 *
 * 实现机制：
 *   - LinkedBlockingQueue 作为缓冲区（容量 10000），防止统计写入阻塞业务主线程
 *   - 单个后台守护线程持续消费队列，逐条写入 perf_stat 表
 *   - 队列满时直接丢弃（offer 非阻塞），保证业务不受影响
 *   - flushForTest() 仅供测试使用，同步消费队列剩余项目以便断言
 */
@Service
public class PerfStatService {

    private static final int QUEUE_CAPACITY = 10000;
    private final LinkedBlockingQueue<PerfStatRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    /**
     * 尚未持久化完毕的记录总数（入队 +1，写入完成 -1）。
     * 用于 flushForTest() 精确等待：当计数归零时，说明所有记录（含守护线程正在写的那条）都已落库。
     */
    private final AtomicInteger pendingCount = new AtomicInteger(0);

    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @PostConstruct
    public void startConsumer() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PerfStatRecord record = queue.take();
                    try {
                        writeSafely(record);
                    } finally {
                        pendingCount.decrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "perf-stat-consumer");
        t.setDaemon(true);
        t.start();
    }

    public void enqueue(PerfStatRecord record) {
        pendingCount.incrementAndGet();
        if (!queue.offer(record)) {
            // 队列满时丢弃，同步撤销计数
            pendingCount.decrementAndGet();
        }
    }

    /** 仅供测试使用，严禁在生产代码中调用 */
    public void flushForTest() {
        // 等待 pendingCount 归零：当计数为 0 时，队列为空且守护线程已完成当前写入
        int waitMs = 0;
        while (pendingCount.get() > 0 && waitMs < 2000) {
            try { Thread.sleep(10); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            waitMs += 10;
        }
        // 补充：直接排干队列中残余（理论上此时应为空，作为兜底）
        List<PerfStatRecord> pending = new ArrayList<>();
        queue.drainTo(pending);
        pending.forEach(r -> {
            pendingCount.decrementAndGet();
            writeSafely(r);
        });
    }

    public StatsSummaryDTO summary(String dimension) {
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime from;
        boolean byHour;
        switch (dimension != null ? dimension : "today") {
            case "week":
                from = LocalDate.now().minusDays(6).atStartOfDay();
                byHour = false;
                break;
            case "month":
                from = LocalDate.now().minusDays(29).atStartOfDay();
                byHour = false;
                break;
            default:
                from = LocalDate.now().atStartOfDay();
                byHour = true;
        }

        List<Map<String, Object>> rows = byHour
                ? perfStatMapper.groupByHour(from, to)
                : perfStatMapper.groupByDay(from, to);

        List<String> timeline = new ArrayList<>();
        List<Long> successCounts = new ArrayList<>();
        List<Long> failCounts = new ArrayList<>();
        List<Long> avgCostMs = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            timeline.add(getStr(row, "label"));
            successCounts.add(getLong(row, "successCount"));
            failCounts.add(getLong(row, "failCount"));
            avgCostMs.add(getLong(row, "avgCostMs"));
        }

        StatsSummaryDTO dto = new StatsSummaryDTO();
        dto.setTimeline(timeline);
        dto.setSuccessCounts(successCounts);
        dto.setFailCounts(failCounts);
        dto.setAvgCostMs(avgCostMs);
        return dto;
    }

    public IPage<PerfAlert> listAlerts(int page, int pageSize) {
        return perfAlertMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<PerfAlert>().orderByDesc(PerfAlert::getCheckTime));
    }

    public Map<String, Object> statBetween(LocalDateTime from, LocalDateTime to) {
        return perfStatMapper.statBetween(from, to);
    }

    public void updateAlertConfig(AlertConfigRequest req) {
        if (req.getFailRate() != null) {
            upsertConfig("alert_fail_rate", String.format("%.1f", req.getFailRate()));
        }
        if (req.getResponseMs() != null) {
            upsertConfig("alert_response_ms", String.valueOf(req.getResponseMs()));
        }
    }

    private void upsertConfig(String key, String value) {
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
    }

    private void writeSafely(PerfStatRecord record) {
        try {
            perfStatMapper.insert(record);
        } catch (Exception ignored) {
            // 统计写入失败静默处理，不影响业务主链路
        }
    }

    /** 大小写兼容的 Map 字符串读取（H2 返回大写别名，MySQL 返回小写） */
    private String getStr(Map<String, Object> row, String alias) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias))
                return e.getValue() != null ? String.valueOf(e.getValue()) : "";
        }
        return "";
    }

    private long getLong(Map<String, Object> row, String alias) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias) && e.getValue() instanceof Number)
                return ((Number) e.getValue()).longValue();
        }
        return 0L;
    }
}
