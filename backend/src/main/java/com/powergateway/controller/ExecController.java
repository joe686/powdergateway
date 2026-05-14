package com.powergateway.controller;

import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.aop.PerfStat;
import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ExecRequest;
import com.powergateway.service.InterfaceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外执行入口（M2-7），无需 Sa-Token 登录。
 * 已在 SaTokenConfig 中排除 /api/exec/**。
 */
@RestController
@RequestMapping("/api/exec")
@Tag(name = "接口执行", description = "已发布接口的对外统一执行入口（无需登录）")
public class ExecController {

    @Autowired
    private InterfaceConfigService service;

    @PerfStat
    @PostMapping("/{interfaceId}")
    @Operation(summary = "执行已发布接口（SELECT/INSERT/UPDATE/DELETE）")
    public Result<?> execute(@PathVariable Long interfaceId,
                             @RequestBody(required = false) ExecRequest req) {
        if (req == null) req = new ExecRequest();
        Map<String, Object> params = req.getParams() != null ? req.getParams() : new HashMap<>();

        InterfaceConfig config = service.getById(interfaceId);

        if ("disabled".equals(config.getStatus())) {
            return Result.fail(403, "接口已禁用");
        }
        if (!"published".equals(config.getStatus())) {
            // 兜底：draft、null 或未知状态均视为未发布
            return Result.fail(400, "接口未发布");
        }

        switch (config.getType()) {
            case "SELECT":
                List<Map<String, Object>> rows =
                        service.executeQuery(interfaceId, params, req.getPage(), req.getPageSize());
                return Result.success(rows);
            case "INSERT":
                return Result.success(service.executeInsert(interfaceId, params));
            case "UPDATE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(interfaceId)
                        .setOpType("UPDATE")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return Result.success(service.executeUpdate(interfaceId, params));
            case "DELETE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(interfaceId)
                        .setOpType("DELETE")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return Result.success(service.executeDelete(interfaceId, params));
            default:
                throw new BusinessException(400, "不支持的接口类型: " + config.getType());
        }
    }
}
