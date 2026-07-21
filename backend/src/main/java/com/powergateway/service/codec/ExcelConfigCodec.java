package com.powergateway.service.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.DeleteConfigJson;
import com.powergateway.model.dto.InsertConfigJson;
import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.model.dto.UpdateConfigJson;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FN-11 Task 2 · 接口配置 ↔ .xlsx 双向编解码
 *
 * 覆盖 SELECT / INSERT / UPDATE / DELETE 四种类型。按 InterfaceConfig.type 分发：
 * <pre>
 *   SELECT  → 元数据 + 表配置(含 JOIN 行) + 字段列表 + 条件配置 + _meta
 *   INSERT  → 元数据 + 表配置(只 TABLE 行) + 数据来源 + _meta
 *   UPDATE  → 元数据 + 表配置(只 TABLE 行) + 数据来源 + 条件配置(含 tableName) + _meta
 *   DELETE  → 元数据 + 表配置(只 TABLE 行) + 条件配置(含 tableName) + _meta
 * </pre>
 * schemaVersion 强制校验，避免旧格式误导入。
 */
@Service
public class ExcelConfigCodec {

    public static final String SCHEMA_VERSION = "1";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SHEET_META = "元数据";
    private static final String SHEET_TABLES = "表配置";
    private static final String SHEET_FIELDS = "字段列表";
    private static final String SHEET_SOURCES = "数据来源";
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
        String type = normalizeType(cfg.getType());
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeMetaSheet(wb, cfg);
            switch (type) {
                case "SELECT": encodeSelect(wb, cfg); break;
                case "INSERT": encodeInsert(wb, cfg); break;
                case "UPDATE": encodeUpdate(wb, cfg); break;
                case "DELETE": encodeDelete(wb, cfg); break;
                default: throw new InvalidExcelStructureException("不支持的接口类型: " + type);
            }
            writeInternalMetaSheet(wb, cfg, type);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Excel 序列化失败", e);
        }
    }

    private void encodeSelect(Workbook wb, InterfaceConfig cfg) {
        QueryConfigJson q = parseJson(cfg.getConfigJson(), QueryConfigJson.class, new QueryConfigJson());
        writeTablesSheet(wb, tablesOf(q), joinsOf(q));
        writeFieldsSheet(wb, q);
        writeConditionsSheetSelect(wb, q);
    }

    private void encodeInsert(Workbook wb, InterfaceConfig cfg) {
        InsertConfigJson i = parseJson(cfg.getConfigJson(), InsertConfigJson.class, new InsertConfigJson());
        List<TableRef> refs = insertTableRefs(i);
        writeTablesSheet(wb, refs, new ArrayList<>());
        writeDataSourcesSheet(wb, insertFieldRefs(i));
    }

    private void encodeUpdate(Workbook wb, InterfaceConfig cfg) {
        UpdateConfigJson u = parseJson(cfg.getConfigJson(), UpdateConfigJson.class, new UpdateConfigJson());
        writeTablesSheet(wb, updateTableRefs(u), new ArrayList<>());
        writeDataSourcesSheet(wb, updateFieldRefs(u));
        writeConditionsSheetWithTable(wb, updateConditionRefs(u));
    }

    private void encodeDelete(Workbook wb, InterfaceConfig cfg) {
        DeleteConfigJson d = parseJson(cfg.getConfigJson(), DeleteConfigJson.class, new DeleteConfigJson());
        writeTablesSheet(wb, deleteTableRefs(d), new ArrayList<>());
        writeConditionsSheetWithTable(wb, deleteConditionRefs(d));
    }

    // ---- Meta ----

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

    // ---- 表配置 ----

    private void writeTablesSheet(Workbook wb, List<TableRef> tables, List<JoinRef> joins) {
        Sheet sheet = wb.createSheet(SHEET_TABLES);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "行类型", "表名", "别名", "JOIN类型", "左表.列", "右表.列");
        int r = 1;
        for (TableRef t : tables) {
            writeRow(sheet, r++, null, "TABLE", t.name, safe(t.alias), "", "", "");
        }
        for (JoinRef j : joins) {
            writeRow(sheet, r++, null, "JOIN", "", "", safe(j.type),
                    safe(j.leftTable) + "." + safe(j.leftCol),
                    safe(j.rightTable) + "." + safe(j.rightCol));
        }
    }

    // ---- SELECT 字段列表 ----

    private void writeFieldsSheet(Workbook wb, QueryConfigJson q) {
        Sheet sheet = wb.createSheet(SHEET_FIELDS);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "表别名", "列名", "输出别名");
        int r = 1;
        if (q.getFields() != null) {
            for (QueryConfigJson.FieldDef f : q.getFields()) {
                writeRow(sheet, r++, null, f.getTable(), f.getColumn(), f.getAlias());
            }
        }
    }

    // ---- INSERT/UPDATE 数据来源 ----

    private void writeDataSourcesSheet(Workbook wb, List<FieldSourceRef> refs) {
        Sheet sheet = wb.createSheet(SHEET_SOURCES);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "目标表", "目标列", "来源类型", "请求参数名", "常量值", "计算表达式");
        int r = 1;
        for (FieldSourceRef f : refs) {
            writeRow(sheet, r++, null,
                    safe(f.tableName), safe(f.column), safe(f.sourceType),
                    safe(f.paramKey), safe(f.constValue), safe(f.expression));
        }
    }

    // ---- SELECT 条件（无 tableName） ----

    private void writeConditionsSheetSelect(Workbook wb, QueryConfigJson q) {
        Sheet sheet = wb.createSheet(SHEET_CONDITIONS);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "目标表", "字段", "操作符", "参数名");
        int r = 1;
        if (q.getConditions() != null) {
            for (QueryConfigJson.ConditionDef c : q.getConditions()) {
                writeRow(sheet, r++, null, "", c.getField(), c.getOp(), c.getParamKey());
            }
        }
    }

    // ---- UPDATE/DELETE 条件（含 tableName） ----

    private void writeConditionsSheetWithTable(Workbook wb, List<ConditionRef> refs) {
        Sheet sheet = wb.createSheet(SHEET_CONDITIONS);
        CellStyle header = buildHeaderStyle(wb);
        writeRow(sheet, 0, header, "目标表", "字段", "操作符", "参数名");
        int r = 1;
        for (ConditionRef c : refs) {
            writeRow(sheet, r++, null, safe(c.tableName), safe(c.field), safe(c.op), safe(c.paramKey));
        }
    }

    // ---- _meta ----

    private void writeInternalMetaSheet(Workbook wb, InterfaceConfig cfg, String type) {
        Sheet sheet = wb.createSheet(SHEET_INTERNAL_META);
        writeRow(sheet, 0, null, META_SCHEMA_VERSION, SCHEMA_VERSION);
        writeRow(sheet, 1, null, META_EXPORTED_AT, LocalDateTime.now().format(TS));
        writeRow(sheet, 2, null, META_SOURCE_ID, cfg.getId() == null ? "" : String.valueOf(cfg.getId()));

        String rulesJson = "[]";
        if ("SELECT".equals(type)) {
            QueryConfigJson q = parseJson(cfg.getConfigJson(), QueryConfigJson.class, new QueryConfigJson());
            if (q.getProcessRules() != null) {
                try {
                    rulesJson = objectMapper.writeValueAsString(q.getProcessRules());
                } catch (JsonProcessingException ignored) {}
            }
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
            requireSheet(wb, SHEET_INTERNAL_META);

            String version = findInternalMeta(wb, META_SCHEMA_VERSION);
            if (!SCHEMA_VERSION.equals(version)) {
                throw new IncompatibleSchemaException(
                        "Excel schemaVersion=" + version + " 与当前 codec 支持的 " + SCHEMA_VERSION + " 不匹配，请从新版本重新导出");
            }

            InterfaceConfig cfg = readMetaSheet(wb.getSheet(SHEET_META));
            String type = normalizeType(cfg.getType());

            switch (type) {
                case "SELECT": decodeSelect(wb, cfg); break;
                case "INSERT": decodeInsert(wb, cfg); break;
                case "UPDATE": decodeUpdate(wb, cfg); break;
                case "DELETE": decodeDelete(wb, cfg); break;
                default: throw new InvalidExcelStructureException("元数据 sheet 里 type 值不合法: " + type);
            }
            return cfg;
        } catch (IncompatibleSchemaException | InvalidExcelStructureException e) {
            throw e;
        } catch (IOException e) {
            throw new InvalidExcelStructureException("Excel 解析失败", e);
        }
    }

    private void decodeSelect(Workbook wb, InterfaceConfig cfg) throws JsonProcessingException {
        requireSheet(wb, SHEET_TABLES);
        requireSheet(wb, SHEET_FIELDS);
        requireSheet(wb, SHEET_CONDITIONS);

        QueryConfigJson q = new QueryConfigJson();
        List<TableRef> tables = new ArrayList<>();
        List<JoinRef> joins = new ArrayList<>();
        readTablesAndJoins(wb.getSheet(SHEET_TABLES), tables, joins);
        q.setTables(tables.stream().map(t -> {
            QueryConfigJson.TableDef d = new QueryConfigJson.TableDef();
            d.setName(t.name); d.setAlias(t.alias); return d;
        }).collect(java.util.stream.Collectors.toList()));
        q.setJoins(joins.stream().map(j -> {
            QueryConfigJson.JoinDef d = new QueryConfigJson.JoinDef();
            d.setLeftTable(j.leftTable); d.setLeftCol(j.leftCol);
            d.setRightTable(j.rightTable); d.setRightCol(j.rightCol);
            d.setType(j.type); return d;
        }).collect(java.util.stream.Collectors.toList()));

        List<QueryConfigJson.FieldDef> fields = new ArrayList<>();
        Sheet fs = wb.getSheet(SHEET_FIELDS);
        for (int i = 1; i <= fs.getLastRowNum(); i++) {
            Row row = fs.getRow(i);
            if (row == null) continue;
            String col = cellStr(row, 1);
            if (col == null || col.isEmpty()) continue;
            QueryConfigJson.FieldDef f = new QueryConfigJson.FieldDef();
            f.setTable(cellStr(row, 0));
            f.setColumn(col);
            f.setAlias(cellStr(row, 2));
            fields.add(f);
        }
        q.setFields(fields);

        List<QueryConfigJson.ConditionDef> conds = new ArrayList<>();
        Sheet cs = wb.getSheet(SHEET_CONDITIONS);
        for (int i = 1; i <= cs.getLastRowNum(); i++) {
            Row row = cs.getRow(i);
            if (row == null) continue;
            String field = cellStr(row, 1);
            if (field == null || field.isEmpty()) continue;
            QueryConfigJson.ConditionDef c = new QueryConfigJson.ConditionDef();
            c.setField(field);
            c.setOp(cellStr(row, 2));
            c.setParamKey(cellStr(row, 3));
            conds.add(c);
        }
        q.setConditions(conds);

        // processRules 从 _meta 恢复
        q.setProcessRules(readProcessRulesFromInternalMeta(wb));

        cfg.setConfigJson(objectMapper.writeValueAsString(q));
    }

    private void decodeInsert(Workbook wb, InterfaceConfig cfg) throws JsonProcessingException {
        requireSheet(wb, SHEET_TABLES);
        requireSheet(wb, SHEET_SOURCES);

        List<TableRef> tables = new ArrayList<>();
        readTablesAndJoins(wb.getSheet(SHEET_TABLES), tables, new ArrayList<>());

        // 按 tableName 分组读取数据来源
        Map<String, List<InsertConfigJson.FieldInsertConfig>> byTable = new LinkedHashMap<>();
        for (TableRef t : tables) {
            byTable.put(t.name, new ArrayList<>());
        }
        Sheet ds = wb.getSheet(SHEET_SOURCES);
        for (int i = 1; i <= ds.getLastRowNum(); i++) {
            Row row = ds.getRow(i);
            if (row == null) continue;
            String tableName = cellStr(row, 0);
            String column = cellStr(row, 1);
            if (column == null || column.isEmpty()) continue;
            InsertConfigJson.FieldInsertConfig f = new InsertConfigJson.FieldInsertConfig();
            f.setColumn(column);
            f.setSourceType(cellStr(row, 2));
            f.setParamKey(cellStr(row, 3));
            f.setConstValue(cellStr(row, 4));
            f.setExpression(cellStr(row, 5));
            byTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(f);
        }

        InsertConfigJson insertConfig = new InsertConfigJson();
        List<InsertConfigJson.TableInsertConfig> tableConfigs = new ArrayList<>();
        for (TableRef t : tables) {
            InsertConfigJson.TableInsertConfig tc = new InsertConfigJson.TableInsertConfig();
            tc.setTableName(t.name);
            tc.setFields(byTable.getOrDefault(t.name, new ArrayList<>()));
            tableConfigs.add(tc);
        }
        insertConfig.setTables(tableConfigs);
        cfg.setConfigJson(objectMapper.writeValueAsString(insertConfig));
    }

    private void decodeUpdate(Workbook wb, InterfaceConfig cfg) throws JsonProcessingException {
        requireSheet(wb, SHEET_TABLES);
        requireSheet(wb, SHEET_SOURCES);
        requireSheet(wb, SHEET_CONDITIONS);

        List<TableRef> tables = new ArrayList<>();
        readTablesAndJoins(wb.getSheet(SHEET_TABLES), tables, new ArrayList<>());

        Map<String, List<InsertConfigJson.FieldInsertConfig>> byTable = new LinkedHashMap<>();
        for (TableRef t : tables) byTable.put(t.name, new ArrayList<>());
        Sheet ds = wb.getSheet(SHEET_SOURCES);
        for (int i = 1; i <= ds.getLastRowNum(); i++) {
            Row row = ds.getRow(i);
            if (row == null) continue;
            String tableName = cellStr(row, 0);
            String column = cellStr(row, 1);
            if (column == null || column.isEmpty()) continue;
            InsertConfigJson.FieldInsertConfig f = new InsertConfigJson.FieldInsertConfig();
            f.setColumn(column);
            f.setSourceType(cellStr(row, 2));
            f.setParamKey(cellStr(row, 3));
            f.setConstValue(cellStr(row, 4));
            f.setExpression(cellStr(row, 5));
            byTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(f);
        }

        UpdateConfigJson updateConfig = new UpdateConfigJson();
        List<UpdateConfigJson.TableUpdateConfig> tableConfigs = new ArrayList<>();
        for (TableRef t : tables) {
            UpdateConfigJson.TableUpdateConfig tc = new UpdateConfigJson.TableUpdateConfig();
            tc.setTableName(t.name);
            tc.setFields(byTable.getOrDefault(t.name, new ArrayList<>()));
            tableConfigs.add(tc);
        }
        updateConfig.setTables(tableConfigs);

        List<UpdateConfigJson.ConditionConfig> conds = new ArrayList<>();
        Sheet cs = wb.getSheet(SHEET_CONDITIONS);
        for (int i = 1; i <= cs.getLastRowNum(); i++) {
            Row row = cs.getRow(i);
            if (row == null) continue;
            String field = cellStr(row, 1);
            if (field == null || field.isEmpty()) continue;
            UpdateConfigJson.ConditionConfig c = new UpdateConfigJson.ConditionConfig();
            c.setTableName(cellStr(row, 0));
            c.setField(field);
            c.setOp(cellStr(row, 2));
            c.setParamKey(cellStr(row, 3));
            conds.add(c);
        }
        updateConfig.setConditions(conds);
        cfg.setConfigJson(objectMapper.writeValueAsString(updateConfig));
    }

    private void decodeDelete(Workbook wb, InterfaceConfig cfg) throws JsonProcessingException {
        requireSheet(wb, SHEET_TABLES);
        requireSheet(wb, SHEET_CONDITIONS);

        List<TableRef> tables = new ArrayList<>();
        readTablesAndJoins(wb.getSheet(SHEET_TABLES), tables, new ArrayList<>());

        Map<String, List<DeleteConfigJson.ConditionItem>> byTable = new LinkedHashMap<>();
        for (TableRef t : tables) byTable.put(t.name, new ArrayList<>());
        Sheet cs = wb.getSheet(SHEET_CONDITIONS);
        for (int i = 1; i <= cs.getLastRowNum(); i++) {
            Row row = cs.getRow(i);
            if (row == null) continue;
            String tableName = cellStr(row, 0);
            String field = cellStr(row, 1);
            if (field == null || field.isEmpty()) continue;
            DeleteConfigJson.ConditionItem c = new DeleteConfigJson.ConditionItem();
            c.setField(field);
            c.setOp(cellStr(row, 2));
            c.setParamKey(cellStr(row, 3));
            byTable.computeIfAbsent(tableName, k -> new ArrayList<>()).add(c);
        }

        DeleteConfigJson deleteConfig = new DeleteConfigJson();
        List<DeleteConfigJson.TableDeleteConfig> tableConfigs = new ArrayList<>();
        for (TableRef t : tables) {
            DeleteConfigJson.TableDeleteConfig tc = new DeleteConfigJson.TableDeleteConfig();
            tc.setTableName(t.name);
            tc.setConditions(byTable.getOrDefault(t.name, new ArrayList<>()));
            tableConfigs.add(tc);
        }
        deleteConfig.setTables(tableConfigs);
        cfg.setConfigJson(objectMapper.writeValueAsString(deleteConfig));
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

    private void readTablesAndJoins(Sheet sheet, List<TableRef> tables, List<JoinRef> joins) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String rowType = cellStr(row, 0);
            if ("TABLE".equalsIgnoreCase(rowType)) {
                TableRef t = new TableRef();
                t.name = cellStr(row, 1);
                t.alias = cellStr(row, 2);
                if (t.name != null && !t.name.isEmpty()) tables.add(t);
            } else if ("JOIN".equalsIgnoreCase(rowType)) {
                JoinRef j = new JoinRef();
                j.type = cellStr(row, 3);
                String[] left = splitDot(cellStr(row, 4));
                String[] right = splitDot(cellStr(row, 5));
                if (left != null) { j.leftTable = left[0]; j.leftCol = left[1]; }
                if (right != null) { j.rightTable = right[0]; j.rightCol = right[1]; }
                joins.add(j);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> readProcessRulesFromInternalMeta(Workbook wb) {
        String json = findInternalMeta(wb, META_PROCESS_RULES_JSON);
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            List<Object> rules = objectMapper.readValue(json, List.class);
            return rules == null ? new ArrayList<>() : rules;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // ============================================================
    // 类型分发时的中间引用（避免各 configJson 泄漏进 sheet 层）
    // ============================================================

    private static class TableRef { String name; String alias; }
    private static class JoinRef { String type; String leftTable; String leftCol; String rightTable; String rightCol; }
    private static class FieldSourceRef {
        String tableName; String column; String sourceType;
        String paramKey; String constValue; String expression;
    }
    private static class ConditionRef {
        String tableName; String field; String op; String paramKey;
    }

    private List<TableRef> tablesOf(QueryConfigJson q) {
        List<TableRef> refs = new ArrayList<>();
        if (q.getTables() != null) {
            for (QueryConfigJson.TableDef t : q.getTables()) {
                TableRef r = new TableRef(); r.name = t.getName(); r.alias = t.getAlias();
                refs.add(r);
            }
        }
        return refs;
    }

    private List<JoinRef> joinsOf(QueryConfigJson q) {
        List<JoinRef> refs = new ArrayList<>();
        if (q.getJoins() != null) {
            for (QueryConfigJson.JoinDef j : q.getJoins()) {
                JoinRef r = new JoinRef();
                r.leftTable = j.getLeftTable(); r.leftCol = j.getLeftCol();
                r.rightTable = j.getRightTable(); r.rightCol = j.getRightCol();
                r.type = j.getType();
                refs.add(r);
            }
        }
        return refs;
    }

    private List<TableRef> insertTableRefs(InsertConfigJson i) {
        List<TableRef> refs = new ArrayList<>();
        if (i.getTables() != null) {
            for (InsertConfigJson.TableInsertConfig t : i.getTables()) {
                TableRef r = new TableRef(); r.name = t.getTableName(); r.alias = "";
                refs.add(r);
            }
        }
        return refs;
    }

    private List<FieldSourceRef> insertFieldRefs(InsertConfigJson i) {
        List<FieldSourceRef> refs = new ArrayList<>();
        if (i.getTables() != null) {
            for (InsertConfigJson.TableInsertConfig t : i.getTables()) {
                if (t.getFields() == null) continue;
                for (InsertConfigJson.FieldInsertConfig f : t.getFields()) {
                    FieldSourceRef r = new FieldSourceRef();
                    r.tableName = t.getTableName();
                    r.column = f.getColumn();
                    r.sourceType = f.getSourceType();
                    r.paramKey = f.getParamKey();
                    r.constValue = f.getConstValue();
                    r.expression = f.getExpression();
                    refs.add(r);
                }
            }
        }
        return refs;
    }

    private List<TableRef> updateTableRefs(UpdateConfigJson u) {
        List<TableRef> refs = new ArrayList<>();
        if (u.getTables() != null) {
            for (UpdateConfigJson.TableUpdateConfig t : u.getTables()) {
                TableRef r = new TableRef(); r.name = t.getTableName(); r.alias = "";
                refs.add(r);
            }
        }
        return refs;
    }

    private List<FieldSourceRef> updateFieldRefs(UpdateConfigJson u) {
        List<FieldSourceRef> refs = new ArrayList<>();
        if (u.getTables() != null) {
            for (UpdateConfigJson.TableUpdateConfig t : u.getTables()) {
                if (t.getFields() == null) continue;
                for (InsertConfigJson.FieldInsertConfig f : t.getFields()) {
                    FieldSourceRef r = new FieldSourceRef();
                    r.tableName = t.getTableName();
                    r.column = f.getColumn();
                    r.sourceType = f.getSourceType();
                    r.paramKey = f.getParamKey();
                    r.constValue = f.getConstValue();
                    r.expression = f.getExpression();
                    refs.add(r);
                }
            }
        }
        return refs;
    }

    private List<ConditionRef> updateConditionRefs(UpdateConfigJson u) {
        List<ConditionRef> refs = new ArrayList<>();
        if (u.getConditions() != null) {
            for (UpdateConfigJson.ConditionConfig c : u.getConditions()) {
                ConditionRef r = new ConditionRef();
                r.tableName = c.getTableName();
                r.field = c.getField();
                r.op = c.getOp();
                r.paramKey = c.getParamKey();
                refs.add(r);
            }
        }
        return refs;
    }

    private List<TableRef> deleteTableRefs(DeleteConfigJson d) {
        List<TableRef> refs = new ArrayList<>();
        if (d.getTables() != null) {
            for (DeleteConfigJson.TableDeleteConfig t : d.getTables()) {
                TableRef r = new TableRef(); r.name = t.getTableName(); r.alias = "";
                refs.add(r);
            }
        }
        return refs;
    }

    private List<ConditionRef> deleteConditionRefs(DeleteConfigJson d) {
        List<ConditionRef> refs = new ArrayList<>();
        if (d.getTables() != null) {
            for (DeleteConfigJson.TableDeleteConfig t : d.getTables()) {
                if (t.getConditions() == null) continue;
                for (DeleteConfigJson.ConditionItem c : t.getConditions()) {
                    ConditionRef r = new ConditionRef();
                    r.tableName = t.getTableName();
                    r.field = c.getField();
                    r.op = c.getOp();
                    r.paramKey = c.getParamKey();
                    refs.add(r);
                }
            }
        }
        return refs;
    }

    // ============================================================
    // Helpers
    // ============================================================

    private <T> T parseJson(String json, Class<T> clazz, T fallback) {
        if (json == null || json.isEmpty()) return fallback;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new InvalidExcelStructureException("configJson 反序列化失败: " + clazz.getSimpleName(), e);
        }
    }

    private String normalizeType(String type) {
        if (type == null) throw new InvalidExcelStructureException("接口 type 不能为空");
        String t = type.trim().toUpperCase();
        if ("QUERY".equals(t)) t = "SELECT"; // 兼容俗称
        return t;
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
