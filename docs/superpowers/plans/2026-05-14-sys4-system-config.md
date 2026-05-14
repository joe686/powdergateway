# SYS-4 系统配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现系统配置管理模块 —— 内存 Map 缓存所有 KV、提供 REST API 和前端管理页面、配置变更后通过 ApplicationEvent 热更新，各服务不再直接查 `sys_config` 表。

**Architecture:** `SysConfigService` 持有 `ConcurrentHashMap` 内存缓存，`@PostConstruct` 启动加载；`batchUpdate()` 写 DB + 更新 cache + 发布 `ConfigChangedEvent`；`ConfigChangeListener` 监听事件并 evict Redis 模板缓存；原来直接注入 `SysConfigMapper` 的 5 处服务均改为注入 `SysConfigService`；前端 `SystemConfig.vue` 完全数据驱动渲染（按 `groupName` 分组、按 `valueType` 渲染控件）。

**Tech Stack:** Spring Boot 2.7.18, MyBatis-Plus, Spring `ApplicationEvent`, Vue 3, Element Plus

---

## 文件清单

### 新建
| 文件 | 职责 |
|---|---|
| `backend/src/main/java/com/powergateway/event/ConfigChangedEvent.java` | Spring ApplicationEvent，携带变更 KV |
| `backend/src/main/java/com/powergateway/service/SysConfigService.java` | 内存缓存 + 类型化读取 + batchUpdate |
| `backend/src/main/java/com/powergateway/service/ConfigChangeListener.java` | 监听 ConfigChangedEvent，evict Redis 模板缓存 |
| `backend/src/main/java/com/powergateway/controller/SysConfigController.java` | GET /api/config/all + PUT /api/config |
| `backend/src/test/java/com/powergateway/SYS4SysConfigServiceTest.java` | Service 层 TDD 测试 |
| `backend/src/test/java/com/powergateway/SYS4SysConfigControllerTest.java` | Controller 层 TDD 测试 |
| `frontend/src/api/sysConfig.js` | getAllConfig / updateConfig |
| `frontend/src/views/system/SystemConfig.vue` | 数据驱动配置表单 |

### 修改
| 文件 | 改动点 |
|---|---|
| `backend/src/main/resources/db/init.sql` | sys_config 表增加 value_type / group_name 列，更新 INSERT 数据 |
| `backend/src/main/java/com/powergateway/model/SysConfig.java` | 新增 valueType / groupName 字段 |
| `backend/src/main/java/com/powergateway/service/ConvertService.java` | 注入 SysConfigService，TTL 改为动态读取 |
| `backend/src/main/java/com/powergateway/job/PerfAlertJob.java` | 替换 SysConfigMapper → SysConfigService |
| `backend/src/main/java/com/powergateway/job/AuditLogCleanupJob.java` | 替换 SysConfigMapper → SysConfigService |
| `backend/src/main/java/com/powergateway/job/SysLogArchiveJob.java` | 替换 SysConfigMapper → SysConfigService |
| `backend/src/main/java/com/powergateway/service/AuthService.java` | 替换 SysConfigMapper → SysConfigService |
| `backend/src/main/java/com/powergateway/service/PerfStatService.java` | upsertConfig 改为调用 SysConfigService.batchUpdate |
| `frontend/src/router/index.js` | system/config 路由替换 PlaceholderView → SystemConfig |

---

## Task 1: Schema 变更 — sys_config 新增 value_type / group_name

**Files:**
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/main/java/com/powergateway/model/SysConfig.java`

- [ ] **Step 1: 修改 init.sql — CREATE TABLE 添加新列**

  在 `sys_config` 表的 `description` 行后、`update_time` 行前插入两列：

  ```sql
  -- 改前:
  CREATE TABLE IF NOT EXISTS sys_config (
    config_key VARCHAR(128) PRIMARY KEY,
    config_value VARCHAR(1024),
    description VARCHAR(512),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  );

  -- 改后:
  CREATE TABLE IF NOT EXISTS sys_config (
    config_key   VARCHAR(128) PRIMARY KEY,
    config_value VARCHAR(1024),
    description  VARCHAR(512),
    value_type   VARCHAR(32)  DEFAULT 'string' COMMENT 'number/boolean/string',
    group_name   VARCHAR(64)  DEFAULT '其他'   COMMENT '前端分组名',
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  );
  ```

- [ ] **Step 2: 修改 init.sql — 替换 INSERT IGNORE 为包含新列的版本**

  将现有 `INSERT IGNORE INTO sys_config ...` 块整体替换为：

  ```sql
  INSERT IGNORE INTO sys_config (config_key, config_value, description, value_type, group_name) VALUES
  ('cache.query.ttl',         '300',  '查询缓存 TTL（秒）',          'number',  '缓存配置'),
  ('cache.template.ttl',      '600',  '模板缓存 TTL（秒）',          'number',  '缓存配置'),
  ('sys.log.retention.days',  '30',   '操作日志归档天数',             'number',  '日志配置'),
  ('audit.log.retention.days','365',  '审计日志保留天数',             'number',  '日志配置'),
  ('sql.log.retention.days',  '90',   'SQL 日志保留天数',             'number',  '日志配置'),
  ('log_menu_enabled',        'true', '日志管理菜单显示开关',         'boolean', '日志配置'),
  ('alert_fail_rate',         '5',    '告警失败率阈值（%）',          'number',  '告警配置'),
  ('alert_response_ms',       '1000', '告警响应时间阈值（ms）',       'number',  '告警配置');
  ```

  > **MySQL 生产环境升级**：在执行前先运行：
  > ```sql
  > ALTER TABLE sys_config
  >   ADD COLUMN IF NOT EXISTS value_type VARCHAR(32) DEFAULT 'string',
  >   ADD COLUMN IF NOT EXISTS group_name VARCHAR(64) DEFAULT '其他';
  > ```

- [ ] **Step 3: 修改 SysConfig.java — 新增两个字段**

  ```java
  @TableField("value_type")
  private String valueType;

  @TableField("group_name")
  private String groupName;
  ```

  完整文件内容（`backend/src/main/java/com/powergateway/model/SysConfig.java`）：

  ```java
  package com.powergateway.model;

  import com.baomidou.mybatisplus.annotation.*;
  import lombok.Data;
  import java.time.LocalDateTime;

  @Data
  @TableName("sys_config")
  public class SysConfig {

      @TableId(type = IdType.INPUT)
      private String configKey;

      private String configValue;

      private String description;

      @TableField("value_type")
      private String valueType;

      @TableField("group_name")
      private String groupName;

      @TableField(fill = FieldFill.INSERT_UPDATE)
      private LocalDateTime updateTime;
  }
  ```

- [ ] **Step 4: 验证现有测试通过**

  ```bash
  cd backend
  mvn test
  ```

  期望：全部测试 PASS，无编译错误，无回归。

- [ ] **Step 5: Commit**

  ```bash
  git add backend/src/main/resources/db/init.sql \
          backend/src/main/java/com/powergateway/model/SysConfig.java
  git commit -m "feat(SYS-4): add value_type and group_name to sys_config schema"
  ```

---

## Task 2: ConfigChangedEvent

**Files:**
- Create: `backend/src/main/java/com/powergateway/event/ConfigChangedEvent.java`

- [ ] **Step 1: 创建 event 包并创建事件类**

  ```java
  package com.powergateway.event;

  import org.springframework.context.ApplicationEvent;
  import java.util.Map;

  public class ConfigChangedEvent extends ApplicationEvent {

      private final Map<String, String> changedEntries;

      public ConfigChangedEvent(Object source, Map<String, String> changedEntries) {
          super(source);
          this.changedEntries = changedEntries;
      }

      public Map<String, String> getChangedEntries() {
          return changedEntries;
      }
  }
  ```

- [ ] **Step 2: Commit**

  ```bash
  git add backend/src/main/java/com/powergateway/event/ConfigChangedEvent.java
  git commit -m "feat(SYS-4): add ConfigChangedEvent"
  ```

---

## Task 3: SysConfigService — TDD

**Files:**
- Create: `backend/src/test/java/com/powergateway/SYS4SysConfigServiceTest.java`
- Create: `backend/src/main/java/com/powergateway/service/SysConfigService.java`

- [ ] **Step 1: 写失败测试**

  创建 `backend/src/test/java/com/powergateway/SYS4SysConfigServiceTest.java`：

  ```java
  package com.powergateway;

  import com.powergateway.dao.SysConfigMapper;
  import com.powergateway.event.ConfigChangedEvent;
  import com.powergateway.model.SysConfig;
  import com.powergateway.service.SysConfigService;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.boot.test.context.TestConfiguration;
  import org.springframework.context.ApplicationListener;
  import org.springframework.context.annotation.Bean;
  import org.springframework.test.context.ActiveProfiles;
  import org.springframework.transaction.annotation.Transactional;

  import java.util.ArrayList;
  import java.util.List;
  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;

  @SpringBootTest
  @ActiveProfiles("test")
  @Transactional
  class SYS4SysConfigServiceTest {

      @Autowired private SysConfigService sysConfigService;
      @Autowired private SysConfigMapper sysConfigMapper;
      @Autowired private EventCapture eventCapture;

      @TestConfiguration
      static class TestConfig {
          @Bean
          EventCapture eventCapture() { return new EventCapture(); }
      }

      static class EventCapture implements ApplicationListener<ConfigChangedEvent> {
          final List<ConfigChangedEvent> events = new ArrayList<>();
          @Override
          public void onApplicationEvent(ConfigChangedEvent event) { events.add(event); }
          void reset() { events.clear(); }
      }

      @BeforeEach
      void resetCache() {
          sysConfigService.init(); // 每次测试前从 DB（已回滚干净）重新加载缓存
          eventCapture.reset();
      }

      @Test
      void getInt_正常读取() {
          assertThat(sysConfigService.getInt("cache.query.ttl", 0)).isEqualTo(300);
      }

      @Test
      void getInt_key不存在_返回默认值() {
          assertThat(sysConfigService.getInt("no.such.key", 99)).isEqualTo(99);
      }

      @Test
      void getBoolean_返回true() {
          assertThat(sysConfigService.getBoolean("log_menu_enabled", false)).isTrue();
      }

      @Test
      void getAll_返回含新列的列表() {
          List<SysConfig> all = sysConfigService.getAll();
          assertThat(all).isNotEmpty();
          assertThat(all.get(0).getGroupName()).isNotNull();
          assertThat(all.get(0).getValueType()).isNotNull();
      }

      @Test
      void batchUpdate_更新内存缓存() {
          sysConfigService.batchUpdate(Map.of("cache.query.ttl", "999"));
          assertThat(sysConfigService.getInt("cache.query.ttl", 0)).isEqualTo(999);
      }

      @Test
      void batchUpdate_写入DB() {
          sysConfigService.batchUpdate(Map.of("cache.query.ttl", "888"));
          SysConfig cfg = sysConfigMapper.selectById("cache.query.ttl");
          assertThat(cfg.getConfigValue()).isEqualTo("888");
      }

      @Test
      void batchUpdate_发布ConfigChangedEvent() {
          sysConfigService.batchUpdate(Map.of("alert_fail_rate", "10"));
          assertThat(eventCapture.events).hasSize(1);
          assertThat(eventCapture.events.get(0).getChangedEntries())
              .containsEntry("alert_fail_rate", "10");
      }

      @Test
      void batchUpdate_空Map_不发布事件() {
          sysConfigService.batchUpdate(Map.of());
          assertThat(eventCapture.events).isEmpty();
      }
  }
  ```

- [ ] **Step 2: 确认编译失败（SysConfigService 不存在）**

  ```bash
  cd backend
  mvn test -Dtest=SYS4SysConfigServiceTest 2>&1 | head -20
  ```

  期望：编译错误 `cannot find symbol: class SysConfigService`。

- [ ] **Step 3: 实现 SysConfigService**

  创建 `backend/src/main/java/com/powergateway/service/SysConfigService.java`：

  ```java
  package com.powergateway.service;

  import com.powergateway.dao.SysConfigMapper;
  import com.powergateway.event.ConfigChangedEvent;
  import com.powergateway.model.SysConfig;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.context.ApplicationEventPublisher;
  import org.springframework.stereotype.Service;

  import javax.annotation.PostConstruct;
  import java.util.List;
  import java.util.Map;
  import java.util.concurrent.ConcurrentHashMap;

  @Service
  public class SysConfigService {

      private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

      @Autowired private SysConfigMapper sysConfigMapper;
      @Autowired private ApplicationEventPublisher eventPublisher;

      @PostConstruct
      public void init() {
          List<SysConfig> all = sysConfigMapper.selectList(null);
          cache.clear();
          all.forEach(c -> {
              if (c.getConfigValue() != null) {
                  cache.put(c.getConfigKey(), c.getConfigValue());
              }
          });
      }

      public String getString(String key, String defaultVal) {
          String val = cache.get(key);
          return val != null ? val : defaultVal;
      }

      public int getInt(String key, int defaultVal) {
          String val = cache.get(key);
          if (val == null) return defaultVal;
          try { return Integer.parseInt(val.trim()); }
          catch (NumberFormatException e) { return defaultVal; }
      }

      public boolean getBoolean(String key, boolean defaultVal) {
          String val = cache.get(key);
          if (val == null) return defaultVal;
          return Boolean.parseBoolean(val.trim());
      }

      public List<SysConfig> getAll() {
          return sysConfigMapper.selectList(null);
      }

      public void batchUpdate(Map<String, String> updates) {
          if (updates.isEmpty()) return;
          updates.forEach((key, value) -> {
              SysConfig cfg = sysConfigMapper.selectById(key);
              if (cfg == null) {
                  cfg = new SysConfig();
                  cfg.setConfigKey(key);
                  cfg.setConfigValue(value);
                  sysConfigMapper.insert(cfg);
              } else {
                  cfg.setConfigValue(value);
                  sysConfigMapper.updateById(cfg);
              }
              cache.put(key, value);
          });
          eventPublisher.publishEvent(new ConfigChangedEvent(this, updates));
      }
  }
  ```

- [ ] **Step 4: 运行测试确认通过**

  ```bash
  mvn test -Dtest=SYS4SysConfigServiceTest
  ```

  期望：`Tests run: 8, Failures: 0, Errors: 0`。

- [ ] **Step 5: Commit**

  ```bash
  git add backend/src/test/java/com/powergateway/SYS4SysConfigServiceTest.java \
          backend/src/main/java/com/powergateway/service/SysConfigService.java
  git commit -m "feat(SYS-4): implement SysConfigService with in-memory cache and ConfigChangedEvent"
  ```

---

## Task 4: ConfigChangeListener — 模板缓存热更新

**Files:**
- Create: `backend/src/main/java/com/powergateway/service/ConfigChangeListener.java`

- [ ] **Step 1: 实现 ConfigChangeListener**

  创建 `backend/src/main/java/com/powergateway/service/ConfigChangeListener.java`：

  ```java
  package com.powergateway.service;

  import com.powergateway.event.ConfigChangedEvent;
  import lombok.extern.slf4j.Slf4j;
  import org.springframework.beans.factory.ObjectProvider;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.context.event.EventListener;
  import org.springframework.data.redis.core.StringRedisTemplate;
  import org.springframework.stereotype.Component;

  import java.util.Set;

  @Slf4j
  @Component
  public class ConfigChangeListener {

      @Autowired
      private ObjectProvider<StringRedisTemplate> redisProvider;

      @EventListener
      public void onConfigChanged(ConfigChangedEvent event) {
          if (!event.getChangedEntries().containsKey("cache.template.ttl")) {
              return;
          }
          StringRedisTemplate redis = redisProvider.getIfAvailable();
          if (redis == null) {
              return;
          }
          Set<String> keys = redis.keys("template:*");
          if (keys != null && !keys.isEmpty()) {
              redis.delete(keys);
              log.info("cache.template.ttl changed, evicted {} template cache entries", keys.size());
          }
      }
  }
  ```

- [ ] **Step 2: 运行全量测试确认无回归**

  ```bash
  mvn test
  ```

  期望：全部测试通过。

- [ ] **Step 3: Commit**

  ```bash
  git add backend/src/main/java/com/powergateway/service/ConfigChangeListener.java
  git commit -m "feat(SYS-4): add ConfigChangeListener to evict template cache on TTL change"
  ```

---

## Task 5: SysConfigController — TDD

**Files:**
- Create: `backend/src/test/java/com/powergateway/SYS4SysConfigControllerTest.java`
- Create: `backend/src/main/java/com/powergateway/controller/SysConfigController.java`

- [ ] **Step 1: 写失败测试**

  创建 `backend/src/test/java/com/powergateway/SYS4SysConfigControllerTest.java`：

  ```java
  package com.powergateway;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.powergateway.model.SysConfig;
  import com.powergateway.service.SysConfigService;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
  import org.springframework.boot.test.mock.mockito.MockBean;
  import org.springframework.http.MediaType;
  import org.springframework.test.context.ActiveProfiles;
  import org.springframework.test.web.servlet.MockMvc;

  import java.util.List;
  import java.util.Map;

  import static org.mockito.BDDMockito.given;
  import static org.mockito.Mockito.verify;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

  @WebMvcTest(controllers = com.powergateway.controller.SysConfigController.class)
  @ActiveProfiles("test")
  class SYS4SysConfigControllerTest {

      @Autowired MockMvc mockMvc;
      @MockBean SysConfigService sysConfigService;
      @Autowired ObjectMapper objectMapper;

      @Test
      void getAll_返回200和配置列表() throws Exception {
          SysConfig cfg = new SysConfig();
          cfg.setConfigKey("alert_fail_rate");
          cfg.setConfigValue("5");
          cfg.setGroupName("告警配置");
          cfg.setValueType("number");
          cfg.setDescription("告警失败率阈值（%）");
          given(sysConfigService.getAll()).willReturn(List.of(cfg));

          mockMvc.perform(get("/api/config/all"))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.code").value(200))
                 .andExpect(jsonPath("$.data[0].configKey").value("alert_fail_rate"))
                 .andExpect(jsonPath("$.data[0].groupName").value("告警配置"))
                 .andExpect(jsonPath("$.data[0].valueType").value("number"));
      }

      @Test
      void update_调用batchUpdate并返回200() throws Exception {
          Map<String, String> body = Map.of("alert_fail_rate", "10");

          mockMvc.perform(put("/api/config")
                 .contentType(MediaType.APPLICATION_JSON)
                 .content(objectMapper.writeValueAsString(body)))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.code").value(200));

          verify(sysConfigService).batchUpdate(Map.of("alert_fail_rate", "10"));
      }
  }
  ```

- [ ] **Step 2: 确认编译失败（SysConfigController 不存在）**

  ```bash
  mvn test -Dtest=SYS4SysConfigControllerTest 2>&1 | head -20
  ```

  期望：编译错误 `cannot find symbol: class SysConfigController`。

- [ ] **Step 3: 实现 SysConfigController**

  创建 `backend/src/main/java/com/powergateway/controller/SysConfigController.java`：

  ```java
  package com.powergateway.controller;

  import cn.dev33.satoken.annotation.SaCheckRole;
  import com.powergateway.common.Result;
  import com.powergateway.model.SysConfig;
  import com.powergateway.service.SysConfigService;
  import io.swagger.v3.oas.annotations.Operation;
  import io.swagger.v3.oas.annotations.tags.Tag;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.web.bind.annotation.*;

  import java.util.List;
  import java.util.Map;

  @Tag(name = "系统配置")
  @RestController
  @RequestMapping("/api/config")
  public class SysConfigController {

      @Autowired private SysConfigService sysConfigService;

      @Operation(summary = "获取全部系统配置")
      @GetMapping("/all")
      public Result<List<SysConfig>> getAll() {
          return Result.success(sysConfigService.getAll());
      }

      @Operation(summary = "批量更新系统配置（仅管理员）")
      @SaCheckRole("admin")
      @PutMapping
      public Result<Void> update(@RequestBody Map<String, String> updates) {
          sysConfigService.batchUpdate(updates);
          return Result.success();
      }
  }
  ```

- [ ] **Step 4: 运行测试确认通过**

  ```bash
  mvn test -Dtest=SYS4SysConfigControllerTest
  ```

  期望：`Tests run: 2, Failures: 0, Errors: 0`。

- [ ] **Step 5: Commit**

  ```bash
  git add backend/src/test/java/com/powergateway/SYS4SysConfigControllerTest.java \
          backend/src/main/java/com/powergateway/controller/SysConfigController.java
  git commit -m "feat(SYS-4): add SysConfigController (GET /api/config/all, PUT /api/config)"
  ```

---

## Task 6: 改造现有服务 — 统一走 SysConfigService

**Files:**
- Modify: `backend/src/main/java/com/powergateway/job/PerfAlertJob.java`
- Modify: `backend/src/main/java/com/powergateway/job/AuditLogCleanupJob.java`
- Modify: `backend/src/main/java/com/powergateway/job/SysLogArchiveJob.java`
- Modify: `backend/src/main/java/com/powergateway/service/AuthService.java`
- Modify: `backend/src/main/java/com/powergateway/service/PerfStatService.java`
- Modify: `backend/src/main/java/com/powergateway/service/ConvertService.java`

- [ ] **Step 1: 修改 PerfAlertJob**

  删除 `@Autowired SysConfigMapper sysConfigMapper` 及其 import，添加 `SysConfigService`，删除 `readDouble` / `readInt` 私有方法，直接调用 Service：

  ```java
  // 删除
  @Autowired private SysConfigMapper sysConfigMapper;

  // 添加
  @Autowired private SysConfigService sysConfigService;

  // checkAndAlert() 内原有的两行替换
  // 改前:
  double failRate   = readDouble(KEY_FAIL_RATE, DEFAULT_FAIL_RATE);
  int    responseMs = readInt(KEY_RESPONSE_MS, DEFAULT_RESPONSE_MS);

  // 改后:
  double failRate   = sysConfigService.getInt(KEY_FAIL_RATE, (int) DEFAULT_FAIL_RATE);
  int    responseMs = sysConfigService.getInt(KEY_RESPONSE_MS, DEFAULT_RESPONSE_MS);
  ```

  同时删除文件末尾的 `readDouble()` 和 `readInt()` 两个私有方法，以及已不再需要的 `SysConfigMapper` / `SysConfig` import。

  > **注意**：`KEY_FAIL_RATE = "alert_fail_rate"` 其值本来是 double，但 `sys_config` 存储的是整数 "5"。改用 `getInt` 即可，类型从 double 降为 int 没有精度损失（5% 告警阈值不需要小数）。`DEFAULT_FAIL_RATE` 强转为 int。

- [ ] **Step 2: 修改 AuditLogCleanupJob**

  ```java
  // 删除
  @Autowired private SysConfigMapper sysConfigMapper;

  // 添加
  @Autowired private SysConfigService sysConfigService;

  // cleanup() 内原有的 5 行读取配置逻辑替换为 1 行：
  // 改前:
  int retentionDays = DEFAULT_RETENTION_DAYS;
  SysConfig config = sysConfigMapper.selectById(CONFIG_KEY);
  if (config != null && config.getConfigValue() != null) {
      try { retentionDays = Integer.parseInt(config.getConfigValue().trim()); }
      catch (NumberFormatException ignored) {}
  }

  // 改后:
  int retentionDays = sysConfigService.getInt(CONFIG_KEY, DEFAULT_RETENTION_DAYS);
  ```

  删除已不需要的 `SysConfigMapper` / `SysConfig` import。

- [ ] **Step 3: 修改 SysLogArchiveJob**

  ```java
  // 删除
  @Autowired private SysConfigMapper sysConfigMapper;

  // 添加
  @Autowired private SysConfigService sysConfigService;

  // archive() 内原有的 5 行读取配置逻辑替换为 1 行：
  // 改前:
  int retentionDays = DEFAULT_RETENTION_DAYS;
  SysConfig config = sysConfigMapper.selectById(CONFIG_KEY);
  if (config != null && config.getConfigValue() != null) {
      try { retentionDays = Integer.parseInt(config.getConfigValue().trim()); }
      catch (NumberFormatException ignored) {}
  }

  // 改后:
  int retentionDays = sysConfigService.getInt(CONFIG_KEY, DEFAULT_RETENTION_DAYS);
  ```

- [ ] **Step 4: 修改 AuthService**

  ```java
  // 删除
  @Autowired private SysConfigMapper sysConfigMapper;

  // 添加
  @Autowired private SysConfigService sysConfigService;

  // getMenuForCurrentUser() 末尾的 4 行替换为 3 行：
  // 改前:
  SysConfig logConfig = sysConfigMapper.selectById(MenuPermission.LOG_MENU_CONFIG_KEY);
  if (logConfig != null && "false".equalsIgnoreCase(logConfig.getConfigValue())) {
      menus.remove(MenuPermission.LOG_MENU_PATH);
  }

  // 改后:
  if (!sysConfigService.getBoolean(MenuPermission.LOG_MENU_CONFIG_KEY, true)) {
      menus.remove(MenuPermission.LOG_MENU_PATH);
  }
  ```

  删除已不需要的 `SysConfigMapper` / `SysConfig` import。

- [ ] **Step 5: 修改 PerfStatService**

  ```java
  // 删除
  @Autowired private SysConfigMapper sysConfigMapper;

  // 添加
  @Autowired private SysConfigService sysConfigService;

  // updateAlertConfig() 改为：
  public void updateAlertConfig(AlertConfigRequest req) {
      Map<String, String> updates = new java.util.LinkedHashMap<>();
      if (req.getFailRate() != null) {
          updates.put("alert_fail_rate", String.format("%.1f", req.getFailRate()));
      }
      if (req.getResponseMs() != null) {
          updates.put("alert_response_ms", String.valueOf(req.getResponseMs()));
      }
      if (!updates.isEmpty()) {
          sysConfigService.batchUpdate(updates);
      }
  }
  ```

  删除 `upsertConfig()` 私有方法，删除已不需要的 `SysConfigMapper` / `SysConfig` import。

- [ ] **Step 6: 修改 ConvertService — 动态读取模板缓存 TTL**

  `ConvertService` 使用 `@RequiredArgsConstructor`（Lombok），添加一个 final 字段即自动注入。

  ```java
  // 在已有 final 字段末尾添加：
  private final SysConfigService sysConfigService;

  // 删除静态常量：
  private static final long TEMPLATE_CACHE_TTL_SECONDS = 600;

  // 在所有使用 TEMPLATE_CACHE_TTL_SECONDS 的地方替换为方法调用：
  // 改前:  redis.opsForValue().set(cacheKey, json, TEMPLATE_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
  // 改后:  redis.opsForValue().set(cacheKey, json, sysConfigService.getInt("cache.template.ttl", 600), TimeUnit.SECONDS);
  ```

- [ ] **Step 7: 运行全量测试确认无回归**

  ```bash
  mvn test
  ```

  期望：全部测试通过（原测试数 +8 新增 = 全绿）。

- [ ] **Step 8: Commit**

  ```bash
  git add backend/src/main/java/com/powergateway/job/PerfAlertJob.java \
          backend/src/main/java/com/powergateway/job/AuditLogCleanupJob.java \
          backend/src/main/java/com/powergateway/job/SysLogArchiveJob.java \
          backend/src/main/java/com/powergateway/service/AuthService.java \
          backend/src/main/java/com/powergateway/service/PerfStatService.java \
          backend/src/main/java/com/powergateway/service/ConvertService.java
  git commit -m "refactor(SYS-4): replace direct SysConfigMapper calls with SysConfigService"
  ```

---

## Task 7: 前端 — SystemConfig.vue

**Files:**
- Create: `frontend/src/api/sysConfig.js`
- Create: `frontend/src/views/system/SystemConfig.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 创建 API 模块**

  创建 `frontend/src/api/sysConfig.js`：

  ```js
  import request from '@/api/request'

  export const getAllConfig = () => request.get('/config/all')
  export const updateConfig = (data) => request.put('/config', data)
  ```

- [ ] **Step 2: 创建 SystemConfig.vue**

  创建 `frontend/src/views/system/SystemConfig.vue`：

  ```vue
  <template>
    <div class="sys-config-page">
      <div class="page-header">
        <span class="title">系统配置</span>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!isAdmin"
          @click="handleSave"
        >
          保存
        </el-button>
      </div>

      <el-card
        v-for="(items, group) in groupedConfigs"
        :key="group"
        class="group-card"
      >
        <template #header>{{ group }}</template>
        <el-form label-width="220px" label-position="left">
          <el-form-item
            v-for="item in items"
            :key="item.configKey"
            :label="item.configKey"
          >
            <el-input-number
              v-if="item.valueType === 'number'"
              :model-value="Number(form[item.configKey])"
              :min="0"
              @update:model-value="val => form[item.configKey] = String(val)"
            />
            <el-switch
              v-else-if="item.valueType === 'boolean'"
              v-model="form[item.configKey]"
              active-value="true"
              inactive-value="false"
            />
            <el-input
              v-else
              v-model="form[item.configKey]"
              style="width: 240px"
            />
            <span class="desc">{{ item.description }}</span>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </template>

  <script setup>
  import { ref, computed, onMounted } from 'vue'
  import { ElMessage } from 'element-plus'
  import { useUserStore } from '@/store/user'
  import { getAllConfig, updateConfig } from '@/api/sysConfig'

  const userStore = useUserStore()
  const isAdmin = computed(() => userStore.role === 'admin')

  const configs = ref([])
  const form = ref({})
  const saving = ref(false)

  const groupedConfigs = computed(() => {
    const groups = {}
    configs.value.forEach(item => {
      const g = item.groupName || '其他'
      if (!groups[g]) groups[g] = []
      groups[g].push(item)
    })
    return groups
  })

  onMounted(async () => {
    configs.value = await getAllConfig()
    configs.value.forEach(item => {
      form.value[item.configKey] = item.configValue ?? ''
    })
  })

  async function handleSave() {
    saving.value = true
    try {
      await updateConfig(form.value)
      ElMessage.success('配置已保存，立即生效')
    } finally {
      saving.value = false
    }
  }
  </script>

  <style scoped>
  .sys-config-page { padding: 20px; }
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }
  .title { font-size: 18px; font-weight: 600; }
  .group-card { margin-bottom: 16px; }
  .desc { margin-left: 12px; color: #909399; font-size: 12px; }
  </style>
  ```

- [ ] **Step 3: 更新路由**

  修改 `frontend/src/router/index.js`，将 `system/config` 路由的 component 从 `PlaceholderView` 改为 `SystemConfig`：

  ```js
  // 改前:
  {
    path: 'system/config',
    name: 'SysConfig',
    component: () => import('@/views/placeholder/PlaceholderView.vue'),
    meta: { title: '系统配置' }
  },

  // 改后:
  {
    path: 'system/config',
    name: 'SysConfig',
    component: () => import('@/views/system/SystemConfig.vue'),
    meta: { title: '系统配置' }
  },
  ```

- [ ] **Step 4: 启动前端验证**

  ```bash
  cd frontend
  npm run dev
  ```

  验证清单：
  - 以 admin 登录，进入「系统配置」菜单，页面按缓存配置/日志配置/告警配置分三组展示
  - 数字类型显示 `el-input-number`，布尔类型显示 `el-switch`
  - 修改告警失败率为 10，点保存，提示"配置已保存，立即生效"
  - 以 user 角色登录，「保存」按钮置灰不可点

- [ ] **Step 5: Commit**

  ```bash
  git add frontend/src/api/sysConfig.js \
          frontend/src/views/system/SystemConfig.vue \
          frontend/src/router/index.js
  git commit -m "feat(SYS-4): add SystemConfig.vue with data-driven KV editor"
  ```

---

## Task 8: 运行全量测试 + Git Push

- [ ] **Step 1: 全量测试**

  ```bash
  cd backend
  mvn test
  ```

  期望：全部测试通过，无回归。

- [ ] **Step 2: Push**

  ```bash
  git push origin master
  ```

---

## 验收检查清单

- [ ] 修改 `alert_fail_rate` 后，下次 `PerfAlertJob` 执行使用新值（日志可确认）
- [ ] 修改 `cache.template.ttl` 后，Redis `template:*` 键被清除
- [ ] 关闭 `log_menu_enabled` 后，`/api/auth/menu` 不再返回日志菜单路径
- [ ] 前端按分组展示全部 KV，数字/开关控件正确渲染
- [ ] 非 admin 角色保存按钮置灰
- [ ] `mvn test` 全部绿色，总数不少于原来 +8
