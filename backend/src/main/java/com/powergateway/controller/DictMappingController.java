package com.powergateway.controller;

import com.powergateway.aop.SysLogRecord;
import com.powergateway.common.Result;
import com.powergateway.model.dto.DictMappingBatchSaveRequest;
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
 * 字典映射管理 REST 层（FN-12 · v0.2.0 ① · v0.2.5 CR-004 加 scope 分场景 + batch API）
 * 路径前缀 /api/dict-mapping
 * <p>
 * scope 语义:
 *   1 = 接口转换 M1 侧(/transform/dict 菜单)
 *   2 = 可视化接口 M2 侧(/interface/dict 菜单)
 *   3 = 通用共享(两侧可见 · Processor lookup 兜底)
 */
@Tag(name = "字典映射管理", description = "FN-12 · v0.2.5 · 跨系统字典值映射 CRUD + Excel + Lookup + batch + scope")
@RestController
@RequestMapping("/api/dict-mapping")
public class DictMappingController {

    @Autowired
    private DictMappingService dictMappingService;

    @Operation(summary = "列表 · 支持按 scope/system/dictKey/direction/status 筛选")
    @GetMapping("/list")
    public Result<List<DictMappingVO>> list(
            @RequestParam(required = false) Integer scope,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String dictKey,
            @RequestParam(required = false) Integer direction,
            @RequestParam(required = false) Integer status) {
        return Result.success(dictMappingService.list(scope, systemCode, dictKey, direction, status));
    }

    @Operation(summary = "系统代号 distinct 列表（按 scope 过滤 · 前端下拉）")
    @GetMapping("/systems")
    public Result<List<String>> systems(@RequestParam(required = false) Integer scope) {
        return Result.success(dictMappingService.getSystems(scope));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public Result<DictMappingVO> get(@PathVariable Long id) {
        return Result.success(dictMappingService.getById(id));
    }

    @Operation(summary = "新增（bidirectional=true 后端拆两条 · scope 默认 3 共享）")
    @SysLogRecord(module = "字典管理", action = "保存字典")
    @PostMapping
    public Result<List<Long>> save(@Valid @RequestBody DictMappingSaveRequest req) {
        return Result.success(dictMappingService.save(req));
    }

    @Operation(summary = "批量新增(v0.2.5 CR-004 · 顶部锁四字段 + items 列表 · 单次上限 200)")
    @SysLogRecord(module = "字典管理", action = "批量保存字典")
    @PostMapping("/batch")
    public Result<List<Long>> saveBatch(@Valid @RequestBody DictMappingBatchSaveRequest req) {
        return Result.success(dictMappingService.saveBatch(req));
    }

    @Operation(summary = "编辑（不允许修改 scope/direction/sourceValue）")
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

    @Operation(summary = "Excel 导入(scope 参数决定整批 scope · 默认 3 共享)")
    @SysLogRecord(module = "字典管理", action = "导入字典")
    @PostMapping("/import")
    public Result<DictMappingImportResult> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer scope) {
        return Result.success(dictMappingService.importExcel(file, scope));
    }

    @Operation(summary = "Excel 导出(按 scope 过滤)")
    @SysLogRecord(module = "字典管理", action = "导出字典")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) Integer scope,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String dictKey,
            @RequestParam(required = false) Integer direction,
            @RequestParam(required = false) Integer status) throws Exception {
        byte[] data = dictMappingService.exportExcel(scope, systemCode, dictKey, direction, status);
        return InterfaceConfigController.excelResponse(
                data, "字典映射_" + InterfaceConfigController.tsSuffix() + ".xlsx");
    }

    @Operation(summary = "Lookup（Processor 内部用 · scope 参数决定视角 · 默认 3 共享）")
    @PostMapping("/lookup")
    public Result<DictMappingLookupResult> lookup(
            @RequestParam(required = false) Integer scope,
            @RequestParam String system,
            @RequestParam String dictKey,
            @RequestParam Integer direction,
            @RequestParam String source) {
        DictMappingLookupResult r = dictMappingService.lookup(scope, system, dictKey, direction, source);
        if (r == null) return Result.fail(404, "字典未定义映射");
        return Result.success(r);
    }
}
