package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * SQL 审计日志查询 Controller（BUG-011 修复）
 * 提供 /api/sql-audit-log/list 端点
 * 与现有 SysLogController 中的 /api/log/audit/list 共存，复用同一 Service
 */
@RestController
@RequestMapping("/api/sql-audit-log")
@Tag(name = "SQL 审计日志查询", description = "SQL 审计日志分页查询（M2-9，BUG-011 补充端点）")
public class SqlAuditLogController {

    @Autowired
    private SysLogService sysLogService;

    @GetMapping("/list")
    @Operation(summary = "SQL 审计日志分页查询")
    public Result<IPage<SqlAuditLog>> list(
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.listAuditLogs(opType, result, startTime, endTime, page, size));
    }
}
