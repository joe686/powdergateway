package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.RegistryConfig;
import com.powergateway.model.dto.RegistryConfigSaveRequest;
import com.powergateway.service.RegistryConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REG-1 Task 7 · 注册中心配置 Controller
 */
@Tag(name = "REG-1 注册中心配置")
@RestController
@RequestMapping("/api/registry")
@RequiredArgsConstructor
public class RegistryConfigController {

    private final RegistryConfigService registryConfigService;

    @Operation(summary = "列表（密码掩码）")
    @GetMapping("/list")
    public Result<List<RegistryConfig>> list() {
        return Result.success(registryConfigService.list());
    }

    @Operation(summary = "新增或更新")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody RegistryConfigSaveRequest req) {
        return Result.success(registryConfigService.save(req));
    }

    @Operation(summary = "软删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        registryConfigService.delete(id);
        return Result.success();
    }

    @Operation(summary = "测试连通")
    @PostMapping("/{id}/test")
    public Result<RegistryConfigService.TestConnectionResult> testConnection(@PathVariable Long id) {
        return Result.success(registryConfigService.testConnection(id));
    }

    @Operation(summary = "服务发现预览（跨所有已启用 client 聚合）")
    @GetMapping("/discover-preview")
    public Result<List<RegistryConfigService.ServicePreview>> discoverPreview(@RequestParam String serviceName) {
        return Result.success(registryConfigService.discoverPreview(serviceName));
    }

    @Operation(summary = "重新注册本机（供 SystemConfig「重新注册」按钮）")
    @PostMapping("/reregister-self")
    public Result<Boolean> reregisterSelf() {
        return Result.success(registryConfigService.reregisterSelf());
    }
}
