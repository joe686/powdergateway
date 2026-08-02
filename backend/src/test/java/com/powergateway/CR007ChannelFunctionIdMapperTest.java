package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.route.ChannelFunctionIdMapper;
import com.powergateway.service.DictMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-007 · v0.3.1 Task 1.2 · ChannelFunctionIdMapper 单元测试(纯 Mockito · 无 Spring 上下文)。
 */
class CR007ChannelFunctionIdMapperTest {

    private DictMappingService dictService;
    private ChannelFunctionIdMapper mapper;

    @BeforeEach
    void setup() throws Exception {
        dictService = Mockito.mock(DictMappingService.class);
        mapper = new ChannelFunctionIdMapper();
        // 反射注入 mock
        java.lang.reflect.Field f = ChannelFunctionIdMapper.class.getDeclaredField("dictMappingService");
        f.setAccessible(true);
        f.set(mapper, dictService);
    }

    @Test
    @DisplayName("字典命中 · 返 targetValue")
    void 字典命中_返targetValue() {
        Mockito.when(dictService.lookup(3, "ROUTE", "channel_to_pg", 1, "181345"))
                .thenReturn(new DictMappingLookupResult("PG-181345", null));
        String result = mapper.map("181345");
        assertEquals("PG-181345", result);
    }

    @Test
    @DisplayName("字典未命中(返 null)· fallback 返原值")
    void 字典未命中_fallback原值() {
        Mockito.when(dictService.lookup(3, "ROUTE", "channel_to_pg", 1, "PG-181345"))
                .thenReturn(null);
        String result = mapper.map("PG-181345");
        assertEquals("PG-181345", result, "渠道直送 PG 功能号 · 未命中 fallback 直接返回");
    }

    @Test
    @DisplayName("字典查询抛异常 · fallback 返原值 · 不阻塞路由")
    void 字典异常_fallback原值() {
        Mockito.when(dictService.lookup(3, "ROUTE", "channel_to_pg", 1, "999999"))
                .thenThrow(new RuntimeException("Redis 不可用"));
        String result = mapper.map("999999");
        assertEquals("999999", result, "字典 unavailable 不阻塞路由 · fallback 原值");
    }

    @Test
    @DisplayName("字典命中但 targetValue 空 · fallback 原值")
    void 字典命中空值_fallback原值() {
        Mockito.when(dictService.lookup(3, "ROUTE", "channel_to_pg", 1, "888888"))
                .thenReturn(new DictMappingLookupResult("", null));
        String result = mapper.map("888888");
        assertEquals("888888", result);
    }

    @Test
    @DisplayName("channelFunctionId 空 · 抛 BusinessException")
    void channelFunctionId空_抛异常() {
        BusinessException ex1 = assertThrows(BusinessException.class, () -> mapper.map(null));
        assertTrue(ex1.getMessage().contains("不能为空"));

        BusinessException ex2 = assertThrows(BusinessException.class, () -> mapper.map(""));
        assertTrue(ex2.getMessage().contains("不能为空"));

        BusinessException ex3 = assertThrows(BusinessException.class, () -> mapper.map("   "));
        assertTrue(ex3.getMessage().contains("不能为空"));
    }

    @Test
    @DisplayName("channelFunctionId 前后空白 · 自动 trim")
    void channelFunctionId_trim() {
        Mockito.when(dictService.lookup(3, "ROUTE", "channel_to_pg", 1, "181345"))
                .thenReturn(new DictMappingLookupResult("PG-181345", null));
        String result = mapper.map("  181345  ");
        assertEquals("PG-181345", result);
    }
}
