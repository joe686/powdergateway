package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.dto.StatsSummaryDTO;
import com.powergateway.service.PerfStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 性能统计查询 Controller（BUG-011 修复）
 * 提供 /api/perf-stat/list 和 /api/perf-stat/stats 两个端点
 * 与现有 StatsController（/api/stats/*）共存，复用同一 Service
 */
@RestController
@RequestMapping("/api/perf-stat")
@Tag(name = "性能统计查询", description = "性能统计明细列表与汇总查询（SYS-2，BUG-011 补充端点）")
public class PerfStatController {

    @Autowired
    private PerfStatService perfStatService;

    @GetMapping("/list")
    @Operation(summary = "性能统计明细分页查询")
    public Result<IPage<PerfStatRecord>> list(
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(perfStatService.listRecords(interfaceId, opType, success, startTime, endTime, page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "性能统计汇总（today/week/month 维度）")
    public Result<StatsSummaryDTO> stats(
            @RequestParam(defaultValue = "today") String dimension) {
        return Result.success(perfStatService.summary(dimension));
    }
}
