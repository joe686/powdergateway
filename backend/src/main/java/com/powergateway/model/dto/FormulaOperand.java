package com.powergateway.model.dto;

import lombok.Data;

/**
 * 公式操作数（UX-C FN-03）。
 * kind 枚举：COLUMN / REQUEST_PARAM / CONST / ARITH / FORMULA_REF
 * 详细约束见 docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md §5.2
 */
@Data
public class FormulaOperand {

    /** 操作数种类 */
    private String kind;

    // ─── kind=COLUMN ───
    private String tableName;
    private String columnName;

    // ─── kind=REQUEST_PARAM ───
    private String paramKey;

    // ─── kind=CONST ───
    /** NUMBER / STRING / BOOLEAN / STRING_ARRAY / NUMBER_ARRAY */
    private String constType;
    /** 字面值：单值或数组，Jackson 自动映射 */
    private Object constValue;

    // ─── kind=ARITH ───
    private ArithExpr expr;

    // ─── kind=FORMULA_REF ───
    private Long formulaId;

    @Data
    public static class ArithExpr {
        /** ADD / SUB / MUL / DIV */
        private String op;
        private FormulaOperand left;
        private FormulaOperand right;
    }
}
