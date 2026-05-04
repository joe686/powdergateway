package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.utils.FieldProcessor;
import com.powergateway.utils.processor.ProcessRule;
import com.powergateway.utils.processor.ProcessRuleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * M1-3 字段加工规则引擎 REST 接口
 */
@Tag(name = "M1-3 字段加工规则引擎", description = "字段值加工：截位/补位/大小写/去空格/类型转换，支持多规则叠加")
@RestController
@RequestMapping("/api/field-process")
public class FieldProcessController {

    private final FieldProcessor fieldProcessor;

    public FieldProcessController(FieldProcessor fieldProcessor) {
        this.fieldProcessor = fieldProcessor;
    }

    /**
     * 对单个字段值执行规则链
     * <p>
     * 请求体示例：
     * <pre>
     * {
     *   "value": "  hello world  ",
     *   "rules": [
     *     {"type": "TRIM",      "params": {"mode": "BOTH"}},
     *     {"type": "CASE",      "params": {"mode": "CAPITALIZE"}},
     *     {"type": "SUBSTRING", "params": {"start": "0", "length": "5"}}
     *   ]
     * }
     * </pre>
     */
    @Operation(summary = "执行字段加工规则链", description = "按规则列表顺序依次处理字段值，每条规则的输出作为下一条规则的输入")
    @PostMapping("/execute")
    public Result<Map<String, String>> execute(@RequestBody ExecuteRequest req) {
        String result = fieldProcessor.process(req.getValue(), req.getRules());
        Map<String, String> data = new LinkedHashMap<>();
        data.put("input",  req.getValue());
        data.put("output", result);
        return Result.success(data);
    }

    /**
     * 分步预览：返回每条规则执行后的中间结果，便于前端实时预览
     */
    @Operation(summary = "分步预览各规则执行效果", description = "返回初始值及每一步规则执行后的中间值，便于前端实时预览")
    @PostMapping("/preview")
    public Result<List<StepResult>> preview(@RequestBody ExecuteRequest req) {
        List<StepResult> steps = new ArrayList<>();
        String current = req.getValue();
        steps.add(new StepResult(0, "初始值", null, current));

        List<ProcessRule> rules = req.getRules();
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                ProcessRule rule = rules.get(i);
                current = fieldProcessor.process(current, Collections.singletonList(rule));
                steps.add(new StepResult(i + 1, rule.getType() != null ? rule.getType().name() : "UNKNOWN",
                        rule.getParams(), current));
            }
        }
        return Result.success(steps);
    }

    /**
     * 获取所有支持的规则类型及其参数说明
     */
    @Operation(summary = "获取可用规则类型列表")
    @GetMapping("/rule-types")
    public Result<List<RuleTypeDesc>> ruleTypes() {
        List<RuleTypeDesc> list = Arrays.asList(
                new RuleTypeDesc(ProcessRuleType.TRIM, "去空格",
                        "mode: BOTH（首尾）| LEFT（左侧）| RIGHT（右侧）| ALL（全部）"),
                new RuleTypeDesc(ProcessRuleType.SUBSTRING, "截位",
                        "start: 起始索引（0-based）, length: 截取长度"),
                new RuleTypeDesc(ProcessRuleType.PAD, "补位",
                        "direction: LEFT|RIGHT, char: 填充字符（默认0）, length: 目标总长度"),
                new RuleTypeDesc(ProcessRuleType.CASE, "大小写转换",
                        "mode: UPPER（全大写）| LOWER（全小写）| CAPITALIZE（首字母大写）"),
                new RuleTypeDesc(ProcessRuleType.TYPE_CAST, "类型转换",
                        "targetType: STRING | INTEGER | DECIMAL | BOOLEAN")
        );
        return Result.success(list);
    }

    // ==================== 请求/响应 DTO ====================

    public static class ExecuteRequest {
        private String value;
        private List<ProcessRule> rules;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public List<ProcessRule> getRules() { return rules; }
        public void setRules(List<ProcessRule> rules) { this.rules = rules; }
    }

    public static class StepResult {
        private int step;
        private String ruleName;
        private Map<String, String> params;
        private String output;

        public StepResult(int step, String ruleName, Map<String, String> params, String output) {
            this.step = step;
            this.ruleName = ruleName;
            this.params = params;
            this.output = output;
        }

        public int getStep() { return step; }
        public String getRuleName() { return ruleName; }
        public Map<String, String> getParams() { return params; }
        public String getOutput() { return output; }
    }

    public static class RuleTypeDesc {
        private ProcessRuleType type;
        private String label;
        private String paramsDesc;

        public RuleTypeDesc(ProcessRuleType type, String label, String paramsDesc) {
            this.type = type;
            this.label = label;
            this.paramsDesc = paramsDesc;
        }

        public ProcessRuleType getType() { return type; }
        public String getLabel() { return label; }
        public String getParamsDesc() { return paramsDesc; }
    }
}
