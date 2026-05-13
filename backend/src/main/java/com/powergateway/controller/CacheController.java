package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.CacheConfigRequest;
import com.powergateway.model.dto.CacheStatDTO;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.QueryCacheManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cache")
@Tag(name = "缓存管理", description = "SELECT 接口双层缓存管理（M2-10）")
public class CacheController {

    @Autowired private QueryCacheManager cacheManager;
    @Autowired private InterfaceConfigService interfaceConfigService;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    @GetMapping("/list")
    @Operation(summary = "查询所有 SELECT 接口缓存配置与命中统计")
    public Result<List<CacheStatDTO>> list() {
        List<InterfaceConfig> interfaces = interfaceConfigService.listSelectInterfaces();
        List<CacheStatDTO> result = interfaces.stream().map(config -> {
            CacheStatDTO stats = cacheManager.getStats(config.getId());
            stats.setInterfaceName(config.getName());
            stats.setCacheEnabled(config.getCacheEnabled() != null ? config.getCacheEnabled() : 0);
            stats.setCacheTtlSeconds(config.getCacheTtlSeconds() != null ? config.getCacheTtlSeconds() : 300);
            stats.setCacheKeyTemplate(config.getCacheKeyTemplate() != null ? config.getCacheKeyTemplate() : "");
            return stats;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @PutMapping("/{interfaceId}/config")
    @Operation(summary = "更新接口缓存配置，自动清除旧缓存")
    public Result<?> updateConfig(@PathVariable Long interfaceId,
                                   @RequestBody CacheConfigRequest req) {
        InterfaceConfig config = interfaceConfigMapper.selectById(interfaceId);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        if (!"SELECT".equals(config.getType())) {
            throw new BusinessException(400, "仅 SELECT 类型接口支持缓存配置");
        }
        InterfaceConfig update = new InterfaceConfig();
        update.setId(interfaceId);
        if (req.getCacheEnabled() != null)    update.setCacheEnabled(req.getCacheEnabled());
        if (req.getCacheTtlSeconds() != null) update.setCacheTtlSeconds(req.getCacheTtlSeconds());
        if (req.getCacheKeyTemplate() != null) update.setCacheKeyTemplate(req.getCacheKeyTemplate());
        interfaceConfigMapper.updateById(update);
        cacheManager.evict(interfaceId);
        return Result.success();
    }

    @SysLogRecord(module = "缓存管理", action = "清除缓存")
    @DeleteMapping("/{interfaceId}")
    @Operation(summary = "清除指定接口的 Caffeine + Redis 缓存")
    public Result<?> evict(@PathVariable Long interfaceId) {
        cacheManager.evict(interfaceId);
        return Result.success();
    }

    @PostMapping("/{interfaceId}/refresh")
    @Operation(summary = "清除后用指定参数预热缓存（接口须已发布且开启缓存）")
    public Result<?> refresh(@PathVariable Long interfaceId,
                              @RequestBody(required = false) Map<String, Object> params) {
        InterfaceConfig config = interfaceConfigMapper.selectById(interfaceId);
        if (config == null) throw new BusinessException(404, "接口配置不存在");

        cacheManager.evict(interfaceId);

        if ("published".equals(config.getStatus()) && Integer.valueOf(1).equals(config.getCacheEnabled())) {
            Map<String, Object> safeParams = params != null ? params : new HashMap<>();
            interfaceConfigService.executeQuery(interfaceId, safeParams, null, null);
        }
        return Result.success();
    }

    @SysLogRecord(module = "缓存管理", action = "清除全部缓存")
    @DeleteMapping("/all")
    @Operation(summary = "一键清除所有接口缓存")
    public Result<?> evictAll() {
        cacheManager.evictAll();
        return Result.success();
    }

    @GetMapping("/{interfaceId}/stats")
    @Operation(summary = "查询单个接口命中/未命中统计")
    public Result<CacheStatDTO> stats(@PathVariable Long interfaceId) {
        InterfaceConfig config = interfaceConfigMapper.selectById(interfaceId);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        CacheStatDTO stats = cacheManager.getStats(interfaceId);
        stats.setInterfaceName(config.getName());
        stats.setCacheEnabled(config.getCacheEnabled());
        stats.setCacheTtlSeconds(config.getCacheTtlSeconds());
        stats.setCacheKeyTemplate(config.getCacheKeyTemplate());
        return Result.success(stats);
    }
}
