package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.ImportResult;
import com.powergateway.service.ConfigExportService;
import com.powergateway.service.ConfigImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FN-11 配置导入/导出 Controller
 * GET  /api/config/export        → 全量导出 zip
 * POST /api/config/import        → 上传 zip 导入，strategy=OVERWRITE|SKIP|ASK
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "配置导入/导出", description = "FN-11 接口配置与模板的导入导出（zip 格式）")
public class ConfigImportExportController {

    private final ConfigExportService exportService;
    private final ConfigImportService importService;

    @GetMapping("/export")
    @Operation(summary = "全量导出配置 zip（含模板 + 接口配置）")
    public ResponseEntity<byte[]> exportAll() {
        byte[] data = exportService.exportAll();
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String rawName = "PowerGateway配置导出_" + ts + ".zip";
        String filename;
        try {
            filename = URLEncoder.encode(rawName, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            filename = rawName;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(data);
    }

    @PostMapping("/import")
    @Operation(summary = "上传 zip 导入配置（strategy: OVERWRITE / SKIP / ASK）")
    public Result<ImportResult> importAll(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "strategy", defaultValue = "SKIP") String strategy) {
        ConfigImportService.ConflictStrategy cs = parseStrategy(strategy);
        ImportResult result = importService.importZip(file, cs);
        return Result.success(result);
    }

    // ============================================================
    // FN-11 Task 5 · Excel / Markdown 导入导出 4 新端点
    // ============================================================

    @PostMapping("/export/excel")
    @Operation(summary = "按 id 列表导出 Excel（单 id → xlsx；多 id → zip）")
    public ResponseEntity<byte[]> exportExcel(@RequestBody ExportByIdsRequest req) {
        ConfigExportService.ExportResult r = "template".equalsIgnoreCase(req.getType())
                ? exportService.exportTemplateItems(req.getIds(), ConfigExportService.ExportFormat.EXCEL)
                : exportService.exportInterfaceItems(req.getIds(), ConfigExportService.ExportFormat.EXCEL);
        return buildDownloadResponse(r);
    }

    @PostMapping("/export/markdown")
    @Operation(summary = "按 id 列表导出 Markdown（单 id → md；多 id → zip）")
    public ResponseEntity<byte[]> exportMarkdown(@RequestBody ExportByIdsRequest req) {
        ConfigExportService.ExportResult r = "template".equalsIgnoreCase(req.getType())
                ? exportService.exportTemplateItems(req.getIds(), ConfigExportService.ExportFormat.MARKDOWN)
                : exportService.exportInterfaceItems(req.getIds(), ConfigExportService.ExportFormat.MARKDOWN);
        return buildDownloadResponse(r);
    }

    @PostMapping("/import/excel")
    @Operation(summary = "上传多个 Excel 导入（strategy: OVERWRITE / SKIP / ASK），文件必须同类型")
    public Result<ImportResult> importExcel(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "strategy", defaultValue = "SKIP") String strategy) {
        ConfigImportService.ConflictStrategy cs = parseStrategy(strategy);
        return Result.success(importService.importExcel(files, cs));
    }

    @PostMapping("/import/preview")
    @Operation(summary = "预览多个 Excel 的导入结果（不落库）")
    public Result<ConfigImportService.PreviewResult> previewExcel(
            @RequestParam("files") MultipartFile[] files) {
        return Result.success(importService.previewExcel(files));
    }

    // ─── helpers ─────────────────────────────────────────────────────

    private ConfigImportService.ConflictStrategy parseStrategy(String s) {
        try {
            return ConfigImportService.ConflictStrategy.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return ConfigImportService.ConflictStrategy.SKIP;
        }
    }

    private ResponseEntity<byte[]> buildDownloadResponse(ConfigExportService.ExportResult r) {
        String filename;
        try {
            filename = URLEncoder.encode(r.fileName, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            filename = r.fileName;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(r.contentType))
                .body(r.data);
    }

    @Data
    public static class ExportByIdsRequest {
        /** 接口/模板 ID 列表 */
        private List<Long> ids;
        /** interface（默认）或 template */
        private String type;
    }
}
