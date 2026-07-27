package com.powergateway.utils.processor;

/**
 * M1-3 字段加工规则类型枚举
 */
public enum ProcessRuleType {

    /** 去空格：前后/全部 */
    TRIM,

    /** 截位：按起始位和长度截取子串 */
    SUBSTRING,

    /** 补位：左补或右补指定字符到目标长度 */
    PAD,

    /** 大小写转换：全大写/全小写/首字母大写 */
    CASE,

    /** 类型转换：字符串 ↔ 整数/小数/布尔 */
    TYPE_CAST,

    /**
     * 字典转换（FN-12 · v0.2.0 ②）
     * params: system (对端系统标识) · dictKey (字典标识) · direction (1=出向 2=入向)
     */
    DICT_MAP
}
