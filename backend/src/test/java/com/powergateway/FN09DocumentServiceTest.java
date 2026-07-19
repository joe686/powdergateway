package com.powergateway;

import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceDocumentService;
import com.powergateway.service.TemplateService;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FN-09 InterfaceDocumentService 生成文档")
class FN09DocumentServiceTest {

    @Autowired private InterfaceDocumentService docService;
    @Autowired private TemplateService templateService;
    @Autowired private InterfaceConfigService interfaceService;

    private Long createTemplate(String name) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName(name);
        req.setSrcFormat("JSON");
        req.setTargetFormat("XML");
        req.setMappingRules(Collections.emptyList());
        return templateService.saveTemplate(req);
    }

    private Long createInterface(String name) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName(name);
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        return interfaceService.save(req);
    }

    // ─── 转换模板文档 ──────────────────────────────────────────────────────────

    @Test
    void 转换模板_生成Markdown_包含标题和格式信息() {
        Long id = createTemplate("MarkdownTestTpl");
        String md = docService.buildMarkdownForTemplate(id);
        assertNotNull(md, "Markdown 不应为 null");
        assertTrue(md.contains("# "), "Markdown 应包含一级标题");
        assertTrue(md.contains("MarkdownTestTpl"), "Markdown 应包含模板名称");
        assertTrue(md.contains("JSON"), "Markdown 应包含源格式 JSON");
        assertTrue(md.contains("XML"), "Markdown 应包含目标格式 XML");
    }

    @Test
    void 转换模板_生成HTML_包含DOCTYPE和编码() {
        Long id = createTemplate("HtmlTestTpl");
        String html = docService.buildHtmlForTemplate(id);
        assertNotNull(html, "HTML 不应为 null");
        assertTrue(html.contains("<!DOCTYPE html>"), "HTML 应包含 DOCTYPE");
        assertTrue(html.contains("UTF-8"), "HTML 应包含 UTF-8 声明");
        assertTrue(html.contains("HtmlTestTpl"), "HTML 应包含模板名称");
    }

    @Test
    void 转换模板_HTML包含基本信息表格() {
        Long id = createTemplate("TableTpl");
        String html = docService.buildHtmlForTemplate(id);
        assertTrue(html.contains("<table>") || html.contains("<h2>"), "HTML 应包含表格或章节标题");
    }

    // ─── 可视化接口文档 ────────────────────────────────────────────────────────

    @Test
    void 可视化接口_生成Markdown_包含接口名和类型() {
        Long id = createInterface("SelectApiDoc");
        String md = docService.buildMarkdownForVisual(id);
        assertNotNull(md, "Markdown 不应为 null");
        assertTrue(md.contains("# "), "Markdown 应包含一级标题");
        assertTrue(md.contains("SelectApiDoc"), "Markdown 应包含接口名称");
        assertTrue(md.contains("SELECT"), "Markdown 应包含接口类型");
    }

    @Test
    void 可视化接口_生成HTML_包含DOCTYPE() {
        Long id = createInterface("HtmlApiDoc");
        String html = docService.buildHtmlForVisual(id);
        assertNotNull(html, "HTML 不应为 null");
        assertTrue(html.contains("<!DOCTYPE html>"), "HTML 应包含 DOCTYPE");
        assertTrue(html.contains("HtmlApiDoc"), "HTML 应包含接口名称");
    }

    @Test
    void 可视化接口_SELECT类型_包含分页字段说明() {
        Long id = createInterface("PaginationApiDoc");
        String md = docService.buildMarkdownForVisual(id);
        // SELECT 接口文档应说明 page/pageSize
        assertTrue(md.contains("page") || md.contains("分页"),
            "SELECT 接口文档应提及分页参数");
    }

    // ─── 全量 zip 导出 ─────────────────────────────────────────────────────────

    @Test
    void 全量导出转换接口zip_包含manifest() throws Exception {
        createTemplate("ZipTpl1");
        createTemplate("ZipTpl2");

        byte[] zipBytes = docService.exportAllTransformZip();
        assertNotNull(zipBytes, "zip 字节不应为 null");
        assertTrue(zipBytes.length > 0, "zip 内容不应为空");

        boolean hasManifest = false;
        int mdCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) hasManifest = true;
                if (entry.getName().endsWith(".md")) mdCount++;
                zis.closeEntry();
            }
        }
        assertTrue(hasManifest, "zip 应包含 manifest.json");
        assertTrue(mdCount >= 2, "zip 应包含至少 2 个 .md 文件");
    }

    @Test
    void 全量导出可视化接口zip_包含manifest() throws Exception {
        createInterface("ZipVisual1");
        createInterface("ZipVisual2");

        byte[] zipBytes = docService.exportAllVisualZip();
        assertNotNull(zipBytes, "zip 字节不应为 null");
        assertTrue(zipBytes.length > 0, "zip 内容不应为空");

        boolean hasManifest = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) hasManifest = true;
                zis.closeEntry();
            }
        }
        assertTrue(hasManifest, "zip 应包含 manifest.json");
    }

    @Test
    void 模板摘要列表_返回id和name() {
        createTemplate("SummaryTpl1");
        createTemplate("SummaryTpl2");
        java.util.List<java.util.Map<String, Object>> summaries = templateService.listAllSummary();
        assertFalse(summaries.isEmpty(), "摘要列表不应为空");
        summaries.forEach(m -> {
            assertTrue(m.containsKey("id"), "每条摘要应包含 id");
            assertTrue(m.containsKey("name"), "每条摘要应包含 name");
            assertTrue(m.containsKey("srcFormat"), "每条摘要应包含 srcFormat");
            assertTrue(m.containsKey("targetFormat"), "每条摘要应包含 targetFormat");
        });
    }

    @Test
    void 接口摘要列表_返回id和name和type() {
        createInterface("SummaryApi1");
        java.util.List<java.util.Map<String, Object>> summaries = interfaceService.listAllSummary();
        assertFalse(summaries.isEmpty(), "摘要列表不应为空");
        summaries.forEach(m -> {
            assertTrue(m.containsKey("id"), "每条摘要应包含 id");
            assertTrue(m.containsKey("name"), "每条摘要应包含 name");
            assertTrue(m.containsKey("type"), "每条摘要应包含 type");
            assertTrue(m.containsKey("status"), "每条摘要应包含 status");
        });
    }
}
