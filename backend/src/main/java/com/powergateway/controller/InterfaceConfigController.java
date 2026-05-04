package com.powergateway.controller;

import com.powergateway.common.Result;
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

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行 INSERT 接口（M2-4）")
    public Result<Integer> execute(
            @PathVariable Long id,
            @RequestBody InterfaceExecuteRequest req) {
        return Result.success(service.executeInsert(id, req.getParams()));
    }
}
