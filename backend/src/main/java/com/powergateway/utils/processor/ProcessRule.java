package com.powergateway.utils.processor;

import java.util.Map;

/**
 * M1-3 单条字段加工规则
 * <p>
 * 存入 convert_template.process_rule（JSON 数组），示例：
 * <pre>
 * [
 *   {"type": "TRIM",      "params": {"mode": "BOTH"}},
 *   {"type": "CASE",      "params": {"mode": "CAPITALIZE"}},
 *   {"type": "SUBSTRING", "params": {"start": "0", "length": "5"}}
 * ]
 * </pre>
 */
public class ProcessRule {

    /** 规则类型 */
    private ProcessRuleType type;

    /**
     * 规则参数，各类型所需参数：
     * <ul>
     *   <li>TRIM:      mode=BOTH|LEFT|RIGHT|ALL</li>
     *   <li>SUBSTRING: start=0, length=5</li>
     *   <li>PAD:       direction=LEFT|RIGHT, char=0, length=10</li>
     *   <li>CASE:      mode=UPPER|LOWER|CAPITALIZE</li>
     *   <li>TYPE_CAST: targetType=INTEGER|DECIMAL|STRING|BOOLEAN</li>
     * </ul>
     */
    private Map<String, String> params;

    public ProcessRule() {}

    public ProcessRule(ProcessRuleType type, Map<String, String> params) {
        this.type = type;
        this.params = params;
    }

    public ProcessRuleType getType() { return type; }
    public void setType(ProcessRuleType type) { this.type = type; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) { this.params = params; }
}
