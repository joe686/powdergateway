# CHG-002 渠道级别报文头适配配置 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在渠道配置和端口路由两级支持报文头（Content-Type、Charset、自定义 KV Header）的配置与合并，转发时自动注入 HttpHeaders 并按需做字节级 charset 转码。

**Architecture:** 两张表各增加一个 `header_config TEXT` 列存 JSON；新增 `HeaderConfig` DTO、`HeaderConfigMerger`（两级合并）、`CharsetConverter`（字节级转码）三个纯 Java 类；`PortRouteService.dispatch()` 在转发前合并配置并构造 `HttpHeaders`，`PortRouteController.dispatch()` 读取 responseHeaders 写入 servlet 响应头。

**Tech Stack:** Spring Boot 2.7、MyBatis-Plus、Jackson、H2（测试）、MockRestServiceServer（集成测试）、Vue 3 + Element Plus（前端）

---

## 文件变更清单

| 操作 | 文件路径 |
|------|---------|
| 新增 | `backend/src/main/java/com/powergateway/model/dto/HeaderConfig.java` |
| 新增 | `backend/src/main/java/com/powergateway/utils/CharsetConverter.java` |
| 新增 | `backend/src/main/java/com/powergateway/utils/HeaderConfigMerger.java` |
| 新增 | `backend/src/test/java/com/powergateway/CharsetConverterTest.java` |
| 新增 | `backend/src/test/java/com/powergateway/HeaderConfigMergerTest.java` |
| 新增 | `backend/src/test/java/com/powergateway/M14ChannelHeaderTest.java` |
| 新增 | `backend/src/test/java/com/powergateway/M17HeaderDispatchTest.java` |
| 修改 | `backend/src/main/resources/db/init.sql` |
| 修改 | `backend/src/main/java/com/powergateway/model/ChannelConfig.java` |
| 修改 | `backend/src/main/java/com/powergateway/model/PortRoute.java` |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/ChannelSaveRequest.java` |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/PortRouteSaveRequest.java` |
| 修改 | `backend/src/main/java/com/powergateway/service/ChannelConfigService.java` |
| 修改 | `backend/src/main/java/com/powergateway/service/PortRouteService.java` |
| 修改 | `backend/src/main/java/com/powergateway/controller/PortRouteController.java` |
| 修改 | `frontend/src/views/convert/ChannelConfig.vue` |
| 修改 | `frontend/src/views/convert/PortRoute.vue` |

---

## Task 1：`HeaderConfig` DTO + DTO 字段扩展

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/HeaderConfig.java`
- Modify: `backend/src/main/java/com/powergateway/model/dto/ChannelSaveRequest.java`
- Modify: `backend/src/main/java/com/powergateway/model/dto/PortRouteSaveRequest.java`

- [ ] **Step 1：创建 `HeaderConfig.java`**

```java
// backend/src/main/java/com/powergateway/model/dto/HeaderConfig.java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.Map;

/**
 * 报文头配置 DTO（CHG-002）
 * 用于 channel_config.header_config 和 port_route.header_config 两列的 JSON 映射。
 */
@Data
public class HeaderConfig {

    /** 出向请求的 Content-Type，e.g. "application/json"；null=不设置 */
    private String contentType;

    /** 出向请求的字符集，e.g. "GBK"、"ISO-8859-1"；null/空=不转码（默认 UTF-8） */
    private String charset;

    /** 出向请求自定义 Header（A→B），key=header 名，value=header 值 */
    private Map<String, String> requestHeaders;

    /** 返回给 A 的响应头，key=header 名，value=header 值 */
    private Map<String, String> responseHeaders;
}
```

- [ ] **Step 2：在 `ChannelSaveRequest` 末尾追加字段**

当前文件末尾 `private Long templateId;` 后面加：

```java
    /** 报文头配置（可选），序列化后存 channel_config.header_config */
    private HeaderConfig headerConfig;
```

- [ ] **Step 3：在 `PortRouteSaveRequest` 末尾追加字段**

当前文件末尾 `private Long responseTemplateId;` 后面加：

```java
    /** 报文头配置（可选），序列化后存 port_route.header_config，覆盖渠道默认值 */
    private HeaderConfig headerConfig;
```

- [ ] **Step 4：编译验证（无测试，纯 POJO）**

```bash
cd backend
mvn compile -q
```

期望：`BUILD SUCCESS`，无报错。

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/HeaderConfig.java \
        backend/src/main/java/com/powergateway/model/dto/ChannelSaveRequest.java \
        backend/src/main/java/com/powergateway/model/dto/PortRouteSaveRequest.java
git commit -m "feat(CHG-002): add HeaderConfig DTO and extend save request DTOs"
```

---

## Task 2：`CharsetConverter` 工具类（TDD）

**Files:**
- Create: `backend/src/test/java/com/powergateway/CharsetConverterTest.java`
- Create: `backend/src/main/java/com/powergateway/utils/CharsetConverter.java`

- [ ] **Step 1：写失败测试**

```java
// backend/src/test/java/com/powergateway/CharsetConverterTest.java
package com.powergateway;

import com.powergateway.utils.CharsetConverter;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class CharsetConverterTest {

    @Test
    void encodeToBytes_utf8_returnsUtf8Bytes() {
        byte[] bytes = CharsetConverter.encodeToBytes("hello", "UTF-8");
        assertArrayEquals("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8), bytes);
    }

    @Test
    void encodeToBytes_nullCharset_defaultsToUtf8() {
        byte[] bytes = CharsetConverter.encodeToBytes("hello", null);
        assertArrayEquals("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8), bytes);
    }

    @Test
    void decodeFromBytes_utf8_returnsOriginalString() {
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("hello", CharsetConverter.decodeFromBytes(bytes, "UTF-8"));
    }

    @Test
    void decodeFromBytes_nullBytes_returnsEmpty() {
        assertEquals("", CharsetConverter.decodeFromBytes(null, "UTF-8"));
    }

    @Test
    void roundTrip_gbk_encodeDecodePreservesAscii() {
        // ASCII 字符在 UTF-8 和 GBK 中字节相同，往返无损
        String original = "Hello PowerGateway 123";
        byte[] encoded = CharsetConverter.encodeToBytes(original, "GBK");
        String decoded = CharsetConverter.decodeFromBytes(encoded, "GBK");
        assertEquals(original, decoded);
    }

    @Test
    void isEffectivelyUtf8_nullOrEmpty_returnsTrue() {
        assertTrue(CharsetConverter.isEffectivelyUtf8(null));
        assertTrue(CharsetConverter.isEffectivelyUtf8(""));
        assertTrue(CharsetConverter.isEffectivelyUtf8("UTF-8"));
        assertTrue(CharsetConverter.isEffectivelyUtf8("utf-8"));
    }

    @Test
    void isEffectivelyUtf8_gbk_returnsFalse() {
        assertFalse(CharsetConverter.isEffectivelyUtf8("GBK"));
        assertFalse(CharsetConverter.isEffectivelyUtf8("ISO-8859-1"));
    }

    @Test
    void encodeToBytes_unsupportedCharset_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> CharsetConverter.encodeToBytes("hello", "INVALID-CHARSET-XYZ"));
    }
}
```

- [ ] **Step 2：确认测试失败**

```bash
cd backend
mvn test -Dtest=CharsetConverterTest 2>&1 | grep -E "Tests run|FAIL|ERROR|BUILD"
```

期望：`BUILD FAILURE`（类不存在）。

- [ ] **Step 3：实现 `CharsetConverter`**

```java
// backend/src/main/java/com/powergateway/utils/CharsetConverter.java
package com.powergateway.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 字节级字符集转换工具（CHG-002）。
 * 所有方法均为静态方法，无状态，可直接调用。
 */
public class CharsetConverter {

    private CharsetConverter() {}

    /**
     * 将 String 编码为目标字符集的字节数组。
     * charset 为 null 或空时默认 UTF-8。
     */
    public static byte[] encodeToBytes(String text, String charset) {
        if (text == null) return new byte[0];
        return text.getBytes(resolveCharset(charset));
    }

    /**
     * 将字节数组按指定字符集解码为 String。
     * bytes 为 null 时返回空字符串。charset 为 null 或空时默认 UTF-8。
     */
    public static String decodeFromBytes(byte[] bytes, String charset) {
        if (bytes == null || bytes.length == 0) return "";
        return new String(bytes, resolveCharset(charset));
    }

    /**
     * 判断 charset 是否等价于 UTF-8（含 null/空，视为 UTF-8）。
     */
    public static boolean isEffectivelyUtf8(String charset) {
        if (charset == null || charset.trim().isEmpty()) return true;
        try {
            return Charset.forName(charset).equals(StandardCharsets.UTF_8);
        } catch (UnsupportedCharsetException e) {
            return false;
        }
    }

    private static Charset resolveCharset(String charset) {
        if (charset == null || charset.trim().isEmpty()) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(charset);
        } catch (UnsupportedCharsetException e) {
            throw new IllegalArgumentException("不支持的字符集：" + charset, e);
        }
    }
}
```

- [ ] **Step 4：确认测试全绿**

```bash
mvn test -Dtest=CharsetConverterTest 2>&1 | grep -E "Tests run|BUILD"
```

期望：`Tests run: 8, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/powergateway/utils/CharsetConverter.java \
        backend/src/test/java/com/powergateway/CharsetConverterTest.java
git commit -m "feat(CHG-002): add CharsetConverter utility with byte-level encoding support"
```

---

## Task 3：`HeaderConfigMerger` 工具类（TDD）

**Files:**
- Create: `backend/src/test/java/com/powergateway/HeaderConfigMergerTest.java`
- Create: `backend/src/main/java/com/powergateway/utils/HeaderConfigMerger.java`

- [ ] **Step 1：写失败测试**

```java
// backend/src/test/java/com/powergateway/HeaderConfigMergerTest.java
package com.powergateway;

import com.powergateway.model.dto.HeaderConfig;
import com.powergateway.utils.HeaderConfigMerger;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class HeaderConfigMergerTest {

    @Test
    void merge_bothNull_returnsEmptyConfig() {
        HeaderConfig result = HeaderConfigMerger.merge(null, null);
        assertNotNull(result);
        assertNull(result.getContentType());
        assertNull(result.getCharset());
        assertNull(result.getRequestHeaders());
        assertNull(result.getResponseHeaders());
    }

    @Test
    void merge_channelOnly_returnsChannelValues() {
        HeaderConfig channel = new HeaderConfig();
        channel.setContentType("application/json");
        channel.setCharset("GBK");
        channel.setRequestHeaders(Map.of("X-Channel", "ch1"));

        HeaderConfig result = HeaderConfigMerger.merge(channel, null);

        assertEquals("application/json", result.getContentType());
        assertEquals("GBK", result.getCharset());
        assertEquals("ch1", result.getRequestHeaders().get("X-Channel"));
    }

    @Test
    void merge_routeOverridesContentType() {
        HeaderConfig channel = new HeaderConfig();
        channel.setContentType("application/json");
        channel.setCharset("GBK");

        HeaderConfig route = new HeaderConfig();
        route.setContentType("application/xml");
        // charset not set in route → channel value wins

        HeaderConfig result = HeaderConfigMerger.merge(channel, route);

        assertEquals("application/xml", result.getContentType()); // route wins
        assertEquals("GBK", result.getCharset());                 // channel fallback
    }

    @Test
    void merge_requestHeaders_routeKeyOverridesChannelKey() {
        HeaderConfig channel = new HeaderConfig();
        channel.setRequestHeaders(Map.of("X-Common", "from-channel", "X-Channel-Only", "yes"));

        HeaderConfig route = new HeaderConfig();
        route.setRequestHeaders(Map.of("X-Common", "from-route", "X-Route-Only", "yes"));

        HeaderConfig result = HeaderConfigMerger.merge(channel, route);

        assertEquals("from-route", result.getRequestHeaders().get("X-Common"));     // route wins
        assertEquals("yes", result.getRequestHeaders().get("X-Channel-Only"));      // channel retained
        assertEquals("yes", result.getRequestHeaders().get("X-Route-Only"));        // route added
    }

    @Test
    void merge_responseHeaders_mergedCorrectly() {
        HeaderConfig channel = new HeaderConfig();
        channel.setResponseHeaders(Map.of("Content-Type", "application/json"));

        HeaderConfig route = new HeaderConfig();
        route.setResponseHeaders(Map.of("X-Trace-Id", "abc"));

        HeaderConfig result = HeaderConfigMerger.merge(channel, route);

        assertEquals("application/json", result.getResponseHeaders().get("Content-Type"));
        assertEquals("abc", result.getResponseHeaders().get("X-Trace-Id"));
    }

    @Test
    void merge_routeNullCharset_channelCharsetUsed() {
        HeaderConfig channel = new HeaderConfig();
        channel.setCharset("ISO-8859-1");

        HeaderConfig route = new HeaderConfig();
        // charset is null

        assertEquals("ISO-8859-1", HeaderConfigMerger.merge(channel, route).getCharset());
    }

    @Test
    void merge_routeEmptyHeaders_channelHeadersRetained() {
        HeaderConfig channel = new HeaderConfig();
        channel.setRequestHeaders(Map.of("X-Keep", "me"));

        HeaderConfig route = new HeaderConfig();
        // requestHeaders is null

        assertEquals("me", HeaderConfigMerger.merge(channel, route).getRequestHeaders().get("X-Keep"));
    }
}
```

- [ ] **Step 2：确认测试失败**

```bash
mvn test -Dtest=HeaderConfigMergerTest 2>&1 | grep -E "Tests run|FAIL|ERROR|BUILD"
```

期望：`BUILD FAILURE`（类不存在）。

- [ ] **Step 3：实现 `HeaderConfigMerger`**

```java
// backend/src/main/java/com/powergateway/utils/HeaderConfigMerger.java
package com.powergateway.utils;

import com.powergateway.model.dto.HeaderConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 两级报文头配置合并工具（CHG-002）。
 * 优先级：port_route 配置 > channel_config 配置。
 */
public class HeaderConfigMerger {

    private HeaderConfigMerger() {}

    /**
     * 合并渠道级别（channel）和端口路由级别（route）的报文头配置。
     * <ul>
     *   <li>contentType / charset：route 非 null 时覆盖，否则取 channel 值</li>
     *   <li>requestHeaders / responseHeaders：先取 channel 的 map，再逐键 putAll route 的 map</li>
     * </ul>
     *
     * @param channel 渠道级别配置，可为 null
     * @param route   端口路由级别配置，可为 null
     * @return 合并后的 HeaderConfig，永远不为 null
     */
    public static HeaderConfig merge(HeaderConfig channel, HeaderConfig route) {
        HeaderConfig result = new HeaderConfig();

        // contentType：route 优先
        result.setContentType(
                route != null && route.getContentType() != null
                        ? route.getContentType()
                        : (channel != null ? channel.getContentType() : null)
        );

        // charset：route 优先
        result.setCharset(
                route != null && route.getCharset() != null
                        ? route.getCharset()
                        : (channel != null ? channel.getCharset() : null)
        );

        // requestHeaders：合并，route 键值覆盖 channel 同名键
        result.setRequestHeaders(mergeMaps(
                channel != null ? channel.getRequestHeaders() : null,
                route != null ? route.getRequestHeaders() : null
        ));

        // responseHeaders：合并，route 键值覆盖 channel 同名键
        result.setResponseHeaders(mergeMaps(
                channel != null ? channel.getResponseHeaders() : null,
                route != null ? route.getResponseHeaders() : null
        ));

        return result;
    }

    private static Map<String, String> mergeMaps(Map<String, String> base, Map<String, String> override) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) merged.putAll(base);
        if (override != null) merged.putAll(override);
        return merged.isEmpty() ? null : merged;
    }
}
```

- [ ] **Step 4：确认测试全绿**

```bash
mvn test -Dtest=HeaderConfigMergerTest 2>&1 | grep -E "Tests run|BUILD"
```

期望：`Tests run: 7, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/powergateway/utils/HeaderConfigMerger.java \
        backend/src/test/java/com/powergateway/HeaderConfigMergerTest.java
git commit -m "feat(CHG-002): add HeaderConfigMerger with two-level priority merge"
```

---

## Task 4：数据库 Schema + 实体类 + `ChannelConfigService` 变更（TDD）

**Files:**
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/main/java/com/powergateway/model/ChannelConfig.java`
- Modify: `backend/src/main/java/com/powergateway/model/PortRoute.java`
- Modify: `backend/src/main/java/com/powergateway/service/ChannelConfigService.java`
- Create: `backend/src/test/java/com/powergateway/M14ChannelHeaderTest.java`

- [ ] **Step 1：写失败测试**

```java
// backend/src/test/java/com/powergateway/M14ChannelHeaderTest.java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.model.dto.HeaderConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M1-4 渠道模板管理 - 报文头配置扩展验收测试（CHG-002）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M1-4 渠道报文头配置（CHG-002）")
class M14ChannelHeaderTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    /** 先创建一个模板（渠道需要关联模板） */
    private Long createTemplate() throws Exception {
        String body = "{\"name\":\"hdr-test-tpl\",\"srcFormat\":\"JSON\",\"targetFormat\":\"XML\",\"mappingRules\":[]}";
        MvcResult r = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return Long.valueOf(JsonPath.read(r.getResponse().getContentAsString(), "$.data").toString());
    }

    @Test
    @DisplayName("保存渠道时携带 headerConfig，查询列表后 headerConfig 字段不丢失")
    void saveChannel_withHeaderConfig_persistedAndReturned() throws Exception {
        Long tplId = createTemplate();

        HeaderConfig hc = new HeaderConfig();
        hc.setContentType("application/json");
        hc.setCharset("GBK");
        hc.setRequestHeaders(Map.of("X-Channel-Id", "CH_TEST"));
        hc.setResponseHeaders(Map.of("X-Resp-Flag", "ok"));

        String body = objectMapper.writeValueAsString(Map.of(
                "channelCode", "CH_HDR_TEST_" + System.currentTimeMillis(),
                "channelName", "Header测试渠道",
                "identifyField", "channel",
                "templateId", tplId,
                "headerConfig", hc
        ));

        MvcResult saveResult = mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Long channelId = Long.valueOf(JsonPath.read(
                saveResult.getResponse().getContentAsString(), "$.data").toString());
        assertNotNull(channelId);

        // 查询列表，验证 headerConfig 被正确持久化并返回
        MvcResult listResult = mockMvc.perform(get("/api/channel/list")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andReturn();

        String listJson = listResult.getResponse().getContentAsString();
        // 找到刚保存的渠道
        String contentType = JsonPath.read(listJson,
                String.format("$.data[?(@.id == %d)].headerConfig.contentType", channelId));
        assertEquals("application/json", contentType);

        String charset = JsonPath.read(listJson,
                String.format("$.data[?(@.id == %d)].headerConfig.charset", channelId));
        assertEquals("GBK", charset);
    }

    @Test
    @DisplayName("保存渠道时不传 headerConfig，存储和查询均正常（向后兼容）")
    void saveChannel_withoutHeaderConfig_noError() throws Exception {
        Long tplId = createTemplate();

        String body = objectMapper.writeValueAsString(Map.of(
                "channelCode", "CH_NO_HDR_" + System.currentTimeMillis(),
                "channelName", "无Header渠道",
                "identifyField", "channel",
                "templateId", tplId
        ));

        mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

- [ ] **Step 2：确认测试失败**

```bash
mvn test -Dtest=M14ChannelHeaderTest 2>&1 | grep -E "Tests run|FAIL|ERROR|BUILD"
```

期望：编译失败或测试失败（`headerConfig` 字段不存在）。

- [ ] **Step 3：更新 `init.sql`，两张表各加一列**

在 `channel_config` 表 DDL 的 `deleted TINYINT DEFAULT 0,` 前插入：

```sql
  header_config TEXT COMMENT '渠道级别报文头配置（JSON），字段见 HeaderConfig（CHG-002）',
```

在 `port_route` 表 DDL 的 `deleted TINYINT DEFAULT 0,` 前插入：

```sql
  header_config TEXT COMMENT '端口路由级别报文头配置（JSON），覆盖渠道默认值（CHG-002）',
```

- [ ] **Step 4：在 `ChannelConfig.java` 追加字段**

在最后一个字段 `private LocalDateTime createTime;` 前面加：

```java
    /** 报文头配置（JSON 字符串），对应 HeaderConfig DTO，可为 null（CHG-002） */
    private String headerConfig;
```

- [ ] **Step 5：在 `PortRoute.java` 追加字段**

在最后一个字段 `private LocalDateTime createTime;` 前面加：

```java
    /** 报文头配置（JSON 字符串），对应 HeaderConfig DTO，可为 null；覆盖渠道默认值（CHG-002） */
    private String headerConfig;
```

- [ ] **Step 6：更新 `ChannelConfigService`**

在类顶部 import 区补充（若不存在）：

```java
import com.powergateway.model.dto.HeaderConfig;
```

**修改 `saveChannel()`：** 在 `config.setTemplateId(req.getTemplateId());` 这行后面加：

```java
        // 序列化 headerConfig（CHG-002）
        if (req.getHeaderConfig() != null) {
            try {
                config.setHeaderConfig(objectMapper.writeValueAsString(req.getHeaderConfig()));
            } catch (Exception e) {
                log.warn("headerConfig 序列化失败，将存 null: {}", e.getMessage());
                config.setHeaderConfig(null);
            }
        } else {
            config.setHeaderConfig(null);
        }
```

**新增 `getByChannelCode()` 方法**（放在 `getById()` 方法之后）：

```java
    /**
     * 按渠道编码查询单个渠道配置（使用缓存列表）。
     * 供 M1-7 dispatch 获取渠道级别 headerConfig 使用（CHG-002）。
     *
     * @return 命中的渠道配置；未找到时返回 null
     */
    public ChannelConfig getByChannelCode(String channelCode) {
        return getAllChannelsCached().stream()
                .filter(ch -> channelCode.equals(ch.getChannelCode()))
                .findFirst()
                .orElse(null);
    }
```

**新增 `parseHeaderConfig()` 辅助方法**（放在 `evictCache()` 之后）：

```java
    /**
     * 将实体的 headerConfig JSON 字符串反序列化为 HeaderConfig DTO。
     * 供 PortRouteService 使用（CHG-002）。
     *
     * @return 反序列化结果；json 为 null/异常时返回 null
     */
    public HeaderConfig parseHeaderConfig(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, HeaderConfig.class);
        } catch (Exception e) {
            log.warn("headerConfig 反序列化失败: {}", e.getMessage());
            return null;
        }
    }
```

**更新 `listChannels()` 的返回**：当前返回 `List<ChannelConfig>`，实体里的 `headerConfig` 字段是 String，但前端查询列表时需要 JSON 对象。为此在 `ChannelConfigController`（或 Service）需要把 String 反序列化后注入对象的同名字段。

> **注意**：当前 `ChannelConfig` 实体的 `headerConfig` 是 `String` 类型，直接序列化为 JSON 时会以字符串（带转义）输出，不是对象结构。解决方案：在 `ChannelConfig` 上用 `@JsonRawValue` 和自定义 getter，或将字段类型改为 `Object`。

**最简方案**：将 `ChannelConfig.headerConfig` 的类型改为 `Object`（让 Jackson 直接输出 JSON 对象），但 MyBatis-Plus 存取时需要 String。为此在 `ChannelConfig` 中改为：

```java
    @TableField("header_config")
    private String headerConfigRaw;   // 数据库存取用，不参与 JSON 序列化

    @TableField(exist = false)
    private HeaderConfig headerConfig; // 前端接口用，不存数据库
```

然后在 `ChannelConfigService.listChannels()` 改为调用一个内部方法来反序列化：

```java
    public List<ChannelConfig> listChannels() {
        LambdaQueryWrapper<ChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChannelConfig::getCreateTime);
        List<ChannelConfig> list = channelConfigMapper.selectList(wrapper);
        // 反序列化 headerConfig（CHG-002）
        list.forEach(ch -> ch.setHeaderConfig(parseHeaderConfig(ch.getHeaderConfigRaw())));
        return list;
    }
```

**同时修改 `ChannelConfig.java`**，将 `private String headerConfig;` 改为：

```java
    /** 数据库原始 JSON 列（CHG-002），不直接暴露给前端 */
    @TableField("header_config")
    private String headerConfigRaw;

    /** 反序列化后的报文头配置对象，前端响应用，不存数据库 */
    @TableField(exist = false)
    private com.powergateway.model.dto.HeaderConfig headerConfig;
```

并在 `saveChannel()` 中将 `config.setHeaderConfig(...)` 改为 `config.setHeaderConfigRaw(...)`。

在 `parseHeaderConfig(String json)` 中不变。

在 `getByChannelCode()` 中返回前也需要反序列化：

```java
    public ChannelConfig getByChannelCode(String channelCode) {
        return getAllChannelsCached().stream()
                .filter(ch -> channelCode.equals(ch.getChannelCode()))
                .findFirst()
                .map(ch -> {
                    ch.setHeaderConfig(parseHeaderConfig(ch.getHeaderConfigRaw()));
                    return ch;
                })
                .orElse(null);
    }
```

> 缓存的 ChannelConfig 列表包含 `headerConfigRaw`（String），每次按需反序列化。

**同样处理 `PortRoute.java`**，将 `private String headerConfig;` 改为：

```java
    /** 数据库原始 JSON 列（CHG-002） */
    @TableField("header_config")
    private String headerConfigRaw;

    /** 反序列化后的报文头配置对象 */
    @TableField(exist = false)
    private com.powergateway.model.dto.HeaderConfig headerConfig;
```

在 `PortRouteService` 中：
- `saveRoute()` 将 `config.setHeaderConfig(...)` 改为 `config.setHeaderConfigRaw(...)`
- `getRouteCachedByChannelCode()` 返回前调用 `parseHeaderConfig(route.getHeaderConfigRaw())` 并 `route.setHeaderConfig(...)`（在 return 语句前处理）

新增 `parseHeaderConfig()` 到 `PortRouteService`（与 ChannelConfigService 相同逻辑，避免依赖）：

```java
    private HeaderConfig parseHeaderConfig(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, HeaderConfig.class);
        } catch (Exception e) {
            log.warn("portRoute.headerConfig 反序列化失败: {}", e.getMessage());
            return null;
        }
    }
```

- [ ] **Step 7：确认测试通过**

```bash
mvn test -Dtest=M14ChannelHeaderTest 2>&1 | grep -E "Tests run|BUILD"
```

期望：`Tests run: 2, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。

- [ ] **Step 8：全量回归**

```bash
mvn test 2>&1 | grep -E "Tests run|BUILD" | tail -5
```

期望：`BUILD SUCCESS`，无新增失败。

- [ ] **Step 9：提交**

```bash
git add backend/src/main/resources/db/init.sql \
        backend/src/main/java/com/powergateway/model/ChannelConfig.java \
        backend/src/main/java/com/powergateway/model/PortRoute.java \
        backend/src/main/java/com/powergateway/service/ChannelConfigService.java \
        backend/src/main/java/com/powergateway/service/PortRouteService.java \
        backend/src/test/java/com/powergateway/M14ChannelHeaderTest.java
git commit -m "feat(CHG-002): add header_config columns, update entities and ChannelConfigService"
```

---

## Task 5：`PortRouteService.dispatch()` 集成 Header 注入 + Charset 转码（TDD）

**Files:**
- Create: `backend/src/test/java/com/powergateway/M17HeaderDispatchTest.java`
- Modify: `backend/src/main/java/com/powergateway/service/PortRouteService.java`
- Modify: `backend/src/main/java/com/powergateway/controller/PortRouteController.java`

- [ ] **Step 1：写失败测试**

```java
// backend/src/test/java/com/powergateway/M17HeaderDispatchTest.java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.model.dto.HeaderConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M1-7 端口分发路由 - 报文头适配扩展验收测试（CHG-002）
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M1-7 报文头适配（CHG-002）")
class M17HeaderDispatchTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;
    private String token;

    private static final String B_URL = "http://mock-b-hdr/api/receive";

    @BeforeEach
    void setup() throws Exception {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    private Long createTemplate() throws Exception {
        String body = "{\"name\":\"hdr-disp-tpl\",\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\",\"mappingRules\":[]}";
        MvcResult r = mockMvc.perform(post("/api/template/save")
                        .header("satoken", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return Long.valueOf(JsonPath.read(r.getResponse().getContentAsString(), "$.data").toString());
    }

    private String createChannel(String channelCode, HeaderConfig headerConfig) throws Exception {
        Long tplId = createTemplate();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("channelCode", channelCode);
        body.put("channelName", "Header测试渠道");
        body.put("identifyField", "channel");
        body.put("templateId", tplId);
        if (headerConfig != null) body.put("headerConfig", headerConfig);

        mockMvc.perform(post("/api/channel/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        return channelCode;
    }

    private Long createRoute(String channelCode, HeaderConfig headerConfig) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("channelCode", channelCode);
        body.put("portAddress", B_URL);
        body.put("portMethod", "POST");
        body.put("timeout", 3000);
        body.put("retryCount", 1);
        if (headerConfig != null) body.put("headerConfig", headerConfig);

        MvcResult r = mockMvc.perform(post("/api/port-route/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return Long.valueOf(JsonPath.read(r.getResponse().getContentAsString(), "$.data").toString());
    }

    @Test
    @DisplayName("dispatch 时出向请求携带渠道级别 Content-Type 和自定义 Header")
    void dispatch_channelHeaderConfig_injectedIntoForwardRequest() throws Exception {
        String code = "CH_HDR_DISP_" + System.currentTimeMillis();

        HeaderConfig channelHc = new HeaderConfig();
        channelHc.setContentType("application/json");
        channelHc.setRequestHeaders(Map.of("X-Channel-Tag", "chg002"));

        createChannel(code, channelHc);
        createRoute(code, null);

        // 期望 B 收到的请求携带正确 Content-Type 和自定义 Header
        mockServer.expect(requestTo(B_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("application/json")))
                .andExpect(header("X-Channel-Tag", equalTo("chg002")))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        String message = String.format("{\"channel\":\"%s\",\"data\":\"test\"}", code);
        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", message, "srcFormat", "JSON"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockServer.verify();
    }

    @Test
    @DisplayName("端口路由 Header 覆盖渠道默认值：同名 key 以路由配置为准")
    void dispatch_routeHeaderOverridesChannelHeader() throws Exception {
        String code = "CH_HDR_OVR_" + System.currentTimeMillis();

        HeaderConfig channelHc = new HeaderConfig();
        channelHc.setContentType("text/plain");
        channelHc.setRequestHeaders(Map.of("X-Common", "from-channel", "X-Channel-Only", "yes"));

        HeaderConfig routeHc = new HeaderConfig();
        routeHc.setContentType("application/xml"); // 覆盖 channel 的 text/plain
        routeHc.setRequestHeaders(Map.of("X-Common", "from-route"));

        createChannel(code, channelHc);
        createRoute(code, routeHc);

        mockServer.expect(requestTo(B_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("application/xml")))
                .andExpect(header("X-Common", equalTo("from-route")))
                .andExpect(header("X-Channel-Only", equalTo("yes")))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String message = String.format("{\"channel\":\"%s\"}", code);
        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", message, "srcFormat", "JSON"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockServer.verify();
    }

    @Test
    @DisplayName("不配置 headerConfig 时，dispatch 仍正常运行（向后兼容）")
    void dispatch_noHeaderConfig_backwardCompatible() throws Exception {
        String code = "CH_HDR_COMPAT_" + System.currentTimeMillis();
        createChannel(code, null);
        createRoute(code, null);

        mockServer.expect(requestTo(B_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("compat-ok", MediaType.TEXT_PLAIN));

        String message = String.format("{\"channel\":\"%s\"}", code);
        mockMvc.perform(post("/api/dispatch")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", message, "srcFormat", "JSON"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("compat-ok"));
    }
}
```

- [ ] **Step 2：确认测试失败**

```bash
mvn test -Dtest=M17HeaderDispatchTest 2>&1 | grep -E "Tests run|FAIL|ERROR|BUILD"
```

期望：测试失败（请求头未注入）。

- [ ] **Step 3：重构 `PortRouteService` 中的 `forwardWithRetry`**

将现有私有方法 `forwardWithRetry(PortRoute route, String requestMsg)` 替换为接受 `HttpHeaders` 参数的字节级版本：

```java
    /**
     * 带重试的 HTTP 转发（字节级别，支持 charset 转码）。
     *
     * @param route        路由配置（用于取 portAddress、portMethod、retryCount）
     * @param requestBytes 请求体字节数组（已按目标 charset 编码）
     * @param headers      构造好的 HttpHeaders
     * @return 响应体字节数组
     */
    private byte[] forwardWithRetryBytes(PortRoute route, byte[] requestBytes, org.springframework.http.HttpHeaders headers) {
        String method = route.getPortMethod() != null ? route.getPortMethod().toUpperCase() : "POST";
        int maxAttempts = route.getRetryCount() != null && route.getRetryCount() > 0
                ? route.getRetryCount() : 1;

        org.springframework.http.HttpEntity<byte[]> entity = new org.springframework.http.HttpEntity<>(requestBytes, headers);

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                        route.getPortAddress(),
                        org.springframework.http.HttpMethod.valueOf(method),
                        entity,
                        byte[].class);
                return response.getBody() != null ? response.getBody() : new byte[0];
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("转发第{}次失败，准备重试，url={}, error={}", attempt, route.getPortAddress(), e.getMessage());
                } else {
                    log.error("转发失败（已重试{}次），url={}, error={}", maxAttempts, route.getPortAddress(), e.getMessage());
                }
            }
        }
        throw new BusinessException(502, "端口转发失败（已重试" + maxAttempts + "次）："
                + (lastException != null ? lastException.getMessage() : "未知错误"));
    }
```

- [ ] **Step 4：新增 `buildHttpHeaders()` 辅助方法**

```java
    /**
     * 根据合并后的 HeaderConfig 构造出向 HttpHeaders。
     * Content-Type 未配置时默认 text/plain（保持原有行为）。
     */
    private org.springframework.http.HttpHeaders buildHttpHeaders(
            com.powergateway.model.dto.HeaderConfig config) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();

        // Content-Type
        String ct = config.getContentType();
        String charset = config.getCharset();
        if (ct != null && !ct.isEmpty()) {
            String fullCt = (charset != null && !charset.isEmpty()) ? ct + "; charset=" + charset : ct;
            headers.set(org.springframework.http.HttpHeaders.CONTENT_TYPE, fullCt);
        } else {
            headers.setContentType(MediaType.TEXT_PLAIN);
        }

        // 自定义请求 Header
        if (config.getRequestHeaders() != null) {
            config.getRequestHeaders().forEach(headers::set);
        }

        return headers;
    }
```

- [ ] **Step 5：更新 `dispatch()` 方法**

在 `PortRouteService.dispatch()` 中，将现有步骤替换/增补如下（保持方法签名不变，只修改方法体）：

在 `// 3. 请求方向转换` 前面插入：

```java
        // 2b. 获取渠道 headerConfig 并合并（CHG-002）
        com.powergateway.model.ChannelConfig channelConfig =
                channelConfigService.getByChannelCode(channelCode);
        com.powergateway.model.dto.HeaderConfig channelHc = channelConfig != null
                ? channelConfigService.parseHeaderConfig(channelConfig.getHeaderConfigRaw()) : null;
        com.powergateway.model.dto.HeaderConfig routeHc =
                parseHeaderConfig(route.getHeaderConfigRaw());
        com.powergateway.model.dto.HeaderConfig effectiveHeader =
                com.powergateway.utils.HeaderConfigMerger.merge(channelHc, routeHc);
```

然后将步骤 4（`forwardWithRetry`）及其后的代码替换为：

```java
        // 4. 构造 HttpHeaders（CHG-002）
        org.springframework.http.HttpHeaders httpHeaders = buildHttpHeaders(effectiveHeader);

        // 4b. Charset 转码（CHG-002）：requestMsg(UTF-8) → 目标 charset bytes
        String targetCharset = effectiveHeader.getCharset() != null
                && !effectiveHeader.getCharset().isEmpty()
                ? effectiveHeader.getCharset() : "UTF-8";
        byte[] requestBytes = com.powergateway.utils.CharsetConverter.encodeToBytes(requestMsg, targetCharset);

        // 5. 转发到 B 系统（带重试）
        byte[] responseBytes = forwardWithRetryBytes(route, requestBytes, httpHeaders);
        log.debug("B 系统应答接收，channelCode={}, bodyLen={}", channelCode, responseBytes.length);

        // 5b. 应答 charset 反向转码（CHG-002）：目标 charset bytes → UTF-8 String
        String bResponse = com.powergateway.utils.CharsetConverter.decodeFromBytes(responseBytes, targetCharset);
```

**同时删除**原有的 `forwardWithRetry(PortRoute route, String requestMsg)` 方法（已被 `forwardWithRetryBytes` 替代）。

同时在 `dispatch()` 返回之前，将 `responseHeaders` 加入返回 Map：

```java
        // 返回（含 responseHeaders 供 Controller 注入到 servlet 响应）
        response.put("responseHeaders", effectiveHeader.getResponseHeaders()); // 可为 null
```

- [ ] **Step 6：更新 `PortRouteController.dispatch()` 注入响应头**

将 Controller 的 dispatch 方法改为：

```java
    @PostMapping("/api/dispatch")
    public Result<Map<String, Object>> dispatch(
            @RequestBody DispatchRequest req,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        Map<String, Object> result = portRouteService.dispatch(req);
        // 注入响应头（CHG-002）
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> respHeaders =
                (java.util.Map<String, String>) result.remove("responseHeaders");
        if (respHeaders != null) {
            respHeaders.forEach(httpResponse::setHeader);
        }
        return Result.success(result);
    }
```

> 注：Spring Boot 2.7 使用 `javax.servlet`，若项目实际用 `javax`，将 `jakarta` 改为 `javax`。检查 `pom.xml` 中 `spring-boot-starter-web` 依赖确认。

- [ ] **Step 7：检查 servlet API 包名**

```bash
grep -r "javax.servlet\|jakarta.servlet" backend/src/main/java --include="*.java" | head -5
```

若输出含 `javax.servlet`，则 Step 6 中用 `javax.servlet.http.HttpServletResponse`；若含 `jakarta`，则用 `jakarta.servlet.http.HttpServletResponse`。

- [ ] **Step 8：确认测试通过**

```bash
mvn test -Dtest=M17HeaderDispatchTest 2>&1 | grep -E "Tests run|BUILD"
```

期望：`Tests run: 3, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。

- [ ] **Step 9：全量回归**

```bash
mvn test 2>&1 | grep -E "Tests run|BUILD" | tail -5
```

期望：`BUILD SUCCESS`，所有测试通过（含原有 M17PortRouteTest 的 15 个测试）。

- [ ] **Step 10：提交**

```bash
git add backend/src/main/java/com/powergateway/service/PortRouteService.java \
        backend/src/main/java/com/powergateway/controller/PortRouteController.java \
        backend/src/test/java/com/powergateway/M17HeaderDispatchTest.java
git commit -m "feat(CHG-002): inject merged headers and charset conversion in dispatch()"
```

---

## Task 6：前端 `ChannelConfig.vue` 报文头配置区

**Files:**
- Modify: `frontend/src/views/convert/ChannelConfig.vue`

- [ ] **Step 1：在 `form` 响应式对象中加入 `headerConfig` 初始值**

找到 `resetForm()` 函数（或 `form` 的初始化对象），在 `templateId: null` 后面加：

```js
headerConfig: {
  contentType: '',
  charset: '',
  requestHeaders: [],   // [{key:'', value:''}]
  responseHeaders: []   // [{key:'', value:''}]
}
```

- [ ] **Step 2：在 `openDialog(row)` 中回填 headerConfig**

找到 `openDialog` 函数，在回填其他字段后加：

```js
// 回填 headerConfig（CHG-002）
const hc = row.headerConfig || {}
form.value.headerConfig = {
  contentType: hc.contentType || '',
  charset: hc.charset || '',
  requestHeaders: hc.requestHeaders
    ? Object.entries(hc.requestHeaders).map(([key, value]) => ({ key, value }))
    : [],
  responseHeaders: hc.responseHeaders
    ? Object.entries(hc.responseHeaders).map(([key, value]) => ({ key, value }))
    : []
}
```

- [ ] **Step 3：在 `handleSave()` 中序列化 headerConfig**

找到构造请求 body 的地方，加入 headerConfig 序列化逻辑：

```js
// 序列化 headerConfig（CHG-002）
const toMap = arr => arr.reduce((acc, { key, value }) => {
  if (key && key.trim()) acc[key.trim()] = value
  return acc
}, {})

const headerConfig = {
  contentType: form.value.headerConfig.contentType || null,
  charset: form.value.headerConfig.charset || null,
  requestHeaders: Object.keys(toMap(form.value.headerConfig.requestHeaders)).length
    ? toMap(form.value.headerConfig.requestHeaders) : null,
  responseHeaders: Object.keys(toMap(form.value.headerConfig.responseHeaders)).length
    ? toMap(form.value.headerConfig.responseHeaders) : null
}
```

然后在发送的 body 中加入 `headerConfig`。

- [ ] **Step 4：在弹窗 `<el-form>` 末尾追加"报文头配置"折叠区**

在 `</el-form>` 前面插入：

```vue
<!-- 报文头配置（CHG-002） -->
<el-divider content-position="left" style="margin-top:20px">报文头配置（可选）</el-divider>
<el-form-item label="Content-Type">
  <el-select v-model="form.headerConfig.contentType" placeholder="不设置则使用默认" clearable style="width:100%">
    <el-option label="application/json" value="application/json" />
    <el-option label="application/xml" value="application/xml" />
    <el-option label="text/plain" value="text/plain" />
    <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
  </el-select>
</el-form-item>
<el-form-item label="字符集">
  <el-select v-model="form.headerConfig.charset" placeholder="不设置则使用 UTF-8（不转码）" clearable style="width:100%">
    <el-option label="UTF-8（默认）" value="UTF-8" />
    <el-option label="GBK" value="GBK" />
    <el-option label="ISO-8859-1" value="ISO-8859-1" />
  </el-select>
</el-form-item>
<el-form-item label="出向请求头">
  <div style="width:100%">
    <div
      v-for="(item, idx) in form.headerConfig.requestHeaders"
      :key="idx"
      style="display:flex;gap:8px;margin-bottom:6px"
    >
      <el-input v-model="item.key" placeholder="Header 名" style="flex:1" />
      <el-input v-model="item.value" placeholder="Header 值" style="flex:1" />
      <el-button type="danger" link @click="form.headerConfig.requestHeaders.splice(idx, 1)">删除</el-button>
    </div>
    <el-button size="small" @click="form.headerConfig.requestHeaders.push({ key: '', value: '' })">
      + 添加请求头
    </el-button>
  </div>
</el-form-item>
<el-form-item label="返回响应头">
  <div style="width:100%">
    <div
      v-for="(item, idx) in form.headerConfig.responseHeaders"
      :key="idx"
      style="display:flex;gap:8px;margin-bottom:6px"
    >
      <el-input v-model="item.key" placeholder="Header 名" style="flex:1" />
      <el-input v-model="item.value" placeholder="Header 值" style="flex:1" />
      <el-button type="danger" link @click="form.headerConfig.responseHeaders.splice(idx, 1)">删除</el-button>
    </div>
    <el-button size="small" @click="form.headerConfig.responseHeaders.push({ key: '', value: '' })">
      + 添加响应头
    </el-button>
  </div>
</el-form-item>
```

- [ ] **Step 5：手动验证**

```bash
cd frontend && npm run dev
```

访问 `http://localhost:5173/convert/channel-config`，点击"新增渠道"，确认：
- 弹窗底部出现"报文头配置"区域
- Content-Type 下拉可选
- 字符集下拉可选
- 添加请求头后可删除
- 保存后列表刷新，再次编辑能正确回填

- [ ] **Step 6：提交**

```bash
git add frontend/src/views/convert/ChannelConfig.vue
git commit -m "feat(CHG-002): add header config section to ChannelConfig.vue"
```

---

## Task 7：前端 `PortRoute.vue` 报文头配置区

**Files:**
- Modify: `frontend/src/views/convert/PortRoute.vue`

- [ ] **Step 1：在 `form` 响应式对象中加入 `headerConfig`**

同 Task 6 Step 1，加入相同的初始值。

- [ ] **Step 2：在 `openDialog(row)` 中回填 headerConfig**

同 Task 6 Step 2 相同逻辑。

- [ ] **Step 3：在 `handleSave()` 中序列化 headerConfig**

同 Task 6 Step 3 相同逻辑。

- [ ] **Step 4：在弹窗 `<el-form>` 末尾追加"报文头配置"折叠区**

加入与 Task 6 Step 4 相同的 HTML 结构，在第一个 `<el-divider>` 前增加提示文字：

```vue
<el-alert
  type="info"
  :closable="false"
  style="margin-bottom:12px"
  description="未填写的报文头项将继承渠道默认值（两级合并：渠道默认 < 路由覆盖）"
/>
```

然后加入与 Task 6 Step 4 完全相同的 `<el-divider>` + 四个 `<el-form-item>` 块。

- [ ] **Step 5：手动验证**

访问 `http://localhost:5173/convert/port-route`，点击"新增路由"，确认：
- 弹窗底部出现"报文头配置"区域，顶部有继承提示
- 保存后，回显正确

- [ ] **Step 6：提交**

```bash
git add frontend/src/views/convert/PortRoute.vue
git commit -m "feat(CHG-002): add header config section to PortRoute.vue with inheritance hint"
```

---

## Task 8：全量验证收尾

- [ ] **Step 1：后端全量测试**

```bash
cd backend
mvn test 2>&1 | tail -10
```

期望：`BUILD SUCCESS`，`Tests run: N, Failures: 0, Errors: 0`（N ≥ 130）。

- [ ] **Step 2：前端构建验证**

```bash
cd frontend
npm run build 2>&1 | tail -5
```

期望：`dist/` 生成无报错。

- [ ] **Step 3：更新问题清单**

在 `README/问题清单.md` 的"已规划"部分，将问题2迁移到"已解决"：

```markdown
### 问题2：报文头格式需支持渠道级别的适配配置（已解决）
- **解决时间**：2026-03-27（CHG-002）
- **解决方案**：...（简述）
```

- [ ] **Step 4：最终提交**

```bash
git add README/问题清单.md
git commit -m "docs: mark 问题2 as resolved after CHG-002 implementation"
```
