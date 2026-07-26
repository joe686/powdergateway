package com.powergateway.controller;

import com.powergateway.aop.SysLogRecord;
import com.powergateway.common.Result;
import com.powergateway.model.dto.DictMappingImportResult;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import com.powergateway.service.DictMappingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 字典映射管理 REST 层（FN-12 · v0.2.0 ①）
 * 路径前缀 /api/dict-mapping
 */
@Tag(name = "字典映射管理", description = "FN-12 · v0.2.0 ① · 跨系统字典值映射 CRUD + Excel + Lookup")
@RestController
@RequestMapping("/api/dict-mapping")
public class DictMappingController {

    @Autowired
    private DictMappingService dictMappingService;

    @Operation(summary = "列表 · 支持按 system/dictKey/direction/status 筛选")
    @GetMapping("/list")
    public Result<List<DictMappingVO>> list(
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String dictKey,
            @RequestParam(required = false) Integer direction,
            @RequestParam(required = false) Integer status) {
        return Result.success(dictMappingService.list(systemCode, dictKey, direction, status));
    }

    @Operation(summary = "系统代号 distinct 列表（前端下拉）")
    @GetMapping("/systems")
    public Result<List<String>> systems() {
        return Result.success(dictMappingService.getSystems());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public Result<DictMappingVO> get(@PathVariable Long id) {
        return Result.success(dictMappingService.getById(id));
    }

    @Operation(summary = "新增（bidirectional=true 后端拆两条）")
    @SysLogRecord(module = "字典管理", action = "保存字典")
    @PostMapping
    public Result<List<Long>> save(@Valid @RequestBody DictMappingSaveRequest req) {
        return Result.success(dictMappingService.save(req));
    }

    @Operation(summary = "编辑（不允许修改 direction/sourceValue）")
    @SysLogRecord(module = "字典管理", action = "更新字典")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DictMappingSaveRequest req) {
        dictMappingService.update(id, req);
        return Result.success();
    }

    @Operation(summary = "删除（软删 + Redis 精准失效）")
    @SysLogRecord(module = "字典管理", action = "删除字典")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dictMappingService.delete(id);
        return Result.success();
    }

    @Operation(summary = "Excel 导入")
    @SysLogRecord(module = "字典管理", action = "导入字典")
    @PostMapping("/import")
    public Result<DictMappingImportResult> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.success(dictMappingService.importExcel(file));
    }

    @Operation(summary = "Excel 导出")
    @SysLogRecord(module = "字典管理", action = "导出字典")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String dictKey,
            @RequestParam(required = false) Integer direction,
            @RequestParam(required = false) Integer status) throws Exception {
        byte[] data = dictMappingService.exportExcel(systemCode, dictKey, direction, status);
        return InterfaceConfigController.excelResponse(
                data, "字典映射_" + InterfaceConfigController.tsSuffix() + ".xlsx");
    }

    @Operation(summary = "Lookup（Processor 内部用）")
    @PostMapping("/lookup")
    public Result<DictMappingLookupResult> lookup(
            @RequestParam String system,
            @RequestParam String dictKey,
            @RequestParam Integer direction,
            @RequestParam String source) {
        DictMappingLookupResult r = dictMappingService.lookup(system, dictKey, direction, source);
        if (r == null) return Result.fail(404, "字典未定义映射");
        return Result.success(r);
    }
}
