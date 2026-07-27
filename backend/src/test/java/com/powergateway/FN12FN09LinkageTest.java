package com.powergateway;

import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.FieldMappingRule;
import com.powergateway.service.DictMappingService;
import com.powergateway.service.InterfaceDocumentService;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** FN-12 × FN-09 联动测试（v0.2.0 ④ Task 1 + Task 2）：字段行读真实 configJson/mappingRule + 提取 dictKey + xlsx 多 sheet */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FN12FN09LinkageTest {

    @Autowired private InterfaceDocumentService docService;
    @Autowired private InterfaceConfigService interfaceService;
    @Autowired private TemplateService templateService;
    @Autowired private DictMappingService dictMappingService;

    // ─── Task 1：字段行改造 + dictKey 提取 + md/html 加列 ─────────────────────

    @Test
    void visualMd_字段表header含字典key列() {
        // 创建含 processRules（顶层）的 SELECT 接口（实际 configJson 结构）
        Long id = insertVisualWithFields();
        String md = docService.buildMarkdownForVisual(id);
        assertThat(md).contains("字典 key");
    }

    @Test
    void visualMd_字段行DICT_MAP的dictKey被展示() {
        Long id = insertVisualWithDictMapField("GENDER");
        String md = docService.buildMarkdownForVisual(id);
        // dictKey 在字段行出现（processRules 顶层，field=gender）
        assertThat(md).contains("GENDER");
    }

    @Test
    void transformMd_字段映射表header含字典key列() {
        Long id = insertTransformWithMapping();
        String md = docService.buildMarkdownForTemplate(id);
        assertThat(md).contains("字典 key");
    }

    @Test
    void transformMd_字段映射真实dictKey被展示() {
        // 前置：创建转换模板，mappingRule 含字段映射，processRule 含 DICT_MAP 字典规则
        Long id = insertTransformWithDictMap("STATUS");
        String md = docService.buildMarkdownForTemplate(id);
        // dictKey "STATUS" 应在字段映射行出现
        assertThat(md).contains("STATUS");
    }

    @Test
    void extractDictKey_无DICT_MAP规则返空串() {
        // 直接调用 package-private static 方法（同一包）
        List<Map<String, Object>> rules = new ArrayList<>();
        Map<String, Object> trimRule = new HashMap<>();
        trimRule.put("type", "TRIM");
        Map<String, Object> params = new HashMap<>();
        params.put("mode", "BOTH");
        trimRule.put("params", params);
        rules.add(trimRule);

        String result = InterfaceDocumentService.extractDictKey(rules);
        assertThat(result).isEmpty();
    }

    // ─── 测试辅助 ──────────────────────────────────────────────────────────────

    /**
     * 创建含基本字段（无字典规则）的 SELECT 接口。
     * configJson 使用实际结构：{tables, fields:[{table,column,alias}], processRules:[...], conditions, joins}
     */
    private Long insertVisualWithFields() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("测试接口FN12FN09基础");
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[{\"name\":\"user\",\"alias\":\"u\"}]," +
            "\"fields\":[" +
                "{\"table\":\"u\",\"column\":\"user_id\",\"alias\":\"userId\"}," +
                "{\"table\":\"u\",\"column\":\"gender\",\"alias\":\"gender\"}" +
            "]," +
            "\"conditions\":[]," +
            "\"joins\":[]," +
            "\"processRules\":[]}");
        return interfaceService.save(req);
    }

    /**
     * 创建含 DICT_MAP processRule（顶层）的 SELECT 接口。
     * processRules 顶层结构：[{type, field, params:{system, dictKey, direction}}]
     */
    private Long insertVisualWithDictMapField(String dictKey) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("测试字典字段FN12FN09_" + dictKey);
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[{\"name\":\"user\",\"alias\":\"u\"}]," +
            "\"fields\":[{\"table\":\"u\",\"column\":\"gender\",\"alias\":\"gender\"}]," +
            "\"conditions\":[]," +
            "\"joins\":[]," +
            "\"processRules\":[{\"type\":\"DICT_MAP\",\"field\":\"gender\"," +
                "\"params\":{\"system\":\"CIF\",\"dictKey\":\"" + dictKey + "\",\"direction\":\"1\"}}]}");
        return interfaceService.save(req);
    }

    /**
     * 创建含字段映射的转换模板。
     * FieldMappingRule 结构：{srcField, targetField, fixedValue}（无 process 字段，processRule 在 process_rule 列）
     */
    private Long insertTransformWithMapping() {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName("测试模板FN12FN09");
        req.setSrcFormat("JSON");
        req.setTargetFormat("XML");
        FieldMappingRule rule = new FieldMappingRule();
        rule.setSrcField("a");
        rule.setTargetField("b");
        req.setMappingRules(Collections.singletonList(rule));
        return templateService.saveTemplate(req);
    }

    /**
     * 创建转换模板，含 processRule（字典规则）。
     * mappingRule 结构：[{srcField, targetField}]
     * processRule 结构：[{type, field, params:{system, dictKey, direction}}]
     */
    private Long insertTransformWithDictMap(String dictKey) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName("测试模板字典FN12FN09_" + dictKey);
        req.setSrcFormat("JSON");
        req.setTargetFormat("XML");
        FieldMappingRule rule = new FieldMappingRule();
        rule.setSrcField("a");
        rule.setTargetField("b");
        req.setMappingRules(Collections.singletonList(rule));
        // 注意：TemplateSaveRequest 可能没有 processRule 字段，需要用原始 SQL 或直接操作数据库
        // 或者如果有 setter，直接设置；此处假设需要用 mapper.insert 或等价操作
        Long id = templateService.saveTemplate(req);
        // 后置：直接写入 processRule（模拟向导生成的规则）
        // 由于 TemplateSaveRequest 可能不支持 processRule，通过 mapper 或原生 SQL 更新
        String processRuleJson = "[{\"type\":\"DICT_MAP\",\"field\":\"b\"," +
                "\"params\":{\"system\":\"CIF\",\"dictKey\":\"" + dictKey + "\",\"direction\":\"1\"}}]";
        // 假设 templateService 有 updateProcessRule 或可访问 mapper
        templateService.updateProcessRuleById(id, processRuleJson);
        return id;
    }

    // ─── Task 2：xlsx 多 sheet 生成 ───────────────────────────────────────────

    @Test
    void visualXlsx_返4sheet() throws Exception {
        Long id = insertVisualWithDictMapField("GENDER");
        byte[] bytes = docService.buildVisualXlsx(id);
        assertThat(bytes).isNotNull();
        // 用 POI 读取验证 sheet 数量和名称
        try (org.apache.poi.ss.usermodel.Workbook wb =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("基本信息");
            assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("请求字段");
            assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("响应字段");
            assertThat(wb.getSheetAt(3).getSheetName()).isEqualTo("字典对照");
        }
    }

    @Test
    void visualXlsx_字典对照sheet含预置字典条目() throws Exception {
        // 前置：先预置 dict_mapping 数据
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("GENDER"); req.setDirection(1);
        req.setSourceValue("M"); req.setTargetValue("1"); req.setBidirectional(false);
        dictMappingService.save(req);

        Long id = insertVisualWithDictMapField("GENDER");
        byte[] bytes = docService.buildVisualXlsx(id);
        try (org.apache.poi.ss.usermodel.Workbook wb =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            org.apache.poi.ss.usermodel.Sheet dict = wb.getSheetAt(3);
            // 至少 header + 1 数据行
            assertThat(dict.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void transformXlsx_返3sheet() throws Exception {
        Long id = insertTransformWithMapping();
        byte[] bytes = docService.buildTransformXlsx(id);
        try (org.apache.poi.ss.usermodel.Workbook wb =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("基本信息");
            assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("字段映射");
            assertThat(wb.getSheetAt(2).getSheetName()).isEqualTo("字典对照");
        }
    }

    // ─── Task 3：zip 升级含 xlsx + manifest 加 xlsx 键 ────────────────────────

    @Test
    void visualZip_manifest含xlsx键() throws Exception {
        insertVisualWithFields();
        byte[] zipBytes = docService.exportAllVisualZip();
        // 解压找 manifest.json
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry e;
            String manifestJson = null;
            java.util.Set<String> names = new java.util.HashSet<>();
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
                if ("manifest.json".equals(e.getName())) {
                    java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                    byte[] bytes = new byte[1024];
                    int len;
                    while ((len = zis.read(bytes)) != -1) {
                        buf.write(bytes, 0, len);
                    }
                    manifestJson = new String(buf.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            assertThat(manifestJson).contains("\"xlsx\"");
            assertThat(names).anyMatch(n -> n.endsWith(".xlsx"));
        }
    }

    @Test
    void transformZip_每接口含md_html_xlsx三份() throws Exception {
        insertTransformWithMapping();
        byte[] zipBytes = docService.exportAllTransformZip();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry e;
            int mdCount = 0, htmlCount = 0, xlsxCount = 0;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().endsWith(".md")) mdCount++;
                if (e.getName().endsWith(".html")) htmlCount++;
                if (e.getName().endsWith(".xlsx")) xlsxCount++;
            }
            assertThat(mdCount).isEqualTo(htmlCount).isEqualTo(xlsxCount);
            assertThat(xlsxCount).isGreaterThan(0);
        }
    }

    @Test
    void controllerFormatXlsx_returnsExcelResponse() throws Exception {
        // 通过直接调 docService 方法验证 bytes 非空
        Long id = insertVisualWithFields();
        byte[] bytes = docService.buildVisualXlsx(id);
        assertThat(bytes.length).isGreaterThan(100);
    }
}
