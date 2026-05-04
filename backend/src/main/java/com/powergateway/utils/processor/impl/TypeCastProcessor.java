package com.powergateway.utils.processor.impl;

import com.powergateway.exception.BusinessException;
import com.powergateway.utils.processor.FieldProcessStrategy;
import com.powergateway.utils.processor.ProcessRuleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 类型转换处理器
 * <p>
 * 参数 targetType：
 * <ul>
 *   <li>STRING  — 转为字符串（直接返回，不做额外处理）</li>
 *   <li>INTEGER — 转为整数字符串（去掉小数部分）</li>
 *   <li>DECIMAL — 转为 BigDecimal 字符串（规范化小数格式）</li>
 *   <li>BOOLEAN — "true"/"1"/"yes" → "true"，其余 → "false"</li>
 * </ul>
 */
@Component
public class TypeCastProcessor implements FieldProcessStrategy {

    @Override
    public String process(String value, Map<String, String> params) {
        if (value == null) return null;
        String targetType = params != null
                ? params.getOrDefault("targetType", "STRING").toUpperCase()
                : "STRING";

        switch (targetType) {
            case "INTEGER":
                return castToInteger(value);
            case "DECIMAL":
                return castToDecimal(value);
            case "BOOLEAN":
                return castToBoolean(value);
            case "STRING":
            default:
                return value;
        }
    }

    private String castToInteger(String value) {
        try {
            return String.valueOf(new BigDecimal(value.trim()).longValue());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "TYPE_CAST 无法将 [" + value + "] 转换为 INTEGER");
        }
    }

    private String castToDecimal(String value) {
        try {
            return new BigDecimal(value.trim()).toPlainString();
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "TYPE_CAST 无法将 [" + value + "] 转换为 DECIMAL");
        }
    }

    private String castToBoolean(String value) {
        String v = value.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v)) {
            return "true";
        }
        return "false";
    }

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.TYPE_CAST;
    }
}
