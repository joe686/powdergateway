package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import com.powergateway.utils.processor.FieldProcessStrategy;
import com.powergateway.utils.processor.ProcessRule;
import com.powergateway.utils.processor.ProcessRuleType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * M1-3 字段加工引擎（Spring Bean）
 * <p>
 * 按规则列表顺序依次执行，每条规则的输出作为下一条规则的输入。
 * 启动时自动收集所有 {@link FieldProcessStrategy} 实现 Bean，
 * 以规则类型为 Key 存入 Map，O(1) 查找。
 * <p>
 * 复用约定：M2 查询/插入/修改接口需要字段加工时，直接注入此 Bean，不得重复实现。
 * <p>
 * 用法示例：
 * <pre>
 * List&lt;ProcessRule&gt; rules = List.of(
 *     new ProcessRule(ProcessRuleType.TRIM,      Map.of("mode", "BOTH")),
 *     new ProcessRule(ProcessRuleType.CASE,      Map.of("mode", "CAPITALIZE")),
 *     new ProcessRule(ProcessRuleType.SUBSTRING, Map.of("start", "0", "length", "5"))
 * );
 * String result = fieldProcessor.process("  hello world  ", rules);
 * // → "Hello"
 * </pre>
 */
@Component
public class FieldProcessor {

    /** 规则类型 → 策略实现，启动时注入 */
    private final Map<ProcessRuleType, FieldProcessStrategy> strategyMap;

    public FieldProcessor(List<FieldProcessStrategy> strategies) {
        strategyMap = new EnumMap<>(ProcessRuleType.class);
        for (FieldProcessStrategy s : strategies) {
            strategyMap.put(s.ruleType(), s);
        }
    }

    /**
     * 对单个字段值应用规则列表
     *
     * @param value 初始字段值
     * @param rules 规则列表（顺序执行）
     * @return 所有规则处理后的最终值
     */
    public String process(String value, List<ProcessRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return value;
        }
        String current = value;
        for (ProcessRule rule : rules) {
            if (rule.getType() == null) {
                throw new BusinessException(400, "规则类型不能为空");
            }
            FieldProcessStrategy strategy = strategyMap.get(rule.getType());
            if (strategy == null) {
                throw new BusinessException(400, "不支持的规则类型: " + rule.getType());
            }
            current = strategy.process(current, rule.getParams());
        }
        return current;
    }

    /**
     * 批量处理多个字段：对 fieldValues 中每个字段按其对应的规则列表加工
     *
     * @param fieldValues 字段名 → 当前值
     * @param fieldRules  字段名 → 规则列表
     * @return 加工后的字段名 → 值 Map
     */
    public Map<String, String> processBatch(Map<String, String> fieldValues,
                                            Map<String, List<ProcessRule>> fieldRules) {
        if (fieldValues == null) return null;
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
            String fieldName = entry.getKey();
            List<ProcessRule> rules = fieldRules != null ? fieldRules.get(fieldName) : null;
            result.put(fieldName, process(entry.getValue(), rules));
        }
        return result;
    }
}
