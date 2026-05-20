package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.CacheStatDTO;
import com.powergateway.model.dto.CallStatsDTO;
import com.powergateway.model.dto.CallTrendDTO;
import com.powergateway.model.dto.HomeOverviewDTO;
import com.powergateway.model.dto.InterfaceStatsDTO;
import com.powergateway.model.dto.OpTypeCountDTO;
import com.powergateway.model.dto.StatsSummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class HomeOverviewService {

    @Autowired private InterfaceConfigMapper interfaceConfigMapper;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfStatService perfStatService;
    @Autowired private QueryCacheManager queryCacheManager;

    public HomeOverviewDTO getOverview(String dimension) {
        TimeWindow window = resolveWindow(dimension);

        InterfaceStatsDTO interfaceStats = computeInterfaceStats();
        CallStatsDTO      callStats      = computeCallStats(window);

        CallTrendDTO callTrend = computeCallTrend(dimension);

        List<OpTypeCountDTO> opTypeDist = computeOpTypeDistribution(window);

        return new HomeOverviewDTO(
                interfaceStats,
                callStats,
                callTrend,
                opTypeDist,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private InterfaceStatsDTO computeInterfaceStats() {
        long draft     = interfaceConfigMapper.selectCount(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getStatus, "draft"));
        long published = interfaceConfigMapper.selectCount(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getStatus, "published"));
        long disabled  = interfaceConfigMapper.selectCount(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getStatus, "disabled"));
        return new InterfaceStatsDTO(draft + published + disabled, draft, published, disabled);
    }

    private CallStatsDTO computeCallStats(TimeWindow w) {
        Map<String, Object> stat = perfStatService.statBetween(w.from, w.to);
        long total     = readLong(stat, "total");
        long failCount = readLong(stat, "failCount");
        long avgMs     = readLong(stat, "avgMs");
        BigDecimal successRate = total == 0
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf((total - failCount) * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
        return new CallStatsDTO(total, successRate, avgMs, computeCacheHitRate());
    }

    private List<OpTypeCountDTO> computeOpTypeDistribution(TimeWindow w) {
        List<Map<String, Object>> rows = perfStatMapper.groupByOpType(w.from, w.to);
        List<OpTypeCountDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new OpTypeCountDTO(readStr(row, "opType"), readLong(row, "count")));
        }
        return result;
    }

    private String readStr(Map<String, Object> row, String alias) {
        if (row == null) return "";
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias))
                return e.getValue() != null ? String.valueOf(e.getValue()) : "";
        }
        return "";
    }

    private CallTrendDTO computeCallTrend(String dimension) {
        StatsSummaryDTO summary = perfStatService.summary(dimension);
        return new CallTrendDTO(
                summary.getTimeline()      != null ? summary.getTimeline()      : Collections.emptyList(),
                summary.getSuccessCounts() != null ? summary.getSuccessCounts() : Collections.emptyList(),
                summary.getFailCounts()    != null ? summary.getFailCounts()    : Collections.emptyList()
        );
    }

    private BigDecimal computeCacheHitRate() {
        List<InterfaceConfig> enabled = interfaceConfigMapper.selectList(
                new LambdaQueryWrapper<InterfaceConfig>().eq(InterfaceConfig::getCacheEnabled, 1));
        long totalHit = 0, totalMiss = 0;
        for (InterfaceConfig cfg : enabled) {
            CacheStatDTO s = queryCacheManager.getStats(cfg.getId());
            totalHit  += s.getHitCount();
            totalMiss += s.getMissCount();
        }
        long sum = totalHit + totalMiss;
        if (sum == 0) return null;
        return BigDecimal.valueOf(totalHit * 100.0 / sum).setScale(1, RoundingMode.HALF_UP);
    }

    private TimeWindow resolveWindow(String dimension) {
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime from;
        switch (dimension != null ? dimension : "today") {
            case "week":  from = LocalDate.now().minusDays(6).atStartOfDay(); break;
            case "month": from = LocalDate.now().minusDays(29).atStartOfDay(); break;
            default:      from = LocalDate.now().atStartOfDay();
        }
        return new TimeWindow(from, to);
    }

    private long readLong(Map<String, Object> row, String alias) {
        if (row == null) return 0L;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias) && e.getValue() instanceof Number) {
                return ((Number) e.getValue()).longValue();
            }
        }
        return 0L;
    }

    private static class TimeWindow {
        final LocalDateTime from, to;
        TimeWindow(LocalDateTime from, LocalDateTime to) { this.from = from; this.to = to; }
    }
}
