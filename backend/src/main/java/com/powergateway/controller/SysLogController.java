package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.model.SysLog;
import com.powergateway.model.SysLogHistory;
import com.powergateway.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/log")
@Tag(name = "日志管理", description = "操作日志查询/导出/归档历史查询（SYS-1）")
public class SysLogController {

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
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.list(operator, module, level, startTime, endTime, page, size));
    }

    @GetMapping("/history/list")
    @Operation(summary = "历史操作日志分页查询（CHG-006）")
    public Result<IPage<SysLogHistory>> historyList(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.listHistory(operator, module, level, startTime, endTime, page, size));
    }

    @GetMapping("/export")
    @Operation(summary = "导出操作日志 Excel")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        byte[] data = sysLogService.exportExcel(operator, module, level, startTime, endTime);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "sys_log.xlsx");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @GetMapping("/audit/list")
    @Operation(summary = "SQL 审计日志分页查询")
    public Result<IPage<SqlAuditLog>> auditList(
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.listAuditLogs(opType, result, startTime, endTime, page, size));
    }
}
