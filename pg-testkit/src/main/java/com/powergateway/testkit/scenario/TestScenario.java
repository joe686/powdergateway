package com.powergateway.testkit.scenario;

import lombok.Data;

import java.util.List;

/**
 * 测试场景定义。
 * <p>
 * 一个场景由若干步骤组成，按顺序执行。AI 可通过 /test/run-scenario API 提交 JSON 运行。
 */
@Data
public class TestScenario {

    /** 场景名称 */
    private String name;

    /** 场景描述 */
    private String description;

    /** 步骤列表 */
    private List<Step> steps;

    /**
     * 单个测试步骤。
     */
    @Data
    public static class Step {
        /** 步骤名称 */
        private String name;

        /** 动作类型：login / createTemplate / convert / dispatch / publishInterface / execInterface / verifyAudit / verifyDb / verifyMock */
        private String action;

        /** 请求参数（Map 形式，由执行器解析） */
        private Object params;

        /** 预期结果（可选，用于断言） */
        private Expectation expect;
    }

    @Data
    public static class Expectation {
        /** 预期响应 code（PG 统一响应体） */
        private Integer code;

        /** 预期响应体包含的文本 */
        private String bodyContains;

        /** 预期数据库表名（用于 verifyDb） */
        private String table;

        /** 预期数据库查询条件（SQL where 片段） */
        private String where;

        /** 预期数据库最小记录数 */
        private Integer minCount;

        /** 预期 Mock 收到的请求数 */
        private Integer mockRequestCount;
    }
}
