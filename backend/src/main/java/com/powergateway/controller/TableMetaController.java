package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.service.TableMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "表结构查询", description = "M2-2 查询目标库表结构、缓存管理、Excel 导出")
@RestController
@RequestMapping("/api/db")
public class TableMetaController {

    @Autowired
    private TableMetaService tableMetaService;

    @Operation(summary = "查询表结构（优先读缓存，TTL 24h）")
    @GetMapping("/{dbId}/tables")
    public Result<List<TableMeta>> getTables(@PathVariable Long dbId) {
        return Result.success(tableMetaService.getTables(dbId));
    }

    @Operation(summary = "清除缓存并刷新表结构")
    @DeleteMapping("/{dbId}/tables/cache")
    public Result<List<TableMeta>> refreshCache(@PathVariable Long dbId) {
        return Result.success(tableMetaService.refreshCache(dbId));
    }

    @Operation(summary = "导出表结构为 Excel")
    @GetMapping("/{dbId}/tables/export")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long dbId) {
        byte[] data = tableMetaService.exportExcel(dbId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "table_structure_" + dbId + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
