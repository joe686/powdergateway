package com.powergateway.utils.processor.impl;

import com.powergateway.exception.BusinessException;
import com.powergateway.utils.processor.FieldProcessStrategy;
import com.powergateway.utils.processor.ProcessRuleType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 截位处理器
 * <p>
 * 参数：
 * <ul>
 *   <li>start  — 起始索引（0-based，默认 0）</li>
 *   <li>length — 截取长度（默认截到末尾）</li>
 * </ul>
 */
@Component
public class SubstringProcessor implements FieldProcessStrategy {

    @Override
    public String process(String value, Map<String, String> params) {
        if (value == null) return null;
        int start = 0;
        int length = value.length();

        if (params != null) {
            if (params.containsKey("start")) {
                try {
                    start = Integer.parseInt(params.get("start"));
                } catch (NumberFormatException e) {
                    throw new BusinessException(400, "SUBSTRING 参数 start 必须为整数");
                }
            }
            if (params.containsKey("length")) {
                try {
                    length = Integer.parseInt(params.get("length"));
                } catch (NumberFormatException e) {
                    throw new BusinessException(400, "SUBSTRING 参数 length 必须为整数");
                }
            }
        }

        if (start < 0 || start > value.length()) {
            throw new BusinessException(400, "SUBSTRING start 超出字符串范围，start=" + start + "，字符串长度=" + value.length());
        }
        int end = Math.min(start + length, value.length());
        return value.substring(start, end);
    }

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.SUBSTRING;
    }
}
