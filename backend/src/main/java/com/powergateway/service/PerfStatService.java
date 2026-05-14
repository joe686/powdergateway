package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
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

@Service
public class PerfStatService {

    private static final int QUEUE_CAPACITY = 10000;
    private final LinkedBlockingQueue<PerfStatRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;

    @PostConstruct
    public void startConsumer() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    writeSafely(queue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "perf-stat-consumer");
        t.setDaemon(true);
        t.start();
    }

    public void enqueue(PerfStatRecord record) {
        queue.offer(record);
    }

    /** 仅供测试使用，严禁在生产代码中调用 */
    public void flushForTest() {
        List<PerfStatRecord> pending = new ArrayList<>();
        queue.drainTo(pending);
        pending.forEach(this::writeSafely);
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

    private void writeSafely(PerfStatRecord record) {
        try {
            perfStatMapper.insert(record);
        } catch (Exception ignored) {
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
