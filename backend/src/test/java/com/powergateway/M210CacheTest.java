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
}
