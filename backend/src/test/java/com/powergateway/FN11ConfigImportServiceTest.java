package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.ImportResult;
import com.powergateway.service.ConfigExportService;
import com.powergateway.service.ConfigImportService;
import com.powergateway.service.TemplateService;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FN-11 ConfigImportService 导入")
class FN11ConfigImportServiceTest {

    @Autowired private ConfigImportService importService;
    @Autowired private ConfigExportService exportService;
    @Autowired private TemplateService templateService;
    @Autowired private InterfaceConfigService interfaceService;
    @Autowired private ObjectMapper objectMapper;

    private Long createTemplate(String name) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName(name);
        req.setSrcFormat("JSON");
        req.setTargetFormat("XML");
        req.setMappingRules(Collections.emptyList());
        return templateService.saveTemplate(req);
    }

    // ─── 辅助：构建最小合法 zip ────────────────────────────────────────────────

    private MockMultipartFile buildZip(String templateName) throws Exception {
        // 先创建模板，再导出
        createTemplate(templateName);
        byte[] zipBytes = exportService.exportAll();
        return new MockMultipartFile("file", "export.zip",
                "application/zip", zipBytes);
    }

    // ─── SKIP 策略 ─────────────────────────────────────────────────────────────

    @Test
    void SKIP策略_新建模板成功() throws Exception {
        MockMultipartFile file = buildZip("SkipNewTpl");
        // 删除该模板，再导入（模拟"新建"场景）
        // 由于 @Transactional，创建的数据会回滚，所以先查数量
        long beforeCount = templateService.listAllSummary().size();

        // 构建一个包含不存在模板的 zip
        byte[] zip = buildManualZip("BrandNewTpl999", "JSON", "XML");
        MockMultipartFile f = new MockMultipartFile("file", "manual.zip",
                "application/zip", zip);
        ImportResult result = importService.importZip(f, ConfigImportService.ConflictStrategy.SKIP);
        assertTrue(result.getImported().size() >= 1, "应有 1 条导入成功");
        assertTrue(result.getFailed().isEmpty(), "不应有失败项");
    }

    @Test
    void SKIP策略_已存在则跳过() throws Exception {
        createTemplate("ExistingTplSkip");
        // 构建包含同名模板的 zip
        byte[] zip = buildManualZip("ExistingTplSkip", "JSON", "XML");
        MockMultipartFile f = new MockMultipartFile("file", "skip.zip",
                "application/zip", zip);
        ImportResult result = importService.importZip(f, ConfigImportService.ConflictStrategy.SKIP);
        assertTrue(result.getSkipped().size() >= 1, "已存在模板应被跳过");
        assertTrue(result.getImported().isEmpty() || result.getImported().stream()
            .noneMatch(s -> s.contains("ExistingTplSkip")), "不应新建 ExistingTplSkip");
    }

    // ─── OVERWRITE 策略 ───────────────────────────────────────────────────────

    @Test
    void OVERWRITE策略_已存在则覆盖() throws Exception {
        createTemplate("OverwriteTpl");
        byte[] zip = buildManualZip("OverwriteTpl", "JSON", "XML");
        MockMultipartFile f = new MockMultipartFile("file", "overwrite.zip",
                "application/zip", zip);
        ImportResult result = importService.importZip(f, ConfigImportService.ConflictStrategy.OVERWRITE);
        assertTrue(result.getImported().stream()
            .anyMatch(s -> s.contains("OverwriteTpl") && s.contains("覆盖")),
            "已存在模板应被覆盖");
    }

    // ─── ASK 策略 ─────────────────────────────────────────────────────────────

    @Test
    void ASK策略_已存在时返回冲突列表() throws Exception {
        createTemplate("AskTpl");
        byte[] zip = buildManualZip("AskTpl", "JSON", "XML");
        MockMultipartFile f = new MockMultipartFile("file", "ask.zip",
                "application/zip", zip);
        ImportResult result = importService.importZip(f, ConfigImportService.ConflictStrategy.ASK);
        assertTrue(result.getConflicts().size() >= 1, "应返回至少 1 条冲突");
        assertTrue(result.getImported().isEmpty(), "ASK 模式不应实际写库");
    }

    // ─── 无效 zip ─────────────────────────────────────────────────────────────

    @Test
    void 缺少manifest_抛出BusinessException() {
        MockMultipartFile f = new MockMultipartFile("file", "bad.zip",
                "application/zip", new byte[]{0x50, 0x4B, 0x05, 0x06, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        assertThrows(Exception.class, () ->
            importService.importZip(f, ConfigImportService.ConflictStrategy.SKIP));
    }

    // ─── 辅助：手动构建包含单个模板的 zip ────────────────────────────────────

    private byte[] buildManualZip(String name, String srcFormat, String targetFormat) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            // 模板文件
            Map<String, Object> tpl = new LinkedHashMap<>();
            tpl.put("name", name);
            tpl.put("srcFormat", srcFormat);
            tpl.put("targetFormat", targetFormat);
            tpl.put("mappingRule", "[]");
            tpl.put("processRule", null);
            tpl.put("functionCode", null);
            tpl.put("version", 1);
            String safeName = name.replaceAll("[/\\\\?*\\[\\]:\\s]", "_");
            String filePath = "templates/" + safeName + ".json";
            byte[] tplBytes = objectMapper.writeValueAsBytes(tpl);
            zos.putNextEntry(new ZipEntry(filePath));
            zos.write(tplBytes);
            zos.closeEntry();

            // manifest.json
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("srcFormat", srcFormat);
            entry.put("targetFormat", targetFormat);
            entry.put("file", filePath);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("type", "POWERGATEWAY_CONFIG_EXPORT");
            manifest.put("exportedAt", "2026-07-20T00:00:00");
            manifest.put("templateCount", 1);
            manifest.put("interfaceCount", 0);
            manifest.put("templates", Collections.singletonList(entry));
            manifest.put("interfaces", Collections.emptyList());
            byte[] mfBytes = objectMapper.writeValueAsBytes(manifest);
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(mfBytes);
            zos.closeEntry();
        }
        return out.toByteArray();
    }
}
