package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.SysConfig;
import com.powergateway.model.dto.AlertConfigRequest;
import com.powergateway.model.dto.StatsSummaryDTO;
import com.powergateway.service.PerfStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "性能统计", description = "接口执行统计、告警查询与阈值配置（SYS-2）")
public class StatsController {

    @Autowired private PerfStatService perfStatService;
    @Autowired private SysConfigMapper sysConfigMapper;

    @GetMapping("/summary")
    @Operation(summary = "获取图表聚合数据（today/week/month）")
    public Result<StatsSummaryDTO> summary(
            @RequestParam(defaultValue = "today") String dimension) {
        return Result.success(perfStatService.summary(dimension));
    }

    @GetMapping("/alerts")
    @Operation(summary = "分页查询告警记录")
    public Result<IPage<PerfAlert>> alerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(perfStatService.listAlerts(page, pageSize));
    }

    @PutMapping("/alert-config")
    @Operation(summary = "更新告警阈值配置")
    public Result<Void> updateAlertConfig(@RequestBody AlertConfigRequest req) {
        if (req.getFailRate() != null) {
            upsertConfig("alert_fail_rate", String.valueOf(req.getFailRate()));
        }
        if (req.getResponseMs() != null) {
            upsertConfig("alert_response_ms", String.valueOf(req.getResponseMs()));
        }
        return Result.success();
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
}
