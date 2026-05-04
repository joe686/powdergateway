package com.powergateway.utils.processor;

import java.util.Map;

/**
 * M1-3 字段加工策略接口
 * <p>
 * 每种加工类型实现此接口，FieldProcessor 按规则列表顺序依次调用。
 * 被 M1 报文转换和 M2 可视化接口开发（查询/插入/修改字段加工）共同复用。
 */
public interface FieldProcessStrategy {

    /**
     * 对字段值执行一次加工
     *
     * @param value  当前字段值（上一步的输出，或初始值）
     * @param params 该规则的参数 Map（key 为参数名，value 为参数值字符串）
     * @return 加工后的字段值
     */
    String process(String value, Map<String, String> params);

    /**
     * 返回该策略对应的规则类型枚举
     */
    ProcessRuleType ruleType();
}
