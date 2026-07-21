package com.powergateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ImportManifest;
import com.powergateway.model.dto.ImportResult;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.codec.ExcelConfigCodec;
import com.powergateway.service.codec.ExcelTemplateCodec;
import com.powergateway.service.codec.IncompatibleSchemaException;
import com.powergateway.service.codec.InvalidExcelStructureException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * FN-11 配置导入 Service
 * <p>支持三种冲突策略：
 * <ul>
 *   <li>OVERWRITE：存在同名（三元组）则覆盖（保版本号），不存在则新建</li>
 *   <li>SKIP：存在冲突则跳过，不存在则新建</li>
 *   <li>ASK：检测所有冲突后返回，不实际写库；需前端确认后再次调用 OVERWRITE/SKIP</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigImportService {

    private final TemplateService templateService;
    private final InterfaceConfigService interfaceService;
    private final ObjectMapper objectMapper;
    private final ExcelConfigCodec excelConfigCodec;
    private final ExcelTemplateCodec excelTemplateCodec;

    public enum ConflictStrategy { OVERWRITE, SKIP, ASK }

    // ============================================================
    // FN-11 Task 5 · Excel 多文件导入 + 预览
    // ============================================================

    /** 上传多个 excel 文件（必须同类型），按文件名前缀 TEMPLATE_ 判为转换模板，其他判为接口配置。 */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(MultipartFile[] files, ConflictStrategy strategy) {
        ImportResult result = new ImportResult();
        if (files == null || files.length == 0) {
            throw new BusinessException(400, "未上传任何文件");
        }
        FileGroup group = classifyFiles(files);
        if (group.mixed) {
            throw new BusinessException(400, "本次上传的文件类型混合（既有接口配置也有转换模板），请分批导入");
        }
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename() == null ? "unknown" : f.getOriginalFilename();
            try (InputStream in = f.getInputStream()) {
                if (group.type == ItemType.TEMPLATE) {
                    ConvertTemplate t = excelTemplateCodec.decode(in);
                    processExcelTemplate(t, strategy, result);
                } else {
                    InterfaceConfig cfg = excelConfigCodec.decode(in);
                    processExcelInterface(cfg, strategy, result);
                }
            } catch (IncompatibleSchemaException | InvalidExcelStructureException e) {
                result.addFailed("文件[" + name + "]: " + e.getMessage());
            } catch (Exception e) {
                result.addFailed("文件[" + name + "]: " + e.getMessage());
            }
        }
        return result;
    }

    /** 只解析不落库，返回将影响的接口/模板名及冲突详情（前端预览用）。 */
    public PreviewResult previewExcel(MultipartFile[] files) {
        PreviewResult preview = new PreviewResult();
        if (files == null || files.length == 0) return preview;
        FileGroup group = classifyFiles(files);
        if (group.mixed) {
            preview.getErrors().add("本次上传的文件类型混合，请分批导入");
            return preview;
        }
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename() == null ? "unknown" : f.getOriginalFilename();
            try (InputStream in = f.getInputStream()) {
                if (group.type == ItemType.TEMPLATE) {
                    ConvertTemplate t = excelTemplateCodec.decode(in);
                    PreviewItem item = new PreviewItem();
                    item.setFileName(name);
                    item.setType("TEMPLATE");
                    item.setEntityName(t.getName());
                    ConvertTemplate exist = templateService.findByPrimary(t.getName(), t.getSrcFormat(), t.getTargetFormat());
                    item.setConflict(exist != null);
                    preview.getItems().add(item);
                } else {
                    InterfaceConfig cfg = excelConfigCodec.decode(in);
                    PreviewItem item = new PreviewItem();
                    item.setFileName(name);
                    item.setType("INTERFACE");
                    item.setEntityName(cfg.getName());
                    InterfaceConfig exist = interfaceService.findByName(cfg.getName());
                    item.setConflict(exist != null);
                    preview.getItems().add(item);
                }
            } catch (Exception e) {
                preview.getErrors().add("文件[" + name + "]: " + e.getMessage());
            }
        }
        return preview;
    }

    private void processExcelTemplate(ConvertTemplate t, ConflictStrategy strategy, ImportResult result) {
        String name = t.getName();
        ConvertTemplate exist = templateService.findByPrimary(t.getName(), t.getSrcFormat(), t.getTargetFormat());
        if (exist != null) {
            switch (strategy) {
                case SKIP:
                    result.addSkipped("模板[" + name + "]: 已存在，跳过");
                    return;
                case ASK:
                    result.addConflict(new ImportResult.ConflictItem("template", name, "已存在同名/同格式模板"));
                    return;
                case OVERWRITE:
                default:
            }
        }
        TemplateSaveRequest req = new TemplateSaveRequest();
        if (exist != null) req.setId(exist.getId());
        req.setName(t.getName());
        req.setSrcFormat(t.getSrcFormat());
        req.setTargetFormat(t.getTargetFormat());
        req.setFunctionCode(t.getFunctionCode());
        // mappingRule JSON → List<FieldMappingRule>
        if (t.getMappingRule() != null && !t.getMappingRule().isEmpty()) {
            try {
                req.setMappingRules(objectMapper.readValue(t.getMappingRule(),
                        new TypeReference<List<com.powergateway.model.dto.FieldMappingRule>>() {}));
            } catch (Exception e) {
                req.setMappingRules(Collections.emptyList());
            }
        } else {
            req.setMappingRules(Collections.emptyList());
        }
        // 注：v1 版本 TemplateSaveRequest 不包含 processRule 字段，excel 里的加工规则
        //     暂无法通过 saveTemplate 保存（与 zip 导入逻辑一致，见 buildTemplateRequest）
        templateService.saveTemplate(req);
        result.addImported("模板[" + name + "]: " + (exist == null ? "已新建" : "已覆盖"));
    }

    private void processExcelInterface(InterfaceConfig cfg, ConflictStrategy strategy, ImportResult result) {
        String name = cfg.getName();
        InterfaceConfig exist = interfaceService.findByName(cfg.getName());
        if (exist != null) {
            switch (strategy) {
                case SKIP:
                    result.addSkipped("接口[" + name + "]: 已存在，跳过");
                    return;
                case ASK:
                    result.addConflict(new ImportResult.ConflictItem("interface", name, "已存在同名接口"));
                    return;
                case OVERWRITE:
                default:
            }
        }
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        if (exist != null) req.setId(exist.getId());
        req.setName(cfg.getName());
        req.setType(cfg.getType());
        req.setConfigJson(cfg.getConfigJson());
        req.setDbConnectionId(cfg.getDbConnectionId());
        req.setResponseFormat(cfg.getResponseFormat());
        req.setResponseHeaders(cfg.getResponseHeaders());
        req.setCacheEnabled(cfg.getCacheEnabled());
        req.setCacheTtlSeconds(cfg.getCacheTtlSeconds());
        req.setCacheKeyTemplate(cfg.getCacheKeyTemplate());
        req.setAllowBatchDelete(cfg.getAllowBatchDelete());
        interfaceService.save(req);
        result.addImported("接口[" + name + "]: " + (exist == null ? "已新建" : "已覆盖"));
    }

    private FileGroup classifyFiles(MultipartFile[] files) {
        FileGroup g = new FileGroup();
        for (MultipartFile f : files) {
            String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().trim();
            ItemType t = name.toUpperCase().startsWith("TEMPLATE_") ? ItemType.TEMPLATE : ItemType.INTERFACE;
            if (g.type == null) g.type = t;
            else if (g.type != t) { g.mixed = true; return g; }
        }
        return g;
    }

    private enum ItemType { INTERFACE, TEMPLATE }
    private static class FileGroup {
        ItemType type;
        boolean mixed = false;
    }

    @Data
    public static class PreviewResult {
        private List<PreviewItem> items = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
    }

    @Data
    public static class PreviewItem {
        private String fileName;
        private String type;       // INTERFACE / TEMPLATE
        private String entityName;
        private boolean conflict;  // 与 DB 已存在同名冲突
    }

    /**
     * 从 MultipartFile（zip）执行导入。
     *
     * @param file     上传的 zip 文件
     * @param strategy 冲突策略
     * @return 导入结果汇总
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importZip(MultipartFile file, ConflictStrategy strategy) {
        Map<String, byte[]> entries = readZip(file);
        ImportManifest manifest = parseManifest(entries);
        ImportResult result = new ImportResult();

        // ── 模板导入 ──────────────────────────────────────────────────────────
        if (manifest.getTemplates() != null) {
            for (ImportManifest.ManifestEntry entry : manifest.getTemplates()) {
                try {
                    byte[] data = entries.get(entry.getFile());
                    if (data == null) {
                        result.addFailed("模板[" + entry.getName() + "]: 文件缺失 " + entry.getFile());
                        continue;
                    }
                    Map<String, Object> payload = parsePayload(data);
                    processTemplate(entry, payload, strategy, result);
                } catch (Exception e) {
                    result.addFailed("模板[" + entry.getName() + "]: " + e.getMessage());
                }
            }
        }

        // ── 接口导入 ──────────────────────────────────────────────────────────
        if (manifest.getInterfaces() != null) {
            for (ImportManifest.ManifestEntry entry : manifest.getInterfaces()) {
                try {
                    byte[] data = entries.get(entry.getFile());
                    if (data == null) {
                        result.addFailed("接口[" + entry.getName() + "]: 文件缺失 " + entry.getFile());
                        continue;
                    }
                    Map<String, Object> payload = parsePayload(data);
                    processInterface(entry, payload, strategy, result);
                } catch (Exception e) {
                    result.addFailed("接口[" + entry.getName() + "]: " + e.getMessage());
                }
            }
        }

        return result;
    }

    // ─── 模板处理 ──────────────────────────────────────────────────────────────

    private void processTemplate(ImportManifest.ManifestEntry entry,
                                  Map<String, Object> payload,
                                  ConflictStrategy strategy,
                                  ImportResult result) {
        String name = str(payload, "name");
        String srcFormat = str(payload, "srcFormat");
        String targetFormat = str(payload, "targetFormat");

        ConvertTemplate existing = templateService.findByPrimary(name, srcFormat, targetFormat);

        if (existing != null) {
            switch (strategy) {
                case SKIP:
                    result.addSkipped("模板[" + name + "]: 已存在，已跳过");
                    return;
                case ASK:
                    result.addConflict(new ImportResult.ConflictItem(
                        "TEMPLATE", name,
                        "已存在模板 " + name + "（" + srcFormat + "→" + targetFormat + "）"));
                    return;
                case OVERWRITE:
                    // 覆盖：以现有 id 更新
                    TemplateSaveRequest req = buildTemplateRequest(payload);
                    req.setId(existing.getId());
                    templateService.saveTemplate(req);
                    result.addImported("模板[" + name + "]: 已覆盖");
                    return;
                default:
                    break;
            }
        }

        // 不存在 → 新建
        TemplateSaveRequest req = buildTemplateRequest(payload);
        templateService.saveTemplate(req);
        result.addImported("模板[" + name + "]: 已新建");
    }

    private TemplateSaveRequest buildTemplateRequest(Map<String, Object> payload) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName(str(payload, "name"));
        req.setSrcFormat(str(payload, "srcFormat"));
        req.setTargetFormat(str(payload, "targetFormat"));
        req.setFunctionCode(str(payload, "functionCode"));
        // mappingRule 存为字符串，反序列化为 List
        String mappingRuleJson = str(payload, "mappingRule");
        if (mappingRuleJson != null && !mappingRuleJson.trim().isEmpty()) {
            try {
                req.setMappingRules(objectMapper.readValue(mappingRuleJson,
                    new TypeReference<List<com.powergateway.model.dto.FieldMappingRule>>() {}));
            } catch (Exception e) {
                req.setMappingRules(Collections.emptyList());
            }
        } else {
            req.setMappingRules(Collections.emptyList());
        }
        return req;
    }

    // ─── 接口处理 ──────────────────────────────────────────────────────────────

    private void processInterface(ImportManifest.ManifestEntry entry,
                                   Map<String, Object> payload,
                                   ConflictStrategy strategy,
                                   ImportResult result) {
        String name = str(payload, "name");
        InterfaceConfig existing = interfaceService.findByName(name);

        if (existing != null) {
            switch (strategy) {
                case SKIP:
                    result.addSkipped("接口[" + name + "]: 已存在，已跳过");
                    return;
                case ASK:
                    result.addConflict(new ImportResult.ConflictItem(
                        "INTERFACE", name, "已存在接口 " + name));
                    return;
                case OVERWRITE:
                    InterfaceSaveRequest req = buildInterfaceRequest(payload);
                    req.setId(existing.getId());
                    interfaceService.save(req);
                    result.addImported("接口[" + name + "]: 已覆盖");
                    return;
                default:
                    break;
            }
        }

        // 不存在 → 新建
        InterfaceSaveRequest req = buildInterfaceRequest(payload);
        interfaceService.save(req);
        result.addImported("接口[" + name + "]: 已新建");
    }

    private InterfaceSaveRequest buildInterfaceRequest(Map<String, Object> payload) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName(str(payload, "name"));
        req.setType(str(payload, "type"));
        req.setConfigJson(str(payload, "configJson"));
        // dbConnectionId
        Object dbId = payload.get("dbConnectionId");
        if (dbId != null) req.setDbConnectionId(((Number) dbId).longValue());
        req.setResponseFormat(str(payload, "responseFormat"));
        req.setResponseHeaders(str(payload, "responseHeaders"));
        // cache fields
        Object cacheEnabled = payload.get("cacheEnabled");
        if (cacheEnabled != null) req.setCacheEnabled(((Number) cacheEnabled).intValue());
        Object ttl = payload.get("cacheTtlSeconds");
        if (ttl != null) req.setCacheTtlSeconds(((Number) ttl).intValue());
        req.setCacheKeyTemplate(str(payload, "cacheKeyTemplate"));
        Object allowBatch = payload.get("allowBatchDelete");
        if (allowBatch != null) req.setAllowBatchDelete(((Number) allowBatch).intValue());
        return req;
    }

    // ─── zip 解析 ─────────────────────────────────────────────────────────────

    private Map<String, byte[]> readZip(MultipartFile file) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (InputStream in = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    byte[] data = readFully(zis);
                    result.put(entry.getName(), data);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            throw new BusinessException(400, "zip 文件解析失败: " + e.getMessage());
        }
        return result;
    }

    private ImportManifest parseManifest(Map<String, byte[]> entries) {
        byte[] manifestData = entries.get("manifest.json");
        if (manifestData == null) {
            throw new BusinessException(400, "zip 中缺少 manifest.json");
        }
        try {
            // manifest.json 中 templates/interfaces 字段对应 ImportManifest 结构
            Map<String, Object> raw = objectMapper.readValue(manifestData,
                new TypeReference<Map<String, Object>>() {});
            ImportManifest manifest = new ImportManifest();
            manifest.setType(str(raw, "type"));
            manifest.setExportedAt(str(raw, "exportedAt"));
            // 解析 templates
            Object tpls = raw.get("templates");
            if (tpls != null) {
                manifest.setTemplates(objectMapper.convertValue(tpls,
                    new TypeReference<List<ImportManifest.ManifestEntry>>() {}));
            }
            // 解析 interfaces
            Object ifaces = raw.get("interfaces");
            if (ifaces != null) {
                manifest.setInterfaces(objectMapper.convertValue(ifaces,
                    new TypeReference<List<ImportManifest.ManifestEntry>>() {}));
            }
            return manifest;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "manifest.json 解析失败: " + e.getMessage());
        }
    }

    private byte[] readFully(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(byte[] data) {
        try {
            return objectMapper.readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(400, "配置文件解析失败: " + e.getMessage());
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }
}
