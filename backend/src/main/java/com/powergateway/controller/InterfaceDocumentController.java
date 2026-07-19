package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.InterfaceDocumentService;
import com.powergateway.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * FN-09 接口文档 Controller
 * GET /api/doc/transform/list     → 转换接口摘要列表
 * GET /api/doc/transform/{id}     → 单份文档（md/html）
 * GET /api/doc/transform/export   → 全量 zip
 * GET /api/doc/visual/list        → 可视化接口摘要列表
 * GET /api/doc/visual/{id}        → 单份文档
 * GET /api/doc/visual/export      → 全量 zip
 */
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
@Tag(name = "接口文档", description = "FN-09 生成并下载接口文档（Markdown/HTML + 全量 zip）")
public class InterfaceDocumentController {

    private final InterfaceDocumentService docService;
    private final TemplateService templateService;
    private final InterfaceConfigService interfaceService;

    @GetMapping("/transform/list")
    @Operation(summary = "获取转换接口摘要列表")
    public Result<List<Map<String, Object>>> transformList() {
        return Result.success(templateService.listAllSummary());
    }

    @GetMapping("/visual/list")
    @Operation(summary = "获取可视化接口摘要列表")
    public Result<List<Map<String, Object>>> visualList() {
        return Result.success(interfaceService.listAllSummary());
    }

    @GetMapping("/transform/{id}")
    @Operation(summary = "下载单份转换接口文档（md 或 html）")
    public ResponseEntity<byte[]> transformDoc(
            @PathVariable Long id,
            @RequestParam(defaultValue = "md") String format) {
        String content = "html".equalsIgnoreCase(format)
                ? docService.buildHtmlForTemplate(id)
                : docService.buildMarkdownForTemplate(id);
        String name = templateService.getById(id).getName() + "_文档";
        return docResponse(content.getBytes(StandardCharsets.UTF_8), format, name);
    }

    @GetMapping("/visual/{id}")
    @Operation(summary = "下载单份可视化接口文档（md 或 html）")
    public ResponseEntity<byte[]> visualDoc(
            @PathVariable Long id,
            @RequestParam(defaultValue = "md") String format) {
        String content = "html".equalsIgnoreCase(format)
                ? docService.buildHtmlForVisual(id)
                : docService.buildMarkdownForVisual(id);
        String name = interfaceService.getById(id).getName() + "_文档";
        return docResponse(content.getBytes(StandardCharsets.UTF_8), format, name);
    }

    @GetMapping("/transform/export")
    @Operation(summary = "全量导出转换接口文档 zip")
    public ResponseEntity<byte[]> exportTransform() {
        return zipResponse(docService.exportAllTransformZip(), "转换接口文档_" + ts() + ".zip");
    }

    @GetMapping("/visual/export")
    @Operation(summary = "全量导出可视化接口文档 zip")
    public ResponseEntity<byte[]> exportVisual() {
        return zipResponse(docService.exportAllVisualZip(), "可视化接口文档_" + ts() + ".zip");
    }

    // ─── 辅助方法 ──────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> docResponse(byte[] body, String format, String baseName) {
        String ext = "html".equalsIgnoreCase(format) ? "html" : "md";
        MediaType type = "html".equalsIgnoreCase(format)
                ? MediaType.TEXT_HTML : MediaType.parseMediaType("text/markdown");
        String filename;
        try {
            filename = URLEncoder.encode(baseName + "." + ext, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            filename = baseName + "." + ext;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(type)
                .body(body);
    }

    private ResponseEntity<byte[]> zipResponse(byte[] body, String baseName) {
        String filename;
        try {
            filename = URLEncoder.encode(baseName, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            filename = baseName;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    private String ts() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
