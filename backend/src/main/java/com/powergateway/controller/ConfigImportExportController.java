package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.ImportResult;
import com.powergateway.service.ConfigExportService;
import com.powergateway.service.ConfigImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
        ConfigImportService.ConflictStrategy cs;
        try {
            cs = ConfigImportService.ConflictStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            cs = ConfigImportService.ConflictStrategy.SKIP;
        }
        ImportResult result = importService.importZip(file, cs);
        return Result.success(result);
    }
}
