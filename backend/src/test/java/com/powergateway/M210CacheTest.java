package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.CacheStatDTO;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.QueryCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-10 缓存查询管理")
class M210CacheTest {

    @Autowired private QueryCacheManager cacheManager;
    @Autowired private InterfaceConfigService interfaceConfigService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ── key 生成 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("无模板时：参数按 key 排序生成一致 cacheKey")
    void cacheKey_noTemplate_sortedParams() {
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("status", "active");
        params1.put("userId", 1);

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("userId", 1);
        params2.put("status", "active");

        String key1 = cacheManager.buildCacheKey(42L, "", params1);
        String key2 = cacheManager.buildCacheKey(42L, null, params2);

        assertThat(key1).startsWith("query_cache:42:");
        assertThat(key1).isEqualTo(key2);
        assertThat(key1).contains("status=active");
        assertThat(key1).contains("userId=1");
    }

    @Test
    @DisplayName("有模板时：{参数名} 占位符被正确替换")
    void cacheKey_withTemplate_replaced() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 99);
        params.put("status", "done");

        String key = cacheManager.buildCacheKey(7L, "order:{userId}:{status}", params);

        assertThat(key).isEqualTo("query_cache:7:order:99:done");
    }

    // ── executeWithCache ──────────────────────────────────────────────────

    @Test
    @DisplayName("cache_enabled=0 时每次调 DB，不写本地缓存")
    void executeWithCache_cacheDisabled_alwaysHitsDb() {
        InterfaceConfig config = new InterfaceConfig();
        config.setCacheEnabled(0);
        config.setCacheTtlSeconds(300);
        config.setCacheKeyTemplate("");

        int[] callCount = {0};
        Supplier<List<Map<String, Object>>> dbFn = () -> {
            callCount[0]++;
            Map<String, Object> row = new HashMap<>();
            row.put("id", callCount[0]);
            return Collections.singletonList(row);
        };

        List<Map<String, Object>> r1 = cacheManager.executeWithCache(1L, config, new HashMap<>(), dbFn);
        List<Map<String, Object>> r2 = cacheManager.executeWithCache(1L, config, new HashMap<>(), dbFn);

        assertThat(callCount[0]).isEqualTo(2);
        assertThat(r1.get(0).get("id")).isEqualTo(1);
        assertThat(r2.get(0).get("id")).isEqualTo(2);
    }

    @Test
    @DisplayName("cache_enabled=1 时第二次命中 Caffeine，DB 只被调一次")
    void executeWithCache_cacheEnabled_secondCallHitsCaffeine() {
        InterfaceConfig config = new InterfaceConfig();
        config.setId(500L);
        config.setCacheEnabled(1);
        config.setCacheTtlSeconds(300);
        config.setCacheKeyTemplate("test:{val}");

        int[] callCount = {0};
        List<Map<String, Object>> dbResult = Collections.singletonList(
                Collections.singletonMap("name", "cached_value"));
        Supplier<List<Map<String, Object>>> dbFn = () -> {
            callCount[0]++;
            return dbResult;
        };

        Map<String, Object> params = new HashMap<>();
        params.put("val", "abc");

        List<Map<String, Object>> r1 = cacheManager.executeWithCache(500L, config, params, dbFn);
        List<Map<String, Object>> r2 = cacheManager.executeWithCache(500L, config, params, dbFn);

        assertThat(callCount[0]).isEqualTo(1);
        assertThat(r1.get(0).get("name")).isEqualTo("cached_value");
        assertThat(r2.get(0).get("name")).isEqualTo("cached_value");
    }
}
