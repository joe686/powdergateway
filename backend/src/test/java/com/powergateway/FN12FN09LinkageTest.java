package com.powergateway;

import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.FieldMappingRule;
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

/** FN-12 × FN-09 联动测试（v0.2.0 ④ Task 1）：字段行读真实 configJson/mappingRule + 提取 dictKey */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FN12FN09LinkageTest {

    @Autowired private InterfaceDocumentService docService;
    @Autowired private InterfaceConfigService interfaceService;
    @Autowired private TemplateService templateService;

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
}
