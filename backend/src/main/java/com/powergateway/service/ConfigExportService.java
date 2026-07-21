package com.powergateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.service.codec.ExcelConfigCodec;
import com.powergateway.service.codec.ExcelTemplateCodec;
import com.powergateway.service.codec.MarkdownConfigCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * FN-11 配置导出 Service
 * 将转换模板 + 可视化接口配置打包为 zip（含 manifest.json）
 */
@Service
@RequiredArgsConstructor
public class ConfigExportService {

    private final TemplateService templateService;
    private final InterfaceConfigService interfaceService;
    private final ObjectMapper objectMapper;
    private final ExcelConfigCodec excelConfigCodec;
    private final ExcelTemplateCodec excelTemplateCodec;
    private final MarkdownConfigCodec markdownConfigCodec;

    // ============================================================
    // FN-11 Task 5 · 按 ID 列表导出 Excel / Markdown（新增端点用）
    // ============================================================

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 导出接口配置 excel：单 ID 返回单 xlsx；多 ID 打包成 zip。 */
    public ExportResult exportInterfaceItems(List<Long> ids, ExportFormat format) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        String ext = format == ExportFormat.EXCEL ? ".xlsx" : ".md";
        for (Long id : ids) {
            InterfaceConfig cfg = interfaceService.getById(id);
            if (cfg == null) continue;
            String fileName = sanitize(cfg.getType()) + "_" + sanitize(cfg.getName()) + "_"
                    + LocalDateTime.now().format(FILE_TS) + ext;
            byte[] bytes = format == ExportFormat.EXCEL
                    ? excelConfigCodec.encode(cfg)
                    : markdownConfigCodec.encodeInterface(cfg).getBytes(StandardCharsets.UTF_8);
            files.put(fileName, bytes);
        }
        return finalizeExport(files, ext, format);
    }

    /** 导出转换模板 excel/markdown：单 ID 返回单文件；多 ID 打包成 zip。 */
    public ExportResult exportTemplateItems(List<Long> ids, ExportFormat format) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        String ext = format == ExportFormat.EXCEL ? ".xlsx" : ".md";
        for (Long id : ids) {
            ConvertTemplate t = templateService.getById(id);
            if (t == null) continue;
            String fileName = "TEMPLATE_" + sanitize(t.getName()) + "_"
                    + sanitize(t.getSrcFormat()) + "to" + sanitize(t.getTargetFormat()) + "_"
                    + LocalDateTime.now().format(FILE_TS) + ext;
            byte[] bytes = format == ExportFormat.EXCEL
                    ? excelTemplateCodec.encode(t)
                    : markdownConfigCodec.encodeTemplate(t).getBytes(StandardCharsets.UTF_8);
            files.put(fileName, bytes);
        }
        return finalizeExport(files, ext, format);
    }

    private ExportResult finalizeExport(Map<String, byte[]> files, String ext, ExportFormat format) {
        if (files.isEmpty()) {
            throw new BusinessException(404, "未找到任何指定 id 对应的记录");
        }
        if (files.size() == 1) {
            Map.Entry<String, byte[]> only = files.entrySet().iterator().next();
            String contentType = format == ExportFormat.EXCEL
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "text/markdown; charset=utf-8";
            return new ExportResult(only.getKey(), only.getValue(), contentType);
        }
        // 多文件打 zip
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
            zos.finish();
            String zipName = "PowerGateway_" + (format == ExportFormat.EXCEL ? "excel" : "markdown")
                    + "_" + LocalDateTime.now().format(FILE_TS) + ".zip";
            return new ExportResult(zipName, out.toByteArray(), "application/zip");
        } catch (Exception e) {
            throw new BusinessException(500, "打包 zip 失败: " + e.getMessage());
        }
    }

    private String sanitize(String s) {
        if (s == null || s.isEmpty()) return "unknown";
        // 非法文件名字符替换为下划线
        return s.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    public enum ExportFormat { EXCEL, MARKDOWN }

    public static class ExportResult {
        public final String fileName;
        public final byte[] data;
        public final String contentType;
        public ExportResult(String fileName, byte[] data, String contentType) {
            this.fileName = fileName;
            this.data = data;
            this.contentType = contentType;
        }
    }

    /**
     * 导出全部最新版本模板 + 所有接口配置为 zip 包。
     *
     * @return zip 字节数组
     */
    public byte[] exportAll() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<Map<String, Object>> templateEntries = new ArrayList<>();
        List<Map<String, Object>> interfaceEntries = new ArrayList<>();

        // ── 转换模板 ────────────────────────────────────────────────────────
        List<Map<String, Object>> templateSummaries = templateService.listAllSummary();
        for (Map<String, Object> s : templateSummaries) {
            Long id = ((Number) s.get("id")).longValue();
            try {
                ConvertTemplate tpl = templateService.getById(id);
                String safeName = safe(tpl.getName());
                String filePath = "templates/" + safeName + ".json";

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", tpl.getName());
                payload.put("srcFormat", tpl.getSrcFormat());
                payload.put("targetFormat", tpl.getTargetFormat());
                payload.put("mappingRule", tpl.getMappingRule());
                payload.put("processRule", tpl.getProcessRule());
                payload.put("functionCode", tpl.getFunctionCode());
                payload.put("version", tpl.getVersion());

                entries.put(filePath, toJson(payload));

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", tpl.getName());
                entry.put("srcFormat", tpl.getSrcFormat());
                entry.put("targetFormat", tpl.getTargetFormat());
                entry.put("file", filePath);
                templateEntries.add(entry);
            } catch (Exception e) {
                // 单个失败不中断
            }
        }

        // ── 可视化接口 ────────────────────────────────────────────────────────
        List<Map<String, Object>> interfaceSummaries = interfaceService.listAllSummary();
        for (Map<String, Object> s : interfaceSummaries) {
            Long id = ((Number) s.get("id")).longValue();
            try {
                InterfaceConfig cfg = interfaceService.getById(id);
                String safeName = safe(cfg.getName());
                String filePath = "interfaces/" + safeName + ".json";

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("name", cfg.getName());
                payload.put("type", cfg.getType());
                payload.put("configJson", cfg.getConfigJson());
                payload.put("dbConnectionId", cfg.getDbConnectionId());
                payload.put("responseFormat", cfg.getResponseFormat());
                payload.put("responseHeaders", cfg.getResponseHeaders());
                payload.put("cacheEnabled", cfg.getCacheEnabled());
                payload.put("cacheTtlSeconds", cfg.getCacheTtlSeconds());
                payload.put("cacheKeyTemplate", cfg.getCacheKeyTemplate());
                payload.put("allowBatchDelete", cfg.getAllowBatchDelete());

                entries.put(filePath, toJson(payload));

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", cfg.getName());
                entry.put("type", cfg.getType());
                entry.put("file", filePath);
                interfaceEntries.add(entry);
            } catch (Exception e) {
                // 单个失败不中断
            }
        }

        // ── manifest.json ────────────────────────────────────────────────────
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("type", "POWERGATEWAY_CONFIG_EXPORT");
        manifest.put("exportedAt", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        manifest.put("templateCount", templateEntries.size());
        manifest.put("interfaceCount", interfaceEntries.size());
        manifest.put("templates", templateEntries);
        manifest.put("interfaces", interfaceEntries);
        entries.put("manifest.json", toJson(manifest));

        return pack(entries);
    }

    // ─── 内部辅助 ──────────────────────────────────────────────────────────────

    private byte[] toJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new BusinessException(500, "JSON 序列化失败: " + e.getMessage());
        }
    }

    private String safe(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[/\\\\?*\\[\\]:\\s]", "_");
    }

    private byte[] pack(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
            zos.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "zip 打包失败: " + e.getMessage());
        }
    }
}
