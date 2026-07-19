package com.powergateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.InterfaceConfig;
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
