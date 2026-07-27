package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.service.DictMappingService;
import com.powergateway.utils.FieldProcessor;
import com.powergateway.utils.processor.ProcessRule;
import com.powergateway.utils.processor.ProcessRuleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FN-12 × M1-3 集成测试（v0.2.0 ②）
 * 走完整 Spring 装配：FieldProcessor → DictMappingProcessor → DictMappingService.lookup → H2
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FN12ProcessorIntegrationTest {

    @Autowired private FieldProcessor fieldProcessor;
    @Autowired private DictMappingService dictMappingService;

    @Test
    void 完整链路_processRule_命中() {
        // 1. 预置字典 CIF/GENDER/1: M→1
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode("CIF"); req.setDictKey("GENDER"); req.setDirection(1);
        req.setSourceValue("M");  req.setTargetValue("1"); req.setBidirectional(false);
        dictMappingService.save(req);

        // 2. 构造 process_rule
        ProcessRule rule = new ProcessRule();
        rule.setType(ProcessRuleType.DICT_MAP);
        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        rule.setParams(params);

        Map<String, List<ProcessRule>> allRules = new HashMap<>();
        allRules.put("gender", Collections.singletonList(rule));

        // 3. 输入字段
        Map<String, String> fieldValues = new HashMap<>();
        fieldValues.put("gender", "M");

        // 4. 执行 processBatch
        Map<String, String> result = fieldProcessor.processBatch(fieldValues, allRules);

        assertThat(result.get("gender")).isEqualTo("1");
    }

    @Test
    void 完整链路_processRule_未命中_抛400() {
        // 不预置字典
        ProcessRule rule = new ProcessRule();
        rule.setType(ProcessRuleType.DICT_MAP);
        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        rule.setParams(params);

        Map<String, List<ProcessRule>> allRules = new HashMap<>();
        allRules.put("gender", Collections.singletonList(rule));

        Map<String, String> fieldValues = new HashMap<>();
        fieldValues.put("gender", "M");

        assertThatThrownBy(() -> fieldProcessor.processBatch(fieldValues, allRules))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未定义映射");
    }
}
