package com.powergateway.service.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.dto.FieldMappingRule;
import com.powergateway.utils.processor.ProcessRule;
import com.powergateway.utils.processor.ProcessRuleType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FN-11 Task 3 · 转换模板 ↔ .xlsx 双向编解码
 *
 * Sheet 布局：元数据 / 字段映射 / 字段加工 / _meta（隐藏）。
 * FieldMappingRule 三列（srcField/targetField/fixedValue）；ProcessRule 两列（type / paramsJson）。
 */
@Service
public class ExcelTemplateCodec {

    public static final String SCHEMA_VERSION = "1";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SHEET_META = "元数据";
    private static final String SHEET_MAPPING = "字段映射";
    private static final String SHEET_PROCESS = "字段加工";
    private static final String SHEET_INTERNAL_META = "_meta";

    private final ObjectMapper objectMapper;

    @Autowired
    public ExcelTemplateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // Encode
    // ============================================================

    public byte[] encode(ConvertTemplate t) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeMetaSheet(wb, t);
            writeMappingSheet(wb, parseMapping(t.getMappingRule()));
            writeProcessSheet(wb, parseProcess(t.getProcessRule()));
            writeInternalMetaSheet(wb, t);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excel 序列化失败", e);
        }
    }

    private void writeMetaSheet(Workbook wb, ConvertTemplate t) {
        Sheet sheet = wb.createSheet(SHEET_META);
        CellStyle header = headerStyle(wb);
        writeRow(sheet, 0, header, "字段编码", "字段名称", "值");
        int r = 1;
        r = put(sheet, r, "id", "模板ID", t.getId());
        r = put(sheet, r, "name", "模板名", t.getName());
        r = put(sheet, r, "srcFormat", "源格式", t.getSrcFormat());
        r = put(sheet, r, "targetFormat", "目标格式", t.getTargetFormat());
        r = put(sheet, r, "functionCode", "功能号", t.getFunctionCode());
        r = put(sheet, r, "version", "版本", t.getVersion());
        put(sheet, r, "isLatest", "是否最新", t.getIsLatest());
    }

    private void writeMappingSheet(Workbook wb, List<FieldMappingRule> rules) {
        Sheet sheet = wb.createSheet(SHEET_MAPPING);
        CellStyle header = headerStyle(wb);
        writeRow(sheet, 0, header, "源字段路径", "目标字段路径", "固定值");
        int r = 1;
        for (FieldMappingRule rule : rules) {
            writeRow(sheet, r++, null, safe(rule.getSrcField()), safe(rule.getTargetField()), safe(rule.getFixedValue()));
        }
    }

    private void writeProcessSheet(Workbook wb, List<ProcessRule> rules) {
        Sheet sheet = wb.createSheet(SHEET_PROCESS);
        CellStyle header = headerStyle(wb);
        writeRow(sheet, 0, header, "加工类型", "参数JSON");
        int r = 1;
        for (ProcessRule rule : rules) {
            String typeName = rule.getType() == null ? "" : rule.getType().name();
            String paramsJson;
            try {
                paramsJson = rule.getParams() == null ? "{}" : objectMapper.writeValueAsString(rule.getParams());
            } catch (Exception e) {
                paramsJson = "{}";
            }
            writeRow(sheet, r++, null, typeName, paramsJson);
        }
    }

    private void writeInternalMetaSheet(Workbook wb, ConvertTemplate t) {
        Sheet sheet = wb.createSheet(SHEET_INTERNAL_META);
        writeRow(sheet, 0, null, "schemaVersion", SCHEMA_VERSION);
        writeRow(sheet, 1, null, "exportedAt", LocalDateTime.now().format(TS));
        writeRow(sheet, 2, null, "sourceTemplateId", t.getId() == null ? "" : String.valueOf(t.getId()));
        wb.setSheetVisibility(wb.getSheetIndex(sheet), SheetVisibility.HIDDEN);
    }

    // ============================================================
    // Decode
    // ============================================================

    public ConvertTemplate decode(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            requireSheet(wb, SHEET_META);
            requireSheet(wb, SHEET_MAPPING);
            requireSheet(wb, SHEET_PROCESS);
            requireSheet(wb, SHEET_INTERNAL_META);

            String version = findInternalMeta(wb, "schemaVersion");
            if (!SCHEMA_VERSION.equals(version)) {
                throw new IncompatibleSchemaException(
                        "Excel schemaVersion=" + version + " 与当前 codec 支持的 " + SCHEMA_VERSION + " 不匹配");
            }

            ConvertTemplate t = readMetaSheet(wb.getSheet(SHEET_META));
            t.setMappingRule(objectMapper.writeValueAsString(readMappingSheet(wb.getSheet(SHEET_MAPPING))));
            t.setProcessRule(objectMapper.writeValueAsString(readProcessSheet(wb.getSheet(SHEET_PROCESS))));
            return t;
        } catch (IncompatibleSchemaException | InvalidExcelStructureException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidExcelStructureException("Excel 解析失败", e);
        }
    }

    private ConvertTemplate readMetaSheet(Sheet sheet) {
        ConvertTemplate t = new ConvertTemplate();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String code = cellStr(row, 0);
            String value = cellStr(row, 2);
            if (code == null || code.isEmpty()) continue;
            switch (code) {
                case "id": t.setId(parseLong(value)); break;
                case "name": t.setName(value); break;
                case "srcFormat": t.setSrcFormat(value); break;
                case "targetFormat": t.setTargetFormat(value); break;
                case "functionCode": t.setFunctionCode(value); break;
                case "version": t.setVersion(parseInt(value)); break;
                case "isLatest": t.setIsLatest(parseInt(value)); break;
                default: /* 兼容 */
            }
        }
        return t;
    }

    private List<FieldMappingRule> readMappingSheet(Sheet sheet) {
        List<FieldMappingRule> rules = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String src = cellStr(row, 0);
            String tgt = cellStr(row, 1);
            String fixed = cellStr(row, 2);
            if ((tgt == null || tgt.isEmpty()) && (src == null || src.isEmpty()) && (fixed == null || fixed.isEmpty())) {
                continue;
            }
            FieldMappingRule r = new FieldMappingRule();
            r.setSrcField(src);
            r.setTargetField(tgt);
            r.setFixedValue(fixed);
            rules.add(r);
        }
        return rules;
    }

    private List<ProcessRule> readProcessSheet(Sheet sheet) {
        List<ProcessRule> rules = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String typeName = cellStr(row, 0);
            if (typeName == null || typeName.isEmpty()) continue;
            String paramsJson = cellStr(row, 1);
            ProcessRule r = new ProcessRule();
            try {
                r.setType(ProcessRuleType.valueOf(typeName));
            } catch (IllegalArgumentException e) {
                throw new InvalidExcelStructureException("字段加工 sheet 第 " + (i + 1) + " 行 未知的加工类型: " + typeName);
            }
            Map<String, String> params = new LinkedHashMap<>();
            if (paramsJson != null && !paramsJson.isEmpty()) {
                try {
                    Map<String, String> parsed = objectMapper.readValue(paramsJson, new TypeReference<Map<String, String>>() {});
                    if (parsed != null) params.putAll(parsed);
                } catch (Exception e) {
                    throw new InvalidExcelStructureException("字段加工 sheet 第 " + (i + 1) + " 行 params JSON 解析失败: " + paramsJson);
                }
            }
            r.setParams(params);
            rules.add(r);
        }
        return rules;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private List<FieldMappingRule> parseMapping(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<FieldMappingRule> list = objectMapper.readValue(json, new TypeReference<List<FieldMappingRule>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (IOException e) {
            throw new InvalidExcelStructureException("mappingRule JSON 反序列化失败", e);
        }
    }

    private List<ProcessRule> parseProcess(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<ProcessRule> list = objectMapper.readValue(json, new TypeReference<List<ProcessRule>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (IOException e) {
            throw new InvalidExcelStructureException("processRule JSON 反序列化失败", e);
        }
    }

    private void requireSheet(Workbook wb, String name) {
        if (wb.getSheet(name) == null) {
            throw new InvalidExcelStructureException("缺少必要 Sheet: " + name);
        }
    }

    private String findInternalMeta(Workbook wb, String key) {
        Sheet sheet = wb.getSheet(SHEET_INTERNAL_META);
        if (sheet == null) return null;
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (key.equals(cellStr(row, 0))) return cellStr(row, 1);
        }
        return null;
    }

    private int put(Sheet sheet, int rowIdx, String code, String label, Object value) {
        writeRow(sheet, rowIdx, null, code, label, value == null ? "" : String.valueOf(value));
        return rowIdx + 1;
    }

    private void writeRow(Sheet sheet, int rowIdx, CellStyle style, String... values) {
        Row row = sheet.createRow(rowIdx);
        for (int c = 0; c < values.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(values[c] == null ? "" : values[c]);
            if (style != null) cell.setCellStyle(style);
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private String cellStr(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        c.setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        String v = c.getStringCellValue();
        return v == null ? null : v.trim();
    }

    private Long parseLong(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
