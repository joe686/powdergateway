package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.utils.FieldProcessor;
import com.powergateway.utils.processor.ProcessRule;
import com.powergateway.utils.processor.ProcessRuleType;
import com.powergateway.utils.processor.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1-3 字段加工规则引擎单元测试
 * <p>
 * 纯工具类测试，无需 Spring 上下文。
 */
@DisplayName("M1-3 字段加工规则引擎")
class M13FieldProcessorTest {

    private FieldProcessor fieldProcessor;

    @BeforeEach
    void setUp() {
        List<com.powergateway.utils.processor.FieldProcessStrategy> strategies = Arrays.asList(
                new TrimProcessor(),
                new SubstringProcessor(),
                new PadProcessor(),
                new CaseProcessor(),
                new TypeCastProcessor()
        );
        fieldProcessor = new FieldProcessor(strategies);
    }

    // ==================== 验收标准用例 ====================

    @Test
    @DisplayName("验收标准：去空格 → 首字母大写 → 截取前5位 → 输出 Hello")
    void acceptanceCriteria() {
        List<ProcessRule> rules = Arrays.asList(
                rule(ProcessRuleType.TRIM,      map("mode", "BOTH")),
                rule(ProcessRuleType.CASE,      map("mode", "CAPITALIZE")),
                rule(ProcessRuleType.SUBSTRING, map("start", "0", "length", "5"))
        );
        String result = fieldProcessor.process("  hello world  ", rules);
        assertEquals("Hello", result, "验收标准用例输出应为 Hello");
    }

    // ==================== TrimProcessor ====================

    @Test
    @DisplayName("TRIM - BOTH：去除首尾空白")
    void trimBoth() {
        assertEquals("hello", process("  hello  ", ProcessRuleType.TRIM, map("mode", "BOTH")));
    }

    @Test
    @DisplayName("TRIM - LEFT：去除左侧空白")
    void trimLeft() {
        assertEquals("hello  ", process("  hello  ", ProcessRuleType.TRIM, map("mode", "LEFT")));
    }

    @Test
    @DisplayName("TRIM - RIGHT：去除右侧空白")
    void trimRight() {
        assertEquals("  hello", process("  hello  ", ProcessRuleType.TRIM, map("mode", "RIGHT")));
    }

    @Test
    @DisplayName("TRIM - ALL：去除所有空白（含中间空格）")
    void trimAll() {
        assertEquals("helloworld", process("  hello world  ", ProcessRuleType.TRIM, map("mode", "ALL")));
    }

    @Test
    @DisplayName("TRIM - 默认 mode 等同 BOTH")
    void trimDefault() {
        assertEquals("hello", process("  hello  ", ProcessRuleType.TRIM, Collections.emptyMap()));
    }

    // ==================== SubstringProcessor ====================

    @Test
    @DisplayName("SUBSTRING - 从0截取5位")
    void substringBasic() {
        assertEquals("Hello", process("Hello World", ProcessRuleType.SUBSTRING, map("start", "0", "length", "5")));
    }

    @Test
    @DisplayName("SUBSTRING - 从中间截取")
    void substringMiddle() {
        assertEquals("World", process("Hello World", ProcessRuleType.SUBSTRING, map("start", "6", "length", "5")));
    }

    @Test
    @DisplayName("SUBSTRING - length 超出字符串长度，截到末尾")
    void substringOverLength() {
        assertEquals("lo", process("Hello", ProcessRuleType.SUBSTRING, map("start", "3", "length", "99")));
    }

    @Test
    @DisplayName("SUBSTRING - start 超出范围抛出 BusinessException")
    void substringOutOfRange() {
        assertThrows(BusinessException.class, () ->
                process("Hi", ProcessRuleType.SUBSTRING, map("start", "10", "length", "5")));
    }

    // ==================== PadProcessor ====================

    @Test
    @DisplayName("PAD - LEFT 左补0到8位")
    void padLeft() {
        assertEquals("00001234", process("1234", ProcessRuleType.PAD,
                map("direction", "LEFT", "char", "0", "length", "8")));
    }

    @Test
    @DisplayName("PAD - RIGHT 右补空格到10位")
    void padRight() {
        assertEquals("hello     ", process("hello", ProcessRuleType.PAD,
                map("direction", "RIGHT", "char", " ", "length", "10")));
    }

    @Test
    @DisplayName("PAD - 值已达目标长度，不做处理")
    void padAlreadyFull() {
        assertEquals("12345", process("12345", ProcessRuleType.PAD,
                map("direction", "LEFT", "char", "0", "length", "5")));
    }

    @Test
    @DisplayName("PAD - 缺少 length 参数抛出 BusinessException")
    void padMissingLength() {
        assertThrows(BusinessException.class, () ->
                process("123", ProcessRuleType.PAD, map("direction", "LEFT")));
    }

    // ==================== CaseProcessor ====================

    @Test
    @DisplayName("CASE - UPPER：全大写")
    void caseUpper() {
        assertEquals("HELLO WORLD", process("hello world", ProcessRuleType.CASE, map("mode", "UPPER")));
    }

    @Test
    @DisplayName("CASE - LOWER：全小写")
    void caseLower() {
        assertEquals("hello world", process("HELLO WORLD", ProcessRuleType.CASE, map("mode", "LOWER")));
    }

    @Test
    @DisplayName("CASE - CAPITALIZE：首字母大写，其余小写")
    void caseCapitalize() {
        assertEquals("Hello world", process("HELLO WORLD", ProcessRuleType.CASE, map("mode", "CAPITALIZE")));
    }

    @Test
    @DisplayName("CASE - 默认 mode 等同 UPPER")
    void caseDefault() {
        assertEquals("HELLO", process("hello", ProcessRuleType.CASE, Collections.emptyMap()));
    }

    // ==================== TypeCastProcessor ====================

    @Test
    @DisplayName("TYPE_CAST - STRING：原样返回")
    void castString() {
        assertEquals("hello", process("hello", ProcessRuleType.TYPE_CAST, map("targetType", "STRING")));
    }

    @Test
    @DisplayName("TYPE_CAST - INTEGER：小数转整数")
    void castInteger() {
        assertEquals("42", process("42.9", ProcessRuleType.TYPE_CAST, map("targetType", "INTEGER")));
    }

    @Test
    @DisplayName("TYPE_CAST - DECIMAL：规范化小数格式")
    void castDecimal() {
        assertEquals("3.14", process("3.14", ProcessRuleType.TYPE_CAST, map("targetType", "DECIMAL")));
    }

    @Test
    @DisplayName("TYPE_CAST - BOOLEAN true/1/yes → true")
    void castBooleanTrue() {
        assertEquals("true", process("true",  ProcessRuleType.TYPE_CAST, map("targetType", "BOOLEAN")));
        assertEquals("true", process("1",     ProcessRuleType.TYPE_CAST, map("targetType", "BOOLEAN")));
        assertEquals("true", process("yes",   ProcessRuleType.TYPE_CAST, map("targetType", "BOOLEAN")));
    }

    @Test
    @DisplayName("TYPE_CAST - BOOLEAN 其余 → false")
    void castBooleanFalse() {
        assertEquals("false", process("0",     ProcessRuleType.TYPE_CAST, map("targetType", "BOOLEAN")));
        assertEquals("false", process("no",    ProcessRuleType.TYPE_CAST, map("targetType", "BOOLEAN")));
        assertEquals("false", process("false", ProcessRuleType.TYPE_CAST, map("targetType", "BOOLEAN")));
    }

    @Test
    @DisplayName("TYPE_CAST - INTEGER 非数字格式抛出 BusinessException")
    void castIntegerInvalid() {
        assertThrows(BusinessException.class, () ->
                process("abc", ProcessRuleType.TYPE_CAST, map("targetType", "INTEGER")));
    }

    // ==================== 多规则叠加 ====================

    @Test
    @DisplayName("多规则叠加：补位 → 截位")
    void multiRulePadThenSubstring() {
        List<ProcessRule> rules = Arrays.asList(
                rule(ProcessRuleType.PAD,       map("direction", "LEFT", "char", "0", "length", "8")),
                rule(ProcessRuleType.SUBSTRING, map("start", "3", "length", "5"))
        );
        // "123" → "00000123" → "00123"
        assertEquals("00123", fieldProcessor.process("123", rules));
    }

    @Test
    @DisplayName("多规则叠加：去空格 → 类型转换 → 补位")
    void multiRuleTrimCastPad() {
        List<ProcessRule> rules = Arrays.asList(
                rule(ProcessRuleType.TRIM,      map("mode", "BOTH")),
                rule(ProcessRuleType.TYPE_CAST, map("targetType", "INTEGER")),
                rule(ProcessRuleType.PAD,       map("direction", "LEFT", "char", "0", "length", "6"))
        );
        // "  42.7  " → "42" → "000042"
        assertEquals("000042", fieldProcessor.process("  42.7  ", rules));
    }

    @Test
    @DisplayName("空规则列表：直接返回原值")
    void emptyRules() {
        assertEquals("hello", fieldProcessor.process("hello", Collections.emptyList()));
        assertEquals("hello", fieldProcessor.process("hello", null));
    }

    @Test
    @DisplayName("null 值：各处理器均安全返回 null")
    void nullValue() {
        assertNull(fieldProcessor.process(null, Arrays.asList(
                rule(ProcessRuleType.TRIM, map("mode", "BOTH")))));
    }

    // ==================== 批量处理 ====================

    @Test
    @DisplayName("批量处理：多字段各自应用不同规则")
    void processBatch() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name",  "  alice  ");
        values.put("amount", "42.5");

        Map<String, List<ProcessRule>> rules = new LinkedHashMap<>();
        rules.put("name",   Arrays.asList(
                rule(ProcessRuleType.TRIM, map("mode", "BOTH")),
                rule(ProcessRuleType.CASE, map("mode", "CAPITALIZE"))));
        rules.put("amount", Collections.singletonList(
                rule(ProcessRuleType.TYPE_CAST, map("targetType", "INTEGER"))));

        Map<String, String> result = fieldProcessor.processBatch(values, rules);
        assertEquals("Alice", result.get("name"));
        assertEquals("42",    result.get("amount"));
    }

    // ==================== 辅助方法 ====================

    private String process(String value, ProcessRuleType type, Map<String, String> params) {
        return fieldProcessor.process(value, Collections.singletonList(rule(type, params)));
    }

    private ProcessRule rule(ProcessRuleType type, Map<String, String> params) {
        return new ProcessRule(type, params);
    }

    @SafeVarargs
    private static Map<String, String> map(String... keyValues) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            m.put(keyValues[i], keyValues[i + 1]);
        }
        return m;
    }
}
