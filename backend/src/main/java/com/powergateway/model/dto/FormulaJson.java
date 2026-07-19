package com.powergateway.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 公式配置 JSON 顶层结构（UX-C FN-03）。
 * 详细 schema：docs/02-设计/详细设计/2026-07-19-UX-C-field-mapping-formula-design.md §5.2
 */
@Data
public class FormulaJson {

    /** 版本号，当前恒为 1 */
    private Integer version = 1;

    /** CONDITION_GROUP / ARITH_EXPR */
    private String type;

    /** 仅 CONDITION_GROUP 使用：AND / OR / NOT */
    private String logic;

    /** 条件组的子节点，元素为 ConditionGroup 或 Condition */
    private List<Node> children = new ArrayList<>();

    /** 接口字段关联 */
    private List<InterfaceRef> interfaceRefs = new ArrayList<>();

    @Data
    public static class Node {
        /** CONDITION_GROUP / CONDITION */
        private String nodeType;

        // 嵌套条件组
        private String logic;
        private List<Node> children;

        // 条件行
        /** EQ / NE / GT / GE / LT / LE / LIKE / IN / BETWEEN / IS_NULL / IS_NOT_NULL */
        private String op;
        private FormulaOperand left;
        private FormulaOperand right;
    }

    @Data
    public static class InterfaceRef {
        private Long interfaceId;
        private String paramKey;
        private String columnHint;
    }
}
