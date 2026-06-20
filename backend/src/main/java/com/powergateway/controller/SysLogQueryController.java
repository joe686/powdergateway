package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作日志查询 Controller（BUG-011 修复）
 * 提供 /api/sys-log/list 端点
 * 与现有 SysLogController（/api/log/list）共存，复用同一 Service
 */
@RestController
@RequestMapping("/api/sys-log")
@Tag(name = "操作日志查询", description = "操作日志分页查询（SYS-1，BUG-011 补充端点）")
public class SysLogQueryController {

    @Autowired
    private SysLogService sysLogService;

    @GetMapping("/list")
    @Operation(summary = "操作日志分页查询")
    public Result<IPage<SysLog>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.list(operator, module, level, startTime, endTime, page, size));
    }
}
