package com.powergateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.*;
import com.powergateway.model.dto.FormulaValidateResult.ErrorItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 字段公式静态校验器（UX-C FN-03）。
 * 一次性收集所有错误，不短路。
 * 校验规则详见 docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md §5.2
 */
@Slf4j
@Service
public class FormulaValidator {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private TableMetaService tableMetaService;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    private static final Set<String> ROOT_TYPES = new HashSet<>(Arrays.asList("CONDITION_GROUP", "ARITH_EXPR"));
    private static final Set<String> LOGIC_OPS = new HashSet<>(Arrays.asList("AND", "OR", "NOT"));
    private static final Set<String> COND_OPS = new HashSet<>(Arrays.asList(
            "EQ", "NE", "GT", "GE", "LT", "LE", "LIKE", "IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL"));
    private static final Set<String> ARITH_OPS = new HashSet<>(Arrays.asList("ADD", "SUB", "MUL", "DIV"));
    private static final Set<String> CONST_TYPES = new HashSet<>(Arrays.asList(
            "NUMBER", "STRING", "BOOLEAN", "STRING_ARRAY", "NUMBER_ARRAY"));

    public FormulaValidateResult validate(FormulaValidateRequest req) {
        FormulaValidateResult result = new FormulaValidateResult();

        if (req == null || req.getFormulaJson() == null || req.getFormulaJson().trim().isEmpty()) {
            result.addError("$", "公式配置不能为空");
            return result;
        }

        FormulaJson root;
        try {
            root = objectMapper.readValue(req.getFormulaJson(), FormulaJson.class);
        } catch (Exception e) {
            result.addError("$", "公式 JSON 结构非法：" + e.getMessage());
            return result;
        }

        if (root.getType() == null || !ROOT_TYPES.contains(root.getType())) {
            result.addError("$.type", "根节点 type 必须为 CONDITION_GROUP 或 ARITH_EXPR");
            return result;
        }

        // 收集所有 COLUMN 引用，供后续元数据校验
        List<ColumnRefWithPath> columnRefs = new ArrayList<>();

        if ("CONDITION_GROUP".equals(root.getType())) {
            validateGroup(root.getLogic(), root.getChildren(), "$", result, columnRefs);
        }

        // 元数据校验：按 dbConnectionId + tableName 分组
        if (req.getDbConnectionId() != null && !columnRefs.isEmpty()) {
            try {
                List<TableMeta> tables = tableMetaService.getTables(req.getDbConnectionId());
                Map<String, Set<String>> tableColumnMap = new HashMap<>();
                for (TableMeta t : tables) {
                    Set<String> cols = new HashSet<>();
                    if (t.getColumns() != null) {
                        for (ColumnMeta c : t.getColumns()) cols.add(c.getName().toLowerCase());
                    }
                    tableColumnMap.put(t.getTableName().toLowerCase(), cols);
                }
                for (ColumnRefWithPath ref : columnRefs) {
                    String tn = ref.tableName == null ? "" : ref.tableName.toLowerCase();
                    String cn = ref.columnName == null ? "" : ref.columnName.toLowerCase();
                    Set<String> cols = tableColumnMap.get(tn);
                    if (cols == null) {
                        result.addError(ref.path,
                                "列引用的表 '" + ref.tableName + "' 在数据库连接中不存在");
                    } else if (!cols.contains(cn)) {
                        result.addError(ref.path,
                                "列 '" + ref.columnName + "' 在表 '" + ref.tableName + "' 中不存在");
                    }
                }
            } catch (Exception e) {
                log.warn("[UX-C] 元数据校验失败: {}", e.getMessage());
                result.addError("$", "无法查询数据库元数据：" + e.getMessage());
            }
        }

        // 接口引用校验
        if (root.getInterfaceRefs() != null) {
            for (int i = 0; i < root.getInterfaceRefs().size(); i++) {
                FormulaJson.InterfaceRef ir = root.getInterfaceRefs().get(i);
                if (ir.getInterfaceId() == null) continue;
                InterfaceConfig cfg = interfaceConfigMapper.selectById(ir.getInterfaceId());
                if (cfg == null) {
                    result.addError("$.interfaceRefs[" + i + "].interfaceId",
                            "引用的接口 " + ir.getInterfaceId() + " 不存在或已删除");
                }
            }
        }

        return result;
    }

    private void validateGroup(String logic, List<FormulaJson.Node> children,
                                String path, FormulaValidateResult result,
                                List<ColumnRefWithPath> columnRefs) {
        if (logic == null || !LOGIC_OPS.contains(logic)) {
            result.addError(path + ".logic", "logic 必须为 AND / OR / NOT");
        }
        if (children == null || children.isEmpty()) {
            result.addError(path + ".children", "条件组子节点不能为空");
            return;
        }
        if ("NOT".equals(logic) && children.size() > 1) {
            result.addError(path + ".children", "NOT 逻辑只能包含一个子节点");
        }
        for (int i = 0; i < children.size(); i++) {
            validateNode(children.get(i), path + ".children[" + i + "]", result, columnRefs);
        }
    }

    private void validateNode(FormulaJson.Node node, String path,
                               FormulaValidateResult result,
                               List<ColumnRefWithPath> columnRefs) {
        if (node.getNodeType() == null) {
            result.addError(path + ".nodeType", "节点 nodeType 必填");
            return;
        }
        if ("CONDITION_GROUP".equals(node.getNodeType())) {
            validateGroup(node.getLogic(), node.getChildren(), path, result, columnRefs);
            return;
        }
        if (!"CONDITION".equals(node.getNodeType())) {
            result.addError(path + ".nodeType", "nodeType 必须为 CONDITION_GROUP 或 CONDITION");
            return;
        }
        // CONDITION
        if (node.getOp() == null || !COND_OPS.contains(node.getOp())) {
            result.addError(path + ".op", "op 必须为受支持的比较操作符");
            return;
        }
        // left 必填
        if (node.getLeft() == null) {
            result.addError(path + ".left", "左操作数必填");
        } else {
            validateOperand(node.getLeft(), path + ".left", result, columnRefs);
        }
        // right：IS_NULL / IS_NOT_NULL 不校验；其它必填
        if ("IS_NULL".equals(node.getOp()) || "IS_NOT_NULL".equals(node.getOp())) {
            return;
        }
        if (node.getRight() == null) {
            result.addError(path + ".right", "右操作数必填");
            return;
        }
        validateOperand(node.getRight(), path + ".right", result, columnRefs);

        // IN / BETWEEN 特殊约束
        if ("IN".equals(node.getOp())) {
            if (!"CONST".equals(node.getRight().getKind())
                    || !(node.getRight().getConstValue() instanceof List)
                    || !("STRING_ARRAY".equals(node.getRight().getConstType())
                         || "NUMBER_ARRAY".equals(node.getRight().getConstType()))) {
                result.addError(path + ".right",
                        "IN 操作符的右操作数必须为 STRING_ARRAY 或 NUMBER_ARRAY 常量");
            }
        }
        if ("BETWEEN".equals(node.getOp())) {
            Object cv = node.getRight().getConstValue();
            if (!"NUMBER_ARRAY".equals(node.getRight().getConstType())
                    || !(cv instanceof List)
                    || ((List<?>) cv).size() != 2) {
                result.addError(path + ".right",
                        "BETWEEN 操作符的右操作数必须为长度为 2 的 NUMBER_ARRAY");
            }
        }
    }

    private void validateOperand(FormulaOperand op, String path,
                                  FormulaValidateResult result,
                                  List<ColumnRefWithPath> columnRefs) {
        if (op.getKind() == null) {
            result.addError(path + ".kind", "操作数 kind 必填");
            return;
        }
        switch (op.getKind()) {
            case "COLUMN":
                if (op.getTableName() == null || op.getColumnName() == null) {
                    result.addError(path, "COLUMN 操作数需 tableName + columnName");
                } else {
                    ColumnRefWithPath ref = new ColumnRefWithPath();
                    ref.tableName = op.getTableName();
                    ref.columnName = op.getColumnName();
                    ref.path = path;
                    columnRefs.add(ref);
                }
                break;
            case "REQUEST_PARAM":
                if (op.getParamKey() == null || op.getParamKey().isEmpty()) {
                    result.addError(path + ".paramKey", "REQUEST_PARAM 需 paramKey");
                }
                break;
            case "CONST":
                if (op.getConstType() == null || !CONST_TYPES.contains(op.getConstType())) {
                    result.addError(path + ".constType", "CONST 需合法 constType");
                }
                break;
            case "ARITH":
                if (op.getExpr() == null || op.getExpr().getOp() == null
                        || !ARITH_OPS.contains(op.getExpr().getOp())) {
                    result.addError(path + ".expr", "ARITH 需 op ∈ {ADD,SUB,MUL,DIV}");
                } else {
                    if (op.getExpr().getLeft() != null) {
                        validateOperand(op.getExpr().getLeft(), path + ".expr.left", result, columnRefs);
                    } else {
                        result.addError(path + ".expr.left", "ARITH 左操作数必填");
                    }
                    if (op.getExpr().getRight() != null) {
                        validateOperand(op.getExpr().getRight(), path + ".expr.right", result, columnRefs);
                    } else {
                        result.addError(path + ".expr.right", "ARITH 右操作数必填");
                    }
                }
                break;
            case "FORMULA_REF":
                if (op.getFormulaId() == null) {
                    result.addError(path + ".formulaId", "FORMULA_REF 需 formulaId");
                }
                break;
            default:
                result.addError(path + ".kind",
                        "kind 必须为 COLUMN/REQUEST_PARAM/CONST/ARITH/FORMULA_REF 之一");
        }
    }

    private static class ColumnRefWithPath {
        String tableName;
        String columnName;
        String path;
    }
}
