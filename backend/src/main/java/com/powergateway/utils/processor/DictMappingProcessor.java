package com.powergateway.utils.processor;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.service.DictMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 字典映射策略（FN-12 · v0.2.0 ②）
 * 集成 M1-3 字段加工引擎，让转换/接口字段可以配置字典转换规则。
 *
 * 参数：
 *   - system    对端系统标识（如 CIF）
 *   - dictKey   字典标识（如 GENDER）
 *   - direction 方向 "1"=出向 "2"=入向（字符串，因 ProcessRule.params 是 Map<String,String>）
 *
 * 语义：
 *   - value 空值（null 或 ""）→ 透传，不 lookup
 *   - 参数缺失 / direction 非法 / miss → 抛 BusinessException(400)
 */
@Component
public class DictMappingProcessor implements FieldProcessStrategy {

    @Autowired
    private DictMappingService dictMappingService;

    @Override
    public ProcessRuleType ruleType() {
        return ProcessRuleType.DICT_MAP;
    }

    @Override
    public String process(String value, Map<String, String> params) {
        // 空值透传（用户可用 default 策略处理源空值场景）
        if (value == null || value.isEmpty()) {
            return value;
        }

        String system  = params == null ? null : params.get("system");
        String dictKey = params == null ? null : params.get("dictKey");
        String dirStr  = params == null ? null : params.get("direction");

        if (system == null || dictKey == null || dirStr == null) {
            throw new BusinessException(400,
                "DICT_MAP 参数缺失：system/dictKey/direction 均必填");
        }

        int direction;
        try {
            direction = Integer.parseInt(dirStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(400,
                "DICT_MAP direction 必须为整数 1 或 2，实际=" + dirStr);
        }

        DictMappingLookupResult r =
            dictMappingService.lookup(system, dictKey, direction, value);
        if (r == null) {
            throw new BusinessException(400, String.format(
                "字典 %s 值 %s 在系统 %s 未定义映射（direction=%d）",
                dictKey, value, system, direction));
        }
        return r.getTargetValue();
    }
}
