package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.ShardConfig;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.model.dto.ShardSaveRequest;
import com.powergateway.service.ShardConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shard")
@Tag(name = "分库分表配置", description = "分片规则 CRUD + 路由预览（M2-8）")
public class ShardConfigController {

    @Autowired
    private ShardConfigService shardConfigService;

    @GetMapping("/list")
    @Operation(summary = "分库分表配置列表")
    public Result<List<ShardConfig>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(shardConfigService.list(name, page, size));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/更新分片配置（id 为空=新增）")
    public Result<Long> save(@RequestBody ShardSaveRequest req) {
        return Result.success(shardConfigService.save(req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分片配置（逻辑删除）")
    public Result<Void> delete(@PathVariable Long id) {
        shardConfigService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "路由预览：传入请求参数，返回路由到的库名和表名")
    public Result<ShardRouteResult> preview(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map
                ? new HashMap<>((Map<String, Object>) body.get("params"))
                : new HashMap<>();
        return Result.success(shardConfigService.preview(id, params));
    }
}
