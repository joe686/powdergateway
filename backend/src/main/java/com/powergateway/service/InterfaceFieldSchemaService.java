package com.powergateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.*;
import com.powergateway.utils.ExcelExportUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FN-07 字段清单导出 Service。
 * 根据接口配置 JSON 解析请求字段与响应字段，生成双 Sheet Excel。
 */
@Service
@RequiredArgsConstructor
public class InterfaceFieldSchemaService {

    private final TableMetaService tableMetaService;
    private final ObjectMapper objectMapper;

    public byte[] exportExcel(InterfaceConfig config) {
        if (config == null) throw new BusinessException(404, "接口配置不存在");

        List<FieldSchemaRow> reqRows;
        List<FieldSchemaRow> respRows;

        try {
            switch (config.getType()) {
                case "SELECT": {
                    QueryConfigJson q = objectMapper.readValue(config.getConfigJson(), QueryConfigJson.class);
                    reqRows  = buildFromQueryConditions(q, config.getDbConnectionId());
                    respRows = buildFromQueryFields(q, config.getDbConnectionId());
                    break;
                }
                case "INSERT": {
                    InsertConfigJson i = objectMapper.readValue(config.getConfigJson(), InsertConfigJson.class);
                    reqRows  = buildFromInsertFields(i, config.getDbConnectionId());
                    respRows = affectedRowsSchema();
                    break;
                }
                case "UPDATE": {
                    UpdateConfigJson u = objectMapper.readValue(config.getConfigJson(), UpdateConfigJson.class);
                    reqRows  = buildFromUpdateFieldsAndConditions(u, config.getDbConnectionId());
                    respRows = affectedRowsSchema();
                    break;
                }
                case "DELETE": {
                    DeleteConfigJson d = objectMapper.readValue(config.getConfigJson(), DeleteConfigJson.class);
                    reqRows  = buildFromDeleteConditions(d, config.getDbConnectionId());
                    respRows = affectedRowsSchema();
                    break;
                }
                default:
                    throw new BusinessException(400, "不支持的接口类型: " + config.getType());
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(400, "配置解析失败: " + e.getMessage());
        }

        List<ExcelExportUtil.Column<FieldSchemaRow>> cols = Arrays.asList(
            new ExcelExportUtil.Column<>("序号",       FieldSchemaRow::getIndex),
            new ExcelExportUtil.Column<>("英文字段名", FieldSchemaRow::getFieldName),
            new ExcelExportUtil.Column<>("中文含义",   FieldSchemaRow::getComment),
            new ExcelExportUtil.Column<>("数据类型",   FieldSchemaRow::getDataType),
            new ExcelExportUtil.Column<>("长度",       FieldSchemaRow::getLength),
            new ExcelExportUtil.Column<>("必填",       FieldSchemaRow::getRequired),
            new ExcelExportUtil.Column<>("备注",       FieldSchemaRow::getSource)
        );

        LinkedHashMap<String, ExcelExportUtil.SheetSpec<?>> sheets = new LinkedHashMap<>();
        sheets.put("请求字段", new ExcelExportUtil.SheetSpec<>(cols, reqRows));
        sheets.put("响应字段", new ExcelExportUtil.SheetSpec<>(cols, respRows));
        return ExcelExportUtil.exportMultiSheet(sheets);
    }

    // ─── 固定响应模式 ───────────────────────────────────────────────────────────────

    private List<FieldSchemaRow> affectedRowsSchema() {
        return Collections.singletonList(
            new FieldSchemaRow(1, "affectedRows", "影响行数", "INT", "-", "Y", "-"));
    }

    // ─── 辅助：取表列元数据 Map ─────────────────────────────────────────────────────

    private Map<String, ColumnMeta> loadColumnMap(Long dbId, String tableName) {
        try {
            List<TableMeta> tables = tableMetaService.getTables(dbId);
            for (TableMeta t : tables) {
                if (tableName.equals(t.getTableName()) && t.getColumns() != null) {
                    Map<String, ColumnMeta> map = new LinkedHashMap<>();
                    for (ColumnMeta c : t.getColumns()) map.put(c.getName(), c);
                    return map;
                }
            }
        } catch (Exception ignore) { /* 找不到时静默，下面用空 map */ }
        return Collections.emptyMap();
    }

    private FieldSchemaRow fromColumn(int idx, String columnName, Map<String, ColumnMeta> colMap, String source) {
        ColumnMeta meta = colMap.get(columnName);
        String comment  = meta != null ? (meta.getRemarks() != null ? meta.getRemarks() : "") : "";
        String type     = meta != null ? meta.getType() : "";
        String required = meta != null ? (!meta.isNullable() ? "Y" : "N") : "N";
        return new FieldSchemaRow(idx, columnName, comment, type, "-", required, source);
    }

    // ─── SELECT ──────────────────────────────────────────────────────────────────

    private List<FieldSchemaRow> buildFromQueryConditions(QueryConfigJson q, Long dbId) {
        List<FieldSchemaRow> rows = new ArrayList<>();
        if (q.getConditions() == null) return rows;
        // 主表
        String mainTable = q.getTables() != null && !q.getTables().isEmpty()
            ? q.getTables().get(0).getName() : null;
        Map<String, ColumnMeta> colMap = mainTable != null ? loadColumnMap(dbId, mainTable) : Collections.emptyMap();
        int idx = 1;
        for (QueryConfigJson.ConditionDef cond : q.getConditions()) {
            String col = cond.getField() != null ? cond.getField().replaceFirst(".*\\.", "") : "?";
            rows.add(fromColumn(idx++, col, colMap, "REQUEST"));
        }
        return rows;
    }

    private List<FieldSchemaRow> buildFromQueryFields(QueryConfigJson q, Long dbId) {
        List<FieldSchemaRow> rows = new ArrayList<>();
        if (q.getFields() == null || q.getFields().isEmpty()) return rows;
        String mainTable = q.getTables() != null && !q.getTables().isEmpty()
            ? q.getTables().get(0).getName() : null;
        Map<String, ColumnMeta> colMap = mainTable != null ? loadColumnMap(dbId, mainTable) : Collections.emptyMap();
        int idx = 1;
        for (QueryConfigJson.FieldDef f : q.getFields()) {
            rows.add(fromColumn(idx++, f.getColumn(), colMap, "-"));
        }
        return rows;
    }

    // ─── INSERT ──────────────────────────────────────────────────────────────────

    private List<FieldSchemaRow> buildFromInsertFields(InsertConfigJson i, Long dbId) {
        List<FieldSchemaRow> rows = new ArrayList<>();
        if (i.getTables() == null) return rows;
        int idx = 1;
        for (InsertConfigJson.TableInsertConfig t : i.getTables()) {
            Map<String, ColumnMeta> colMap = loadColumnMap(dbId, t.getTableName());
            if (t.getFields() == null) continue;
            for (InsertConfigJson.FieldInsertConfig f : t.getFields()) {
                String src = buildSource(f.getSourceType(), f.getParamKey(), f.getConstValue(), f.getExpression());
                rows.add(fromColumn(idx++, f.getColumn(), colMap, src));
            }
        }
        return rows;
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────────

    private List<FieldSchemaRow> buildFromUpdateFieldsAndConditions(UpdateConfigJson u, Long dbId) {
        List<FieldSchemaRow> rows = new ArrayList<>();
        int idx = 1;
        if (u.getTables() != null) {
            for (UpdateConfigJson.TableUpdateConfig t : u.getTables()) {
                Map<String, ColumnMeta> colMap = loadColumnMap(dbId, t.getTableName());
                if (t.getFields() == null) continue;
                for (InsertConfigJson.FieldInsertConfig f : t.getFields()) {
                    String src = buildSource(f.getSourceType(), f.getParamKey(), f.getConstValue(), f.getExpression());
                    rows.add(fromColumn(idx++, f.getColumn(), colMap, src));
                }
            }
        }
        if (u.getConditions() != null) {
            for (UpdateConfigJson.ConditionConfig c : u.getConditions()) {
                Map<String, ColumnMeta> colMap = c.getTableName() != null
                    ? loadColumnMap(dbId, c.getTableName()) : Collections.emptyMap();
                rows.add(fromColumn(idx++, c.getField(), colMap, "REQUEST（条件）"));
            }
        }
        return rows;
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────────

    private List<FieldSchemaRow> buildFromDeleteConditions(DeleteConfigJson d, Long dbId) {
        List<FieldSchemaRow> rows = new ArrayList<>();
        if (d.getTables() == null) return rows;
        int idx = 1;
        for (DeleteConfigJson.TableDeleteConfig t : d.getTables()) {
            Map<String, ColumnMeta> colMap = loadColumnMap(dbId, t.getTableName());
            if (t.getConditions() == null) continue;
            for (DeleteConfigJson.ConditionItem c : t.getConditions()) {
                rows.add(fromColumn(idx++, c.getField(), colMap, "REQUEST（条件）"));
            }
        }
        return rows;
    }

    // ─── 辅助 ────────────────────────────────────────────────────────────────────

    private String buildSource(String sourceType, String paramKey, String constValue, String expression) {
        if ("REQUEST".equals(sourceType)) return "REQUEST";
        if ("CONST".equals(sourceType))   return "CONST=" + (constValue != null ? constValue : "");
        if ("CALC".equals(sourceType))    return "CALC=" + (expression != null ? expression : "");
        return "-";
    }
}
