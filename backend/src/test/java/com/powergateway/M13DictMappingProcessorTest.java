package com.powergateway;

import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.service.DictMappingService;
import com.powergateway.utils.processor.DictMappingProcessor;
import com.powergateway.utils.processor.ProcessRuleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** M1-3 DictMappingProcessor 单元测试（Mockito mock DictMappingService · 纯工具类 · @ActiveProfiles 遵循项目强制规约） */
@ActiveProfiles("test")
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
        // v0.2.5 CR-004:Processor 现在调 5 参 lookup(scope, ...) · 未指定 scope 时传 null
        Mockito.when(mockService.lookup(
                (Integer) null, "CIF", "GENDER", 1, "M"))
               .thenReturn(new DictMappingLookupResult("1", "男"));

        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");

        String result = processor.process("M", params);
        assertThat(result).isEqualTo("1");
        assertThat(processor.ruleType()).isEqualTo(ProcessRuleType.DICT_MAP);
    }

    @Test
    void mock_未命中_抛BusinessException400() {
        // v0.2.5:5 参签名 · 用 any* matcher
        Mockito.when(mockService.lookup(
                Mockito.nullable(Integer.class),
                Mockito.anyString(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.anyString()))
               .thenReturn(null);

        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process("X", params))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("未定义映射");
    }

    @Test
    void mock_带scope参数_传给service() {
        // v0.2.5 CR-004:params 含 scope=1 时应传给 service.lookup
        Mockito.when(mockService.lookup(1, "CIF", "GENDER", 1, "M"))
               .thenReturn(new DictMappingLookupResult("male", null));

        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        params.put("scope", "1");

        String result = processor.process("M", params);
        assertThat(result).isEqualTo("male");
    }

    @Test
    void 参数缺失_无system_抛400() {
        Map<String, String> params = new HashMap<>();
        params.put("dictKey", "GENDER");
        params.put("direction", "1");
        // 缺 system

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process("M", params))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("system/dictKey/direction 均必填");
    }

    @Test
    void direction非整数_抛400() {
        Map<String, String> params = new HashMap<>();
        params.put("system", "CIF");
        params.put("dictKey", "GENDER");
        params.put("direction", "abc");   // 非整数

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process("M", params))
            .isInstanceOf(com.powergateway.exception.BusinessException.class)
            .hasMessageContaining("direction 必须为整数");
    }
}
