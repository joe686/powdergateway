package com.powergateway.utils.processor.impl;

import com.powergateway.exception.BusinessException;
import com.powergateway.utils.processor.FieldProcessStrategy;
import com.powergateway.utils.processor.ProcessRuleType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 补位处理器
 * <p>
 * 参数：
 * <ul>
 *   <li>direction — LEFT（左补）或 RIGHT（右补），默认 LEFT</li>
 *   <li>char      — 填充字符（默认 "0"）</li>
 *   <li>length    — 目标总长度（必填）</li>
 * </ul>
 * 若值已达到或超过目标长度，直接返回原值（不截断）。
 */
@Component
public class PadProcessor implements FieldProcessStrategy {

    @Override
    public String process(String value, Map<String, String> params) {
        if (value == null) return null;
        if (params == null || !params.containsKey("length")) {
            throw new BusinessException(400, "PAD 规则缺少必填参数 length");
        }

        int targetLength;
        try {
            targetLength = Integer.parseInt(params.get("length"));
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "PAD 参数 length 必须为整数");
        }

        if (targetLength <= 0) {
            throw new BusinessException(400, "PAD 参数 length 必须大于 0");
        }

        if (value.length() >= targetLength) {
            return value;
        }

        String direction = params.getOrDefault("direction", "LEFT").toUpperCase();
        String padChar   = params.getOrDefault("char", "0");
        if (padChar.isEmpty()) padChar = "0";
        // 只取补位字符的第一个字符
        char fill = padChar.charAt(0);

        int padCount = targetLength - value.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < padCount; i++) {
            sb.append(fill);
        }

        if ("RIGHT".equals(direction)) {
            return value + sb.toString();
        } else {
            return sb.toString() + value;
        }
    }

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.PAD;
    }
}
