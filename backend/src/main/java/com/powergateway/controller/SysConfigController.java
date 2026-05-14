package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.SysConfig;
import com.powergateway.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/api/config")
public class SysConfigController {

    @Autowired private SysConfigService sysConfigService;

    @Operation(summary = "获取全部系统配置")
    @GetMapping("/all")
    public Result<List<SysConfig>> getAll() {
        return Result.success(sysConfigService.getAll());
    }

    @Operation(summary = "批量更新系统配置（仅管理员）")
    @PutMapping
    public Result<Void> update(@RequestBody Map<String, String> updates) {
        sysConfigService.batchUpdate(updates);
        return Result.success();
    }
}
