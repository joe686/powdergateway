package com.powergateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.dto.FieldMappingRule;
import com.powergateway.service.codec.ExcelTemplateCodec;
import com.powergateway.service.codec.IncompatibleSchemaException;
import com.powergateway.service.codec.InvalidExcelStructureException;
import com.powergateway.utils.processor.ProcessRule;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FN-11 Task 3 · ExcelTemplateCodec 转换模板 ↔ xlsx 双向编解码
 */
@ActiveProfiles("test")
class FN11ExcelTemplateCodecTest {

    private ObjectMapper objectMapper;
    private ExcelTemplateCodec codec;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        codec = new ExcelTemplateCodec(objectMapper);
    }

    // ============ 基本 encode ============

    @Test
    void encode_生成4张sheet__meta隐藏() throws Exception {
        byte[] bytes = codec.encode(buildFlatTemplate());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getSheet("元数据")).isNotNull();
            assertThat(wb.getSheet("字段映射")).isNotNull();
            assertThat(wb.getSheet("字段加工")).isNotNull();
            assertThat(wb.getSheet("_meta")).isNotNull();
        }
    }

    // ============ 往返：普通映射 ============

    @Test
    void encode_decode_普通映射_往返一致() throws Exception {
        ConvertTemplate original = buildFlatTemplate();
        byte[] bytes = codec.encode(original);
        ConvertTemplate back = codec.decode(new ByteArrayInputStream(bytes));

        assertThat(back.getName()).isEqualTo(original.getName());
        assertThat(back.getSrcFormat()).isEqualTo("JSON");
        assertThat(back.getTargetFormat()).isEqualTo("XML");
        assertThat(back.getFunctionCode()).isEqualTo("BIZ_001");

        List<FieldMappingRule> rules = objectMapper.readValue(back.getMappingRule(),
                new TypeReference<List<FieldMappingRule>>() {});
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).getSrcField()).isEqualTo("userId");
        assertThat(rules.get(0).getTargetField()).isEqualTo("uid");
        assertThat(rules.get(1).getSrcField()).isEqualTo("amount");
        assertThat(rules.get(1).getTargetField()).isEqualTo("amt");
    }

    // ============ 往返：嵌套路径 + 数组 ============

    @Test
    void encode_decode_嵌套与数组路径_保持不变() throws Exception {
        ConvertTemplate t = new ConvertTemplate();
        t.setName("循环报文模板");
        t.setSrcFormat("JSON");
        t.setTargetFormat("JSON");
        t.setFunctionCode("BIZ_LOOP");
        FieldMappingRule r1 = new FieldMappingRule();
        r1.setSrcField("head.FunctionId");
        r1.setTargetField("bizCode");
        FieldMappingRule r2 = new FieldMappingRule();
        r2.setSrcField("body.items[*].amount");
        r2.setTargetField("dst.list[*].amt");
        t.setMappingRule(objectMapper.writeValueAsString(Arrays.asList(r1, r2)));

        byte[] bytes = codec.encode(t);
        ConvertTemplate back = codec.decode(new ByteArrayInputStream(bytes));
        List<FieldMappingRule> rules = objectMapper.readValue(back.getMappingRule(),
                new TypeReference<List<FieldMappingRule>>() {});
        assertThat(rules.get(0).getSrcField()).isEqualTo("head.FunctionId");
        assertThat(rules.get(1).getSrcField()).isEqualTo("body.items[*].amount");
        assertThat(rules.get(1).getTargetField()).isEqualTo("dst.list[*].amt");
    }

    // ============ 往返：固定值 ============

    @Test
    void encode_decode_固定值映射_srcField空_fixedValue保留() throws Exception {
        ConvertTemplate t = new ConvertTemplate();
        t.setName("含固定值");
        t.setSrcFormat("JSON");
        t.setTargetFormat("JSON");
        FieldMappingRule r = new FieldMappingRule();
        r.setSrcField(null);
        r.setTargetField("version");
        r.setFixedValue("1.0.0");
        t.setMappingRule(objectMapper.writeValueAsString(Arrays.asList(r)));

        byte[] bytes = codec.encode(t);
        ConvertTemplate back = codec.decode(new ByteArrayInputStream(bytes));
        List<FieldMappingRule> rules = objectMapper.readValue(back.getMappingRule(),
                new TypeReference<List<FieldMappingRule>>() {});
        assertThat(rules.get(0).getSrcField()).isNullOrEmpty();
        assertThat(rules.get(0).getFixedValue()).isEqualTo("1.0.0");
        assertThat(rules.get(0).getTargetField()).isEqualTo("version");
    }

    // ============ 往返：字段加工 ============

    @Test
    void encode_decode_字段加工_params完整保留() throws Exception {
        ConvertTemplate t = buildFlatTemplate();
        Map<String, String> trimParams = new LinkedHashMap<>();
        trimParams.put("mode", "BOTH");
        Map<String, String> subParams = new LinkedHashMap<>();
        subParams.put("start", "0");
        subParams.put("length", "5");

        List<ProcessRule> rules = Arrays.asList(
                new ProcessRule(com.powergateway.utils.processor.ProcessRuleType.TRIM, trimParams),
                new ProcessRule(com.powergateway.utils.processor.ProcessRuleType.SUBSTRING, subParams)
        );
        t.setProcessRule(objectMapper.writeValueAsString(rules));

        byte[] bytes = codec.encode(t);
        ConvertTemplate back = codec.decode(new ByteArrayInputStream(bytes));
        List<ProcessRule> backRules = objectMapper.readValue(back.getProcessRule(),
                new TypeReference<List<ProcessRule>>() {});
        assertThat(backRules).hasSize(2);
        assertThat(backRules.get(0).getType()).isEqualTo(com.powergateway.utils.processor.ProcessRuleType.TRIM);
        assertThat(backRules.get(0).getParams()).containsEntry("mode", "BOTH");
        assertThat(backRules.get(1).getType()).isEqualTo(com.powergateway.utils.processor.ProcessRuleType.SUBSTRING);
        assertThat(backRules.get(1).getParams()).containsEntry("length", "5");
    }

    // ============ 空规则 ============

    @Test
    void encode_decode_空mappingRule_不崩溃() throws Exception {
        ConvertTemplate t = new ConvertTemplate();
        t.setName("空规则");
        t.setSrcFormat("JSON");
        t.setTargetFormat("JSON");
        t.setMappingRule("[]");
        t.setProcessRule("[]");

        byte[] bytes = codec.encode(t);
        ConvertTemplate back = codec.decode(new ByteArrayInputStream(bytes));
        assertThat(back.getMappingRule()).isEqualTo("[]");
    }

    // ============ schema 校验 ============

    @Test
    void decode_schemaVersion不匹配_抛IncompatibleSchemaException() throws Exception {
        byte[] bytes = codec.encode(buildFlatTemplate());
        byte[] tampered = tamperSchemaVersion(bytes, "99");
        assertThatThrownBy(() -> codec.decode(new ByteArrayInputStream(tampered)))
                .isInstanceOf(IncompatibleSchemaException.class);
    }

    @Test
    void decode_缺失字段映射sheet_抛InvalidExcelStructureException() throws Exception {
        byte[] bytes = codec.encode(buildFlatTemplate());
        byte[] stripped = removeSheet(bytes, "字段映射");
        assertThatThrownBy(() -> codec.decode(new ByteArrayInputStream(stripped)))
                .isInstanceOf(InvalidExcelStructureException.class);
    }

    // ============ 辅助 ============

    private ConvertTemplate buildFlatTemplate() throws Exception {
        ConvertTemplate t = new ConvertTemplate();
        t.setId(11L);
        t.setName("扁平模板示例");
        t.setSrcFormat("JSON");
        t.setTargetFormat("XML");
        t.setFunctionCode("BIZ_001");
        t.setVersion(1);
        t.setIsLatest(1);

        FieldMappingRule r1 = new FieldMappingRule();
        r1.setSrcField("userId");
        r1.setTargetField("uid");
        FieldMappingRule r2 = new FieldMappingRule();
        r2.setSrcField("amount");
        r2.setTargetField("amt");
        t.setMappingRule(objectMapper.writeValueAsString(Arrays.asList(r1, r2)));
        t.setProcessRule("[]");
        return t;
    }

    private byte[] tamperSchemaVersion(byte[] bytes, String badVersion) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet meta = wb.getSheet("_meta");
            for (int i = 0; i <= meta.getLastRowNum(); i++) {
                if ("schemaVersion".equals(meta.getRow(i).getCell(0).getStringCellValue())) {
                    meta.getRow(i).getCell(1).setCellValue(badVersion);
                    break;
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] removeSheet(byte[] bytes, String sheetName) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            int idx = wb.getSheetIndex(sheetName);
            if (idx >= 0) wb.removeSheetAt(idx);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
