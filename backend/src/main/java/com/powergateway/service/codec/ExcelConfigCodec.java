package com.powergateway.service.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.QueryConfigJson;
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
import java.util.List;

/**
 * FN-11 Task 2 · 接口配置 ↔ .xlsx 双向编解码
 *
 * v1 覆盖 SELECT (QUERY) 类型；INSERT / UPDATE / DELETE 下轮扩展。
 * Sheet 布局：元数据 / 表配置 / 字段列表 / 条件配置 / _meta（隐藏）。
 * schemaVersion 强制校验，避免旧格式误导入。
 */
@Service
public class ExcelConfigCodec {

    public static final String SCHEMA_VERSION = "1";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SHEET_META = "元数据";
    private static final String SHEET_TABLES = "表配置";
    private static final String SHEET_FIELDS = "字段列表";
    private static final String SHEET_CONDITIONS = "条件配置";
    private static final String SHEET_INTERNAL_META = "_meta";

    private static final String META_SCHEMA_VERSION = "schemaVersion";
    private static final String META_EXPORTED_AT = "exportedAt";
    private static final String META_SOURCE_ID = "sourceInterfaceId";
    private static final String META_PROCESS_RULES_JSON = "processRulesJson";

    private final ObjectMapper objectMapper;

    @Autowired
    public ExcelConfigCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // Encode
    // ============================================================

    public byte[] encode(InterfaceConfig cfg) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            QueryConfigJson query = parseQueryConfigJson(cfg.getConfigJson());

            writeMetaSheet(wb, cfg);
            writeTablesSheet(wb, query);
            writeFieldsSheet(wb, query);
            writeConditionsSheet(wb, query);
            writeInternalMetaSheet(wb, cfg, query);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excel 序列化失败", e);
        }
    }

    private void writeMetaSheet(Workbook wb, InterfaceConfig cfg) {
        Sheet sheet = wb.createSheet(SHEET_META);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "字段编码", "字段名称", "值");

        int r = 1;
        r = putMeta(sheet, r, "id", "接口ID", cfg.getId());
        r = putMeta(sheet, r, "name", "接口名称", cfg.getName());
        r = putMeta(sheet, r, "path", "访问路径", cfg.getPath());
        r = putMeta(sheet, r, "type", "接口类型", cfg.getType());
        r = putMeta(sheet, r, "dbConnectionId", "数据库连接ID", cfg.getDbConnectionId());
        r = putMeta(sheet, r, "status", "状态", cfg.getStatus());
        r = putMeta(sheet, r, "responseFormat", "响应格式", cfg.getResponseFormat());
        r = putMeta(sheet, r, "responseHeaders", "响应头JSON", cfg.getResponseHeaders());
        r = putMeta(sheet, r, "logEnabled", "开启SQL日志", cfg.getLogEnabled());
        r = putMeta(sheet, r, "shardConfigId", "分库分表配置ID", cfg.getShardConfigId());
        r = putMeta(sheet, r, "allowBatchDelete", "允许批量删除", cfg.getAllowBatchDelete());
        r = putMeta(sheet, r, "cacheEnabled", "启用缓存", cfg.getCacheEnabled());
        r = putMeta(sheet, r, "cacheTtlSeconds", "缓存TTL(秒)", cfg.getCacheTtlSeconds());
        putMeta(sheet, r, "cacheKeyTemplate", "缓存Key模板", cfg.getCacheKeyTemplate());
    }

    private void writeTablesSheet(Workbook wb, QueryConfigJson query) {
        Sheet sheet = wb.createSheet(SHEET_TABLES);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "行类型", "表名", "别名", "JOIN类型", "左表.列", "右表.列");
        int r = 1;
        if (query.getTables() != null) {
            for (QueryConfigJson.TableDef t : query.getTables()) {
                writeRow(sheet, r++, null, "TABLE", t.getName(), t.getAlias(), "", "", "");
            }
        }
        if (query.getJoins() != null) {
            for (QueryConfigJson.JoinDef j : query.getJoins()) {
                String left = safe(j.getLeftTable()) + "." + safe(j.getLeftCol());
                String right = safe(j.getRightTable()) + "." + safe(j.getRightCol());
                writeRow(sheet, r++, null, "JOIN", "", "", j.getType(), left, right);
            }
        }
    }

    private void writeFieldsSheet(Workbook wb, QueryConfigJson query) {
        Sheet sheet = wb.createSheet(SHEET_FIELDS);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "表别名", "列名", "输出别名");
        int r = 1;
        if (query.getFields() != null) {
            for (QueryConfigJson.FieldDef f : query.getFields()) {
                writeRow(sheet, r++, null, f.getTable(), f.getColumn(), f.getAlias());
            }
        }
    }

    private void writeConditionsSheet(Workbook wb, QueryConfigJson query) {
        Sheet sheet = wb.createSheet(SHEET_CONDITIONS);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "字段", "操作符", "参数名");
        int r = 1;
        if (query.getConditions() != null) {
            for (QueryConfigJson.ConditionDef c : query.getConditions()) {
                writeRow(sheet, r++, null, c.getField(), c.getOp(), c.getParamKey());
            }
        }
    }

    private void writeInternalMetaSheet(Workbook wb, InterfaceConfig cfg, QueryConfigJson query) {
        Sheet sheet = wb.createSheet(SHEET_INTERNAL_META);
        writeRow(sheet, 0, null, META_SCHEMA_VERSION, SCHEMA_VERSION);
        writeRow(sheet, 1, null, META_EXPORTED_AT, LocalDateTime.now().format(TS));
        writeRow(sheet, 2, null, META_SOURCE_ID, cfg.getId() == null ? "" : String.valueOf(cfg.getId()));
        // 把 processRules 原样序列化存这里；反导入时回填到 configJson
        String rulesJson;
        try {
            rulesJson = query.getProcessRules() == null ? "[]"
                    : objectMapper.writeValueAsString(query.getProcessRules());
        } catch (JsonProcessingException e) {
            rulesJson = "[]";
        }
        writeRow(sheet, 3, null, META_PROCESS_RULES_JSON, rulesJson);
        wb.setSheetVisibility(wb.getSheetIndex(sheet), SheetVisibility.HIDDEN);
    }

    // ============================================================
    // Decode
    // ============================================================

    public InterfaceConfig decode(InputStream in) {
        try (Workbook wb = new XSSFWorkbook(in)) {
            requireSheet(wb, SHEET_META);
            requireSheet(wb, SHEET_TABLES);
            requireSheet(wb, SHEET_FIELDS);
            requireSheet(wb, SHEET_CONDITIONS);
            requireSheet(wb, SHEET_INTERNAL_META);

            String version = findInternalMeta(wb, META_SCHEMA_VERSION);
            if (!SCHEMA_VERSION.equals(version)) {
                throw new IncompatibleSchemaException(
                        "Excel schemaVersion=" + version + " 与当前 codec 支持的 " + SCHEMA_VERSION + " 不匹配，请从新版本重新导出");
            }

            InterfaceConfig cfg = readMetaSheet(wb.getSheet(SHEET_META));
            QueryConfigJson query = new QueryConfigJson();
            readTablesAndJoins(wb.getSheet(SHEET_TABLES), query);
            readFields(wb.getSheet(SHEET_FIELDS), query);
            readConditions(wb.getSheet(SHEET_CONDITIONS), query);
            readProcessRules(wb, query);

            try {
                cfg.setConfigJson(objectMapper.writeValueAsString(query));
            } catch (JsonProcessingException e) {
                throw new InvalidExcelStructureException("configJson 回写失败", e);
            }
            return cfg;
        } catch (IncompatibleSchemaException | InvalidExcelStructureException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidExcelStructureException("Excel 解析失败", e);
        }
    }

    private InterfaceConfig readMetaSheet(Sheet sheet) {
        InterfaceConfig cfg = new InterfaceConfig();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String code = cellStr(row, 0);
            String value = cellStr(row, 2);
            if (code == null || code.isEmpty()) continue;
            switch (code) {
                case "id": cfg.setId(parseLong(value)); break;
                case "name": cfg.setName(value); break;
                case "path": cfg.setPath(value); break;
                case "type": cfg.setType(value); break;
                case "dbConnectionId": cfg.setDbConnectionId(parseLong(value)); break;
                case "status": cfg.setStatus(value); break;
                case "responseFormat": cfg.setResponseFormat(value); break;
                case "responseHeaders": cfg.setResponseHeaders(value); break;
                case "logEnabled": cfg.setLogEnabled(parseInt(value)); break;
                case "shardConfigId": cfg.setShardConfigId(parseLong(value)); break;
                case "allowBatchDelete": cfg.setAllowBatchDelete(parseInt(value)); break;
                case "cacheEnabled": cfg.setCacheEnabled(parseInt(value)); break;
                case "cacheTtlSeconds": cfg.setCacheTtlSeconds(parseInt(value)); break;
                case "cacheKeyTemplate": cfg.setCacheKeyTemplate(value); break;
                default: /* 忽略未识别字段，向前兼容 */
            }
        }
        return cfg;
    }

    private void readTablesAndJoins(Sheet sheet, QueryConfigJson query) {
        List<QueryConfigJson.TableDef> tables = new ArrayList<>();
        List<QueryConfigJson.JoinDef> joins = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String rowType = cellStr(row, 0);
            if ("TABLE".equalsIgnoreCase(rowType)) {
                QueryConfigJson.TableDef t = new QueryConfigJson.TableDef();
                t.setName(cellStr(row, 1));
                t.setAlias(cellStr(row, 2));
                if (t.getName() != null && !t.getName().isEmpty()) tables.add(t);
            } else if ("JOIN".equalsIgnoreCase(rowType)) {
                QueryConfigJson.JoinDef j = new QueryConfigJson.JoinDef();
                j.setType(cellStr(row, 3));
                String[] left = splitDot(cellStr(row, 4));
                String[] right = splitDot(cellStr(row, 5));
                if (left != null) { j.setLeftTable(left[0]); j.setLeftCol(left[1]); }
                if (right != null) { j.setRightTable(right[0]); j.setRightCol(right[1]); }
                joins.add(j);
            }
        }
        query.setTables(tables);
        query.setJoins(joins);
    }

    private void readFields(Sheet sheet, QueryConfigJson query) {
        List<QueryConfigJson.FieldDef> fields = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String col = cellStr(row, 1);
            if (col == null || col.isEmpty()) continue;
            QueryConfigJson.FieldDef f = new QueryConfigJson.FieldDef();
            f.setTable(cellStr(row, 0));
            f.setColumn(col);
            f.setAlias(cellStr(row, 2));
            fields.add(f);
        }
        query.setFields(fields);
    }

    private void readConditions(Sheet sheet, QueryConfigJson query) {
        List<QueryConfigJson.ConditionDef> conds = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String field = cellStr(row, 0);
            if (field == null || field.isEmpty()) continue;
            QueryConfigJson.ConditionDef c = new QueryConfigJson.ConditionDef();
            c.setField(field);
            c.setOp(cellStr(row, 1));
            c.setParamKey(cellStr(row, 2));
            conds.add(c);
        }
        query.setConditions(conds);
    }

    private void readProcessRules(Workbook wb, QueryConfigJson query) {
        String json = findInternalMeta(wb, META_PROCESS_RULES_JSON);
        if (json == null || json.isEmpty()) {
            query.setProcessRules(new ArrayList<>());
            return;
        }
        try {
            List<Object> rules = objectMapper.readValue(json, List.class);
            query.setProcessRules(rules == null ? new ArrayList<>() : rules);
        } catch (IOException e) {
            query.setProcessRules(new ArrayList<>());
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private QueryConfigJson parseQueryConfigJson(String json) {
        if (json == null || json.isEmpty()) return new QueryConfigJson();
        try {
            return objectMapper.readValue(json, QueryConfigJson.class);
        } catch (IOException e) {
            throw new InvalidExcelStructureException("现有 configJson 反序列化失败", e);
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
            if (key.equals(cellStr(row, 0))) {
                return cellStr(row, 1);
            }
        }
        return null;
    }

    private int putMeta(Sheet sheet, int rowIdx, String code, String label, Object value) {
        String v = value == null ? "" : String.valueOf(value);
        writeRow(sheet, rowIdx, null, code, label, v);
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

    private CellStyle buildHeaderStyle(Workbook wb) {
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

    private String[] splitDot(String s) {
        if (s == null) return null;
        int idx = s.indexOf('.');
        if (idx < 0) return new String[]{s, ""};
        return new String[]{s.substring(0, idx), s.substring(idx + 1)};
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
