package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.ChannelConfig;
import com.powergateway.model.dto.ChannelSaveRequest;
import com.powergateway.aop.SysLogRecord;
import com.powergateway.service.ChannelConfigService;
import com.powergateway.utils.FormatType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * M1-4 渠道模板管理接口
 */
@Tag(name = "渠道模板管理")
@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
public class ChannelConfigController {

    private final ChannelConfigService channelConfigService;

    @Operation(summary = "查询所有渠道配置列表")
    @GetMapping("/list")
    public Result<List<ChannelConfig>> list() {
        return Result.success(channelConfigService.listChannels());
    }

    @Operation(summary = "按 id 查询渠道配置")
    @GetMapping("/{id}")
    public Result<ChannelConfig> getById(@PathVariable Long id) {
        return Result.success(channelConfigService.getById(id));
    }

    @Operation(summary = "新增或更新渠道配置",
               description = "id 为空时新增；id 有值时更新。保存后刷新 Redis 缓存。")
    @SysLogRecord(module = "渠道配置", action = "保存渠道")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody ChannelSaveRequest req) {
        return Result.success(channelConfigService.saveChannel(req));
    }

    @Operation(summary = "删除渠道配置（逻辑删除）")
    @SysLogRecord(module = "渠道配置", action = "删除渠道")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelConfigService.deleteChannel(id);
        return Result.success();
    }

    @Operation(summary = "渠道自动路由（运行时匹配）",
               description = "解析报文中的识别字段，自动匹配渠道并返回关联模板 id。未命中返回 null。")
    @PostMapping("/match")
    public Result<Long> match(
            @Parameter(description = "源报文字符串") @RequestParam String message,
            @Parameter(description = "报文格式：JSON / XML / CSV / FORM_DATA") @RequestParam String format) {
        Long templateId = channelConfigService.match(message, FormatType.parse(format));
        return Result.success(templateId);
    }
}
