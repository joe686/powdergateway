package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.SysConfig;
import com.powergateway.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置查询 Controller（BUG-011 修复）
 * 提供 /api/sys-config/list 端点
 * 与现有 SysConfigController（/api/config/all）共存，复用同一 Service
 */
@RestController
@RequestMapping("/api/sys-config")
@Tag(name = "系统配置查询", description = "系统配置列表查询（SYS-4，BUG-011 补充端点）")
public class SysConfigQueryController {

    @Autowired
    private SysConfigService sysConfigService;

    @GetMapping("/list")
    @Operation(summary = "系统配置列表查询")
    public Result<List<SysConfig>> list(
            @RequestParam(required = false) String groupName) {
        List<SysConfig> all = sysConfigService.getAll();
        if (groupName != null && !groupName.trim().isEmpty()) {
            all.removeIf(c -> !groupName.equals(c.getGroupName()));
        }
        return Result.success(all);
    }
}
