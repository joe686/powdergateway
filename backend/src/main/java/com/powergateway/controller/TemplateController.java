package com.powergateway.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.common.Result;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.dto.PreviewRequest;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.TemplateQueryRequest;
import com.powergateway.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 转换模板管理接口（M1-2 + M1-5）
 */
@Tag(name = "转换模板管理")
@RestController
@RequestMapping("/api/template")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "保存/更新转换模板（含映射规则）",
               description = "id 为空时新增；id 有值时旧版本 is_latest 置 0，插入新版本（M1-2/M1-5）")
    @PostMapping("/save")
    public Result<Long> save(@RequestBody TemplateSaveRequest req) {
        Long id = templateService.saveTemplate(req);
        return Result.success(id);
    }

    @Operation(summary = "分页查询模板列表",
               description = "支持名称关键词模糊搜索，默认只返回最新版本（M1-5）")
    @GetMapping("/list")
    public Result<Page<ConvertTemplate>> list(TemplateQueryRequest req) {
        return Result.success(templateService.listTemplates(req));
    }

    @Operation(summary = "按 ID 查询模板")
    @GetMapping("/{id}")
    public Result<ConvertTemplate> getById(@PathVariable Long id) {
        return Result.success(templateService.getById(id));
    }

    @Operation(summary = "删除模板（逻辑删除，M1-5）",
               description = "软删除当前版本及同名历史版本")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }

    @Operation(summary = "复制模板（M1-5）",
               description = "name 加 _copy 后缀，version 重置为 1")
    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id) {
        return Result.success(templateService.copyTemplate(id));
    }

    @Operation(summary = "映射预览",
               description = "传入测试报文，按模板 mapping_rule 应用字段映射，返回映射后的字段 Map")
    @PostMapping("/{id}/preview")
    public Result<Map<String, Object>> preview(
            @PathVariable Long id,
            @RequestBody PreviewRequest req) {
        return Result.success(templateService.preview(id, req));
    }
}
