package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.InsertConfigJson.FieldInsertConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据来源解析器（M2-4，M2-5 复用）。
 *
 * 支持三种数据来源：
 * <ul>
 *   <li>REQUEST：从请求参数 Map 中按 paramKey 取值</li>
 *   <li>CONST：直接返回 constValue</li>
 *   <li>CALC：解析简单四则运算表达式（+、-、*、/，支持括号）</li>
 * </ul>
 */
@Component
public class DataSourceResolver {

    /**
     * 解析单个字段的值。
     *
     * @param field  字段配置
     * @param params 请求参数
     * @return 解析后的值（String、Double 或 null）
     */
    public Object resolve(FieldInsertConfig field, Map<String, Object> params) {
        if (field == null || field.getSourceType() == null) {
            throw new BusinessException(400, "字段配置不完整");
        }
        switch (field.getSourceType().toUpperCase()) {
            case "REQUEST":
                return params != null ? params.get(field.getParamKey()) : null;
            case "CONST":
                return field.getConstValue();
            case "CALC":
                return evalCalcExpression(field.getExpression(), params);
            default:
                throw new BusinessException(400, "未知的数据来源类型: " + field.getSourceType());
        }
    }

    // ─── 私有：表达式求值 ─────────────────────────────────────────────────────────

    private Object evalCalcExpression(String expression, Map<String, Object> params) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new BusinessException(400, "CALC 类型必须提供 expression");
        }
        // 将表达式中的参数名替换为其数值
        String expr = expression.replaceAll("\\s+", "");
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() != null) {
                    String escaped = Pattern.quote(entry.getKey());
                    expr = expr.replaceAll("(?<![\\w$])" + escaped + "(?![\\w$])",
                            String.valueOf(entry.getValue()));
                }
            }
        }
        try {
            double result = parseExpr(expr, new int[]{0});
            // 若结果是整数，返回 long；否则返回 double
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return (long) result;
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "表达式求值失败 [" + expression + "]: " + e.getMessage());
        }
    }

    // ─── 递归下降四则运算解析器 ──────────────────────────────────────────────────

    static double parseExpr(String expr, int[] pos) {
        double val = parseTerm(expr, pos);
        while (pos[0] < expr.length()) {
            char ch = expr.charAt(pos[0]);
            if (ch != '+' && ch != '-') break;
            pos[0]++;
            double t = parseTerm(expr, pos);
            val = ch == '+' ? val + t : val - t;
        }
        return val;
    }

    static double parseTerm(String expr, int[] pos) {
        double val = parseFactor(expr, pos);
        while (pos[0] < expr.length()) {
            char ch = expr.charAt(pos[0]);
            if (ch != '*' && ch != '/') break;
            pos[0]++;
            double f = parseFactor(expr, pos);
            if (ch == '/' && f == 0) throw new ArithmeticException("除以零");
            val = ch == '*' ? val * f : val / f;
        }
        return val;
    }

    static double parseFactor(String expr, int[] pos) {
        if (pos[0] >= expr.length()) {
            throw new IllegalArgumentException("表达式解析错误：意外结束");
        }
        if (expr.charAt(pos[0]) == '(') {
            pos[0]++;
            double val = parseExpr(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') pos[0]++;
            return val;
        }
        boolean negative = false;
        if (expr.charAt(pos[0]) == '-') {
            negative = true;
            pos[0]++;
        }
        int start = pos[0];
        while (pos[0] < expr.length() &&
               (Character.isDigit(expr.charAt(pos[0])) || expr.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        if (start == pos[0]) {
            throw new IllegalArgumentException("表达式中包含无法解析的标记，位置: " + pos[0]);
        }
        double val = Double.parseDouble(expr.substring(start, pos[0]));
        return negative ? -val : val;
    }
}
