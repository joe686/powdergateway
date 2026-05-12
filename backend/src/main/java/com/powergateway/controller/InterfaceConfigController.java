package com.powergateway.controller;

import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InterfaceExecuteRequest;
import com.powergateway.model.dto.InterfacePreviewRequest;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 接口配置 REST 层（M2-3 查询接口配置，M2-4 插入接口配置）。
 * 路径前缀 /api/interface
 */
@RestController
@RequestMapping("/api/interface")
@Tag(name = "接口配置管理", description = "可视化接口配置（M2-3 查询 / M2-4 插入）")
public class InterfaceConfigController {

    @Autowired
    private InterfaceConfigService service;

    @PostMapping("/save")
    @Operation(summary = "保存接口配置（新建或更新）")
    public Result<Long> save(@RequestBody InterfaceSaveRequest req) {
        return Result.success(service.save(req));
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "预览接口（执行 SQL 返回前10条结果）")
    public Result<List<Map<String, Object>>> preview(
            @PathVariable Long id,
            @RequestBody InterfacePreviewRequest req) {
        return Result.success(service.preview(id, req.getParams()));
    }

    @PostMapping("/{id}/delete-preview")
    @Operation(summary = "预览待删数据（执行 SELECT 前10条，M2-6）")
    public Result<Map<String, List<Map<String, Object>>>> deletePreview(
            @PathVariable Long id,
            @RequestBody InterfacePreviewRequest req) {
        return Result.success(service.deletePreview(id, req.getParams()));
    }

    @GetMapping("/list")
    @Operation(summary = "查询接口配置列表")
    public Result<List<InterfaceConfig>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(service.list(name, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询接口配置详情")
    public Result<InterfaceConfig> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除接口配置")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布接口（status → published）")
    public Result<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return Result.success();
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用接口（status → disabled）")
    public Result<Void> disable(@PathVariable Long id) {
        service.disable(id);
        return Result.success();
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行接口（INSERT/UPDATE，按 type 分发，M2-4/M2-5，对外开放）")
    public Result<Integer> execute(
            @PathVariable Long id,
            @RequestBody InterfaceExecuteRequest req) {
        com.powergateway.model.InterfaceConfig config = service.getById(id);
        String type = config.getType();

        if ("INSERT".equals(type)) {
            return Result.success(service.executeInsert(id, req.getParams()));
        } else if ("UPDATE".equals(type)) {
            AuditContextHolder.set(new AuditContext()
                    .setInterfaceId(id)
                    .setOpType("UPDATE")
                    .setTargetDb(config.getDbConnectionId() != null
                            ? config.getDbConnectionId().toString() : "unknown"));
            return Result.success(service.executeUpdate(id, req.getParams()));
        } else if ("DELETE".equals(type)) {
            AuditContextHolder.set(new AuditContext()
                    .setInterfaceId(id)
                    .setOpType("DELETE")
                    .setTargetDb(config.getDbConnectionId() != null
                            ? config.getDbConnectionId().toString() : "unknown"));
            return Result.success(service.executeDelete(id, req.getParams()));
        } else {
            throw new BusinessException(400, "不支持的接口类型: " + type);
        }
    }

    @PatchMapping("/{id}/shard-config")
    @Operation(summary = "绑定/解绑分库分表配置（M2-8），shardConfigId=null 表示解绑")
    public Result<Void> bindShardConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Object raw = body.get("shardConfigId");
        Long shardConfigId = null;
        if (raw != null) {
            try {
                shardConfigId = Long.parseLong(raw.toString());
            } catch (NumberFormatException e) {
                throw new com.powergateway.exception.BusinessException(400, "shardConfigId 必须为数字");
            }
        }
        service.bindShardConfig(id, shardConfigId);
        return Result.success();
    }
}
