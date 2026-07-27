package com.powergateway;

import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.service.DictMappingService;
import com.powergateway.utils.processor.DictMappingProcessor;
import com.powergateway.utils.processor.ProcessRuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** M1-3 DictMappingProcessor 单元测试（Mockito mock DictMappingService） */
class M13DictMappingProcessorTest {

    private DictMappingService mockService;
    private DictMappingProcessor processor;

    @BeforeEach
    void setUp() {
        mockService = Mockito.mock(DictMappingService.class);
        processor = new DictMappingProcessor();
        // 手工注入 mock（@Autowired 字段用反射设置）
        try {
            java.lang.reflect.Field f = DictMappingProcessor.class.getDeclaredField("dictMappingService");
            f.setAccessible(true);
            f.set(processor, mockService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mock_命中_返回target() {
        Mockito.when(mockService.lookup("CIF", "GENDER", 1, "M"))
               .thenReturn(new DictMappingLookupResult("1", "男"));

        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");

        String result = processor.process("M", params);
        assertThat(result).isEqualTo("1");
        assertThat(processor.ruleType()).isEqualTo(ProcessRuleType.DICT_MAP);
    }
}
