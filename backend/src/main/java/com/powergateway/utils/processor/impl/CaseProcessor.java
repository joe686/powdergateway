package com.powergateway.utils.processor.impl;

import com.powergateway.utils.processor.FieldProcessStrategy;
import com.powergateway.utils.processor.ProcessRuleType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 大小写转换处理器
 * <p>
 * 参数 mode：
 * <ul>
 *   <li>UPPER      — 全部大写</li>
 *   <li>LOWER      — 全部小写</li>
 *   <li>CAPITALIZE — 首字母大写，其余小写</li>
 * </ul>
 */
@Component
public class CaseProcessor implements FieldProcessStrategy {

    @Override
    public String process(String value, Map<String, String> params) {
        if (value == null) return null;
        String mode = params != null ? params.getOrDefault("mode", "UPPER").toUpperCase() : "UPPER";
        switch (mode) {
            case "LOWER":
                return value.toLowerCase();
            case "CAPITALIZE":
                return capitalize(value);
            case "UPPER":
            default:
                return value.toUpperCase();
        }
    }

    /** 首字母大写，其余小写 */
    private String capitalize(String value) {
        if (value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.CASE;
    }
}
