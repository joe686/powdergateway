package com.powergateway.utils.processor.impl;

import com.powergateway.utils.processor.FieldProcessStrategy;
import com.powergateway.utils.processor.ProcessRuleType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 去空格处理器
 * <p>
 * 参数 mode：
 * <ul>
 *   <li>BOTH（默认）— 去除首尾空白（String.trim()）</li>
 *   <li>LEFT  — 去除左侧空白</li>
 *   <li>RIGHT — 去除右侧空白</li>
 *   <li>ALL   — 去除所有空白字符</li>
 * </ul>
 */
@Component
public class TrimProcessor implements FieldProcessStrategy {

    @Override
    public String process(String value, Map<String, String> params) {
        if (value == null) return null;
        String mode = params != null ? params.getOrDefault("mode", "BOTH").toUpperCase() : "BOTH";
        switch (mode) {
            case "LEFT":
                return value.replaceAll("^\\s+", "");
            case "RIGHT":
                return value.replaceAll("\\s+$", "");
            case "ALL":
                return value.replaceAll("\\s+", "");
            case "BOTH":
            default:
                return value.trim();
        }
    }

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.TRIM;
    }
}
