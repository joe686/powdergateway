# M2-7 接口发布 + Swagger 集成 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 M2-3～M2-6 已配置的接口添加发布/禁用状态管理，暴露对外统一执行入口 `POST /api/exec/{id}`（无需 token），并动态同步 Swagger 文档，配套前端接口列表页。

**Architecture:** 新建 `ExecController` 处理对外执行请求（排除 Sa-Token），在 `InterfaceConfigService` 扩展 publish/disable/executeQuery 方法；`OpenApiDynamicCustomizer` 在每次请求 `/v3/api-docs` 时动态注册已发布接口。前端新增 `InterfaceList.vue` 展示状态和操作。

**Tech Stack:** Spring Boot 2.7.x、MyBatis-Plus、Sa-Token 1.37.0、springdoc-openapi-ui 1.7.0、Vue 3、Element Plus

---

## 文件清单

| 操作 | 文件路径 |
|------|---------|
| 新建 | `backend/src/main/java/com/powergateway/model/dto/ExecRequest.java` |
| 新建 | `backend/src/main/java/com/powergateway/controller/ExecController.java` |
| 新建 | `backend/src/main/java/com/powergateway/config/OpenApiDynamicCustomizer.java` |
| 新建 | `backend/src/test/java/com/powergateway/M27PublishTest.java` |
| 新建 | `frontend/src/views/interface/InterfaceList.vue` |
| 修改 | `backend/src/main/java/com/powergateway/utils/QueryBuilder.java` |
| 修改 | `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java` |
| 修改 | `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java` |
| 修改 | `backend/src/main/java/com/powergateway/config/SaTokenConfig.java` |
| 修改 | `frontend/src/api/interface.js` |
| 修改 | `frontend/src/router/index.js` |

---

## Task 1：QueryBuilder 扩展全量与分页查询方法

**Files:**
- Modify: `backend/src/main/java/com/powergateway/utils/QueryBuilder.java`
- Test: `backend/src/test/java/com/powergateway/M27QueryBuilderTest.java`（新建）

- [ ] **Step 1：写失败测试**

新建 `backend/src/test/java/com/powergateway/M27QueryBuilderTest.java`：

```java
package com.powergateway;

import com.powergateway.model.dto.QueryConfigJson;
import com.powergateway.model.dto.QueryConfigJson.FieldDef;
import com.powergateway.model.dto.QueryConfigJson.TableDef;
import com.powergateway.utils.QueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("M2-7 QueryBuilder 全量与分页")
class M27QueryBuilderTest {

    private QueryConfigJson simpleConfig() {
        QueryConfigJson c = new QueryConfigJson();
        TableDef t = new TableDef();
        t.setName("orders");
        t.setAlias("o");
        c.setTables(Collections.singletonList(t));
        FieldDef f = new FieldDef();
        f.setTable("o");
        f.setColumn("id");
        f.setAlias("id");
        c.setFields(Collections.singletonList(f));
        c.setConditions(Collections.emptyList());
        c.setJoins(Collections.emptyList());
        return c;
    }

    @Test
    void buildFull_不含LIMIT() {
        QueryBuilder.SqlResult r = QueryBuilder.buildFull(simpleConfig(), new HashMap<>());
        assertFalse(r.sql.contains("LIMIT"), "全量查询不应含 LIMIT，实际 SQL: " + r.sql);
    }

    @Test
    void buildPaginated_含正确LIMIT和OFFSET() {
        QueryBuilder.SqlResult r = QueryBuilder.buildPaginated(simpleConfig(), new HashMap<>(), 2, 5);
        assertTrue(r.sql.contains("LIMIT 5"), "应含 LIMIT 5，实际 SQL: " + r.sql);
        assertTrue(r.sql.contains("OFFSET 5"), "page=2 时应含 OFFSET 5，实际 SQL: " + r.sql);
    }

    @Test
    void buildPaginated_第一页OFFSET为零() {
        QueryBuilder.SqlResult r = QueryBuilder.buildPaginated(simpleConfig(), new HashMap<>(), 1, 10);
        assertTrue(r.sql.contains("LIMIT 10"), "实际 SQL: " + r.sql);
        assertTrue(r.sql.contains("OFFSET 0"), "第一页 OFFSET 应为 0，实际 SQL: " + r.sql);
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
cd backend && mvn test -Dtest=M27QueryBuilderTest -q 2>&1 | tail -20
```

预期：`BUILD FAILURE`，报 `buildFull`/`buildPaginated` 方法不存在。

- [ ] **Step 3：在 QueryBuilder 中新增两个静态方法**

在 `backend/src/main/java/com/powergateway/utils/QueryBuilder.java` 的 `build()` 方法下方追加：

```java
/**
 * 全量查询（不添加 LIMIT），用于已发布接口的实际执行。
 */
public static SqlResult buildFull(QueryConfigJson config, Map<String, Object> params) {
    if (config == null || config.getTables() == null || config.getTables().isEmpty()) {
        throw new IllegalArgumentException("配置中至少需要一张表");
    }
    StringBuilder sql = new StringBuilder();
    List<Object> paramValues = new ArrayList<>();
    appendSelect(sql, config.getFields());
    appendFrom(sql, config.getTables().get(0));
    appendJoins(sql, config.getTables(), config.getJoins());
    appendWhere(sql, config.getConditions(), params, paramValues);
    return new SqlResult(sql.toString(), paramValues);
}

/**
 * 分页查询，page 从 1 开始，OFFSET = (page-1) * pageSize。
 */
public static SqlResult buildPaginated(QueryConfigJson config, Map<String, Object> params,
                                        int page, int pageSize) {
    SqlResult full = buildFull(config, params);
    String pagedSql = full.sql + " LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize);
    return new SqlResult(pagedSql, full.params);
}
```

- [ ] **Step 4：运行测试，确认全部通过**

```bash
cd backend && mvn test -Dtest=M27QueryBuilderTest -q 2>&1 | tail -10
```

预期：`BUILD SUCCESS`，3 个测试通过。

- [ ] **Step 5：回归确认 QueryBuilder 现有测试不受影响**

```bash
cd backend && mvn test -Dtest=M23QueryConfigTest -q 2>&1 | tail -10
```

预期：`BUILD SUCCESS`。

- [ ] **Step 6：提交**

```bash
cd backend
git add src/main/java/com/powergateway/utils/QueryBuilder.java \
        src/test/java/com/powergateway/M27QueryBuilderTest.java
git commit -m "feat(M2-7): QueryBuilder 新增 buildFull 和 buildPaginated 方法"
```

---

## Task 2：InterfaceConfigService 扩展（publish / disable / executeQuery / delete 校验）

**Files:**
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`
- Test: `backend/src/test/java/com/powergateway/M27PublishTest.java`（新建）

- [ ] **Step 1：写失败测试**

新建 `backend/src/test/java/com/powergateway/M27PublishTest.java`：

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("M2-7 接口发布/禁用/删除校验/executeQuery")
class M27PublishTest {

    @Autowired private InterfaceConfigService service;

    private Long createInterface(String type, String configJson) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("M27测试接口_" + type);
        req.setDbConnectionId(1L);
        req.setType(type);
        req.setConfigJson(configJson);
        return service.save(req);
    }

    private static final String SELECT_CONFIG =
        "{\"tables\":[{\"name\":\"m27_test\",\"alias\":\"t\"}]," +
        "\"fields\":[{\"table\":\"t\",\"column\":\"id\",\"alias\":\"id\"}]," +
        "\"conditions\":[],\"joins\":[]}";

    @Test
    void 发布接口_状态变为published且path写入正确() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        InterfaceConfig cfg = service.getById(id);
        assertEquals("published", cfg.getStatus());
        assertEquals("/api/exec/" + id, cfg.getPath());
    }

    @Test
    void 禁用已发布接口_状态变为disabled() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        service.disable(id);
        assertEquals("disabled", service.getById(id).getStatus());
    }

    @Test
    void 禁用草稿接口_也可以禁用() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.disable(id);
        assertEquals("disabled", service.getById(id).getStatus());
    }

    @Test
    void 重复发布_幂等不报错() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        assertDoesNotThrow(() -> service.publish(id));
        assertEquals("published", service.getById(id).getStatus());
    }

    @Test
    void 禁用后重新发布_状态恢复published() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        service.disable(id);
        service.publish(id);
        assertEquals("published", service.getById(id).getStatus());
    }

    @Test
    void 删除已发布接口_抛出BusinessException() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(id));
        assertTrue(ex.getMessage().contains("禁用"), "异常信息应提示先禁用");
    }

    @Test
    void 删除草稿接口_成功() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.delete(id);
        assertThrows(BusinessException.class, () -> service.getById(id));
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
cd backend && mvn test -Dtest=M27PublishTest -q 2>&1 | tail -20
```

预期：`BUILD FAILURE`，报 `publish`/`disable` 方法不存在。

- [ ] **Step 3：在 InterfaceConfigService 中实现三个方法，并修改 delete**

在 `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java` 的 `delete` 方法上方，添加以下方法（紧接已有的 `getById` 方法之后）：

```java
// ─── M2-7 状态管理 ────────────────────────────────────────────────────────────

/** 发布接口：status → published，path 写入 /api/exec/{id} */
public void publish(Long id) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    InterfaceConfig update = new InterfaceConfig();
    update.setId(id);
    update.setStatus("published");
    update.setPath("/api/exec/" + id);
    interfaceConfigMapper.updateById(update);
}

/** 禁用接口：status → disabled（draft/published 均可） */
public void disable(Long id) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    InterfaceConfig update = new InterfaceConfig();
    update.setId(id);
    update.setStatus("disabled");
    interfaceConfigMapper.updateById(update);
}
```

将原来的 `delete` 方法替换为：

```java
/** 删除接口配置（已发布状态不允许删除，需先禁用） */
public void delete(Long id) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    if ("published".equals(config.getStatus())) {
        throw new BusinessException(400, "接口已发布，请先禁用后再删除");
    }
    interfaceConfigMapper.deleteById(id);
}
```

- [ ] **Step 4：在 InterfaceConfigService 中新增 executeQuery 方法**

在 `delete` 方法后面添加（M2-7 SELECT 执行区块）：

```java
// ─── M2-7 SELECT 执行（全量/分页）──────────────────────────────────────────────

/**
 * 执行已发布 SELECT 接口。
 * page/pageSize 均不为 null 时分页（page 从 1 开始），否则全量返回。
 */
public List<Map<String, Object>> executeQuery(Long id, Map<String, Object> params,
                                               Integer page, Integer pageSize) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    if (!"SELECT".equals(config.getType())) throw new BusinessException(400, "非 SELECT 类型接口");

    QueryConfigJson queryConfig;
    try {
        queryConfig = objectMapper.readValue(config.getConfigJson(), QueryConfigJson.class);
    } catch (Exception e) {
        throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
    }

    QueryBuilder.SqlResult sqlResult;
    try {
        if (page != null && pageSize != null) {
            sqlResult = QueryBuilder.buildPaginated(queryConfig, params, page, pageSize);
        } else {
            sqlResult = QueryBuilder.buildFull(queryConfig, params);
        }
    } catch (Exception e) {
        throw new BusinessException(400, "SQL 构建失败: " + e.getMessage());
    }

    log.info("[M2-7] 执行 SELECT SQL: {}", sqlResult.sql);

    DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
    if (conn == null) throw new BusinessException(404, "数据库连接不存在");
    return executeQuery(conn, sqlResult);
}
```

- [ ] **Step 5：运行测试，确认全部通过**

```bash
cd backend && mvn test -Dtest=M27PublishTest -q 2>&1 | tail -10
```

预期：`BUILD SUCCESS`，7 个测试通过。

- [ ] **Step 6：回归全量测试**

```bash
cd backend && mvn test -q 2>&1 | tail -15
```

预期：`BUILD SUCCESS`，所有已有测试通过。

- [ ] **Step 7：提交**

```bash
cd backend
git add src/main/java/com/powergateway/service/InterfaceConfigService.java \
        src/test/java/com/powergateway/M27PublishTest.java
git commit -m "feat(M2-7): Service 新增 publish/disable/executeQuery，delete 加状态校验"
```

---

## Task 3：InterfaceConfigController 新增 publish / disable 端点

**Files:**
- Modify: `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java`

- [ ] **Step 1：在 InterfaceConfigController 中追加两个端点**

在 `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java` 的 `delete` 端点后追加：

```java
@PostMapping("/{id}/publish")
@Operation(summary = "发布接口（status → published）")
public Result<Void> publish(@PathVariable Long id) {
    service.publish(id);
    return Result.success();
}

@PostMapping("/{id}/disable")
@Operation(summary = "禁用接口（status → disabled）")
public Result<Void> disable(@PathVariable Long id) {
    service.disable(id);
    return Result.success();
}
```

- [ ] **Step 2：运行全量测试，确认无回归**

```bash
cd backend && mvn test -q 2>&1 | tail -15
```

预期：`BUILD SUCCESS`。

- [ ] **Step 3：提交**

```bash
cd backend
git add src/main/java/com/powergateway/controller/InterfaceConfigController.java
git commit -m "feat(M2-7): Controller 新增 publish/disable 端点"
```

---

## Task 4：ExecRequest DTO + ExecController + Sa-Token 排除

**Files:**
- 新建: `backend/src/main/java/com/powergateway/model/dto/ExecRequest.java`
- 新建: `backend/src/main/java/com/powergateway/controller/ExecController.java`
- 修改: `backend/src/main/java/com/powergateway/config/SaTokenConfig.java`
- 测试: `backend/src/test/java/com/powergateway/M27ExecControllerTest.java`（新建）

- [ ] **Step 1：写失败测试**

新建 `backend/src/test/java/com/powergateway/M27ExecControllerTest.java`：

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-7 ExecController 统一执行入口")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M27ExecControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;
    private static final String H2_URL  = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        // 建 H2 测试表
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m27_product");
            stmt.execute("CREATE TABLE m27_product (" +
                         "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                         "  name VARCHAR(64) NOT NULL" +
                         ")");
            stmt.execute("INSERT INTO m27_product(name) VALUES('Apple'),('Banana'),('Cherry')");
        }

        // 登录取 token
        String loginBody = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";
        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        // 注册指向 H2 的 DB 连接
        DbConnection db = new DbConnection();
        db.setName("M27_H2");
        db.setDbType("MySQL");
        db.setUrl(H2_URL);
        db.setUsername("sa");
        db.setPassword(AesUtil.encrypt("", AES_KEY));
        db.setEnv("dev");
        dbConnectionMapper.insert(db);
        testDbId = db.getId();
    }

    /** 保存并发布一个 SELECT 接口，返回其 id */
    private Long saveAndPublishSelect() throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"m27_product\",\"alias\":\"p\"}]," +
            "\"fields\":[{\"table\":\"p\",\"column\":\"id\",\"alias\":\"id\"}," +
            "{\"table\":\"p\",\"column\":\"name\",\"alias\":\"name\"}]," +
            "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "M27_SELECT_" + System.currentTimeMillis());
        req.put("dbConnectionId", testDbId);
        req.put("type", "SELECT");
        req.put("configJson", configJson);

        MvcResult sr = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = ((Number) JsonPath.read(sr.getResponse().getContentAsString(), "$.data")).longValue();

        mockMvc.perform(post("/api/interface/" + id + "/publish")
                .header("satoken", token))
                .andExpect(jsonPath("$.code").value(200));
        return id;
    }

    @Test
    @Order(1)
    void 无token调用exec_成功返回数据() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    @Order(2)
    void 分页查询_返回指定行数() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{},\"page\":1,\"pageSize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @Order(3)
    void 禁用接口调用exec_返回403() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/interface/" + id + "/disable")
                .header("satoken", token));
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @Order(4)
    void 草稿接口调用exec_返回400() throws Exception {
        // 直接保存不发布
        String configJson = "{\"tables\":[{\"name\":\"m27_product\",\"alias\":\"p\"}]," +
            "\"fields\":[{\"table\":\"p\",\"column\":\"id\",\"alias\":\"id\"}]," +
            "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "M27_DRAFT_" + System.currentTimeMillis());
        req.put("dbConnectionId", testDbId);
        req.put("type", "SELECT");
        req.put("configJson", configJson);
        MvcResult sr = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        Long id = ((Number) JsonPath.read(sr.getResponse().getContentAsString(), "$.data")).longValue();

        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
cd backend && mvn test -Dtest=M27ExecControllerTest -q 2>&1 | tail -20
```

预期：`BUILD FAILURE`，报 `/api/exec/**` 端点不存在（404）或 `ExecController` 未定义。

- [ ] **Step 3：新建 ExecRequest DTO**

新建 `backend/src/main/java/com/powergateway/model/dto/ExecRequest.java`：

```java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class ExecRequest {
    private Map<String, Object> params = new HashMap<>();
    private Integer page;
    private Integer pageSize;
}
```

- [ ] **Step 4：新建 ExecController**

新建 `backend/src/main/java/com/powergateway/controller/ExecController.java`：

```java
package com.powergateway.controller;

import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ExecRequest;
import com.powergateway.service.InterfaceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对外执行入口（M2-7），无需 Sa-Token 登录。
 * 已在 SaTokenConfig 中排除 /api/exec/**。
 */
@RestController
@RequestMapping("/api/exec")
@Tag(name = "接口执行", description = "已发布接口的对外统一执行入口（无需登录）")
public class ExecController {

    @Autowired
    private InterfaceConfigService service;

    @PostMapping("/{interfaceId}")
    @Operation(summary = "执行已发布接口（SELECT/INSERT/UPDATE/DELETE）")
    public Result<?> execute(@PathVariable Long interfaceId,
                             @RequestBody(required = false) ExecRequest req) {
        if (req == null) req = new ExecRequest();
        Map<String, Object> params = req.getParams() != null ? req.getParams() : new HashMap<>();

        InterfaceConfig config = service.getById(interfaceId);

        if ("disabled".equals(config.getStatus())) {
            return Result.fail(403, "接口已禁用");
        }
        if ("draft".equals(config.getStatus())) {
            return Result.fail(400, "接口未发布");
        }

        switch (config.getType()) {
            case "SELECT":
                List<Map<String, Object>> rows =
                        service.executeQuery(interfaceId, params, req.getPage(), req.getPageSize());
                return Result.success(rows);
            case "INSERT":
                return Result.success(service.executeInsert(interfaceId, params));
            case "UPDATE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(interfaceId)
                        .setOpType("UPDATE")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return Result.success(service.executeUpdate(interfaceId, params));
            case "DELETE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(interfaceId)
                        .setOpType("DELETE")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return Result.success(service.executeDelete(interfaceId, params));
            default:
                throw new BusinessException(400, "不支持的接口类型: " + config.getType());
        }
    }
}
```

- [ ] **Step 5：在 SaTokenConfig 中排除 /api/exec/\*\***

修改 `backend/src/main/java/com/powergateway/config/SaTokenConfig.java`，将 `excludePathPatterns` 改为：

```java
.excludePathPatterns("/api/auth/login", "/api/auth/logout", "/api/health", "/api/exec/**");
```

- [ ] **Step 6：运行测试，确认全部通过**

```bash
cd backend && mvn test -Dtest=M27ExecControllerTest -q 2>&1 | tail -10
```

预期：`BUILD SUCCESS`，4 个测试通过。

- [ ] **Step 7：回归全量测试**

```bash
cd backend && mvn test -q 2>&1 | tail -15
```

预期：`BUILD SUCCESS`。

- [ ] **Step 8：提交**

```bash
cd backend
git add src/main/java/com/powergateway/model/dto/ExecRequest.java \
        src/main/java/com/powergateway/controller/ExecController.java \
        src/main/java/com/powergateway/config/SaTokenConfig.java \
        src/test/java/com/powergateway/M27ExecControllerTest.java
git commit -m "feat(M2-7): ExecController 统一执行入口 + Sa-Token 排除 /api/exec/**"
```

---

## Task 5：OpenApiDynamicCustomizer（Swagger 动态注册）

**Files:**
- 新建: `backend/src/main/java/com/powergateway/config/OpenApiDynamicCustomizer.java`

- [ ] **Step 1：新建 OpenApiDynamicCustomizer**

新建 `backend/src/main/java/com/powergateway/config/OpenApiDynamicCustomizer.java`：

```java
package com.powergateway.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 动态将已发布接口注册到 Swagger 文档（M2-7）。
 * SpringDoc 每次请求 /v3/api-docs 时都会调用 customise()，自动反映最新发布状态。
 */
@Component
public class OpenApiDynamicCustomizer implements OpenApiCustomizer {

    @Autowired
    private InterfaceConfigMapper interfaceConfigMapper;

    @Override
    public void customise(OpenAPI openApi) {
        LambdaQueryWrapper<InterfaceConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InterfaceConfig::getStatus, "published");
        List<InterfaceConfig> published = interfaceConfigMapper.selectList(wrapper);

        for (InterfaceConfig config : published) {
            String path = "/api/exec/" + config.getId();
            if (openApi.getPaths() != null && openApi.getPaths().containsKey(path)) {
                continue; // 已由 ExecController 自动注册，跳过
            }

            Schema<?> paramsSchema = new Schema<>().type("object");
            Schema<?> bodySchema = new Schema<>().type("object")
                    .addProperties("params", paramsSchema);
            if ("SELECT".equals(config.getType())) {
                bodySchema.addProperties("page", new Schema<>().type("integer").example(1));
                bodySchema.addProperties("pageSize", new Schema<>().type("integer").example(20));
            }

            Operation op = new Operation()
                    .summary("[" + config.getType() + "] " + config.getName())
                    .description("自动发布的接口，类型：" + config.getType())
                    .addTagsItem("接口执行")
                    .requestBody(new RequestBody()
                            .content(new Content().addMediaType("application/json",
                                    new MediaType().schema(bodySchema))))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("执行成功")));

            PathItem pathItem = new PathItem().post(op);
            openApi.path(path, pathItem);
        }
    }
}
```

- [ ] **Step 2：启动后验证 Swagger 文档（手动）**

```bash
cd backend && mvn spring-boot:run &
# 等待启动后
curl -s http://localhost:8080/v3/api-docs | python -m json.tool | grep "/api/exec"
```

发布一个接口（通过 Swagger UI 或 curl）后，再次调用 `/v3/api-docs` 确认该接口出现在文档中。

- [ ] **Step 3：回归全量测试**

```bash
cd backend && mvn test -q 2>&1 | tail -15
```

预期：`BUILD SUCCESS`。

- [ ] **Step 4：提交**

```bash
cd backend
git add src/main/java/com/powergateway/config/OpenApiDynamicCustomizer.java
git commit -m "feat(M2-7): OpenApiDynamicCustomizer 动态注册已发布接口到 Swagger"
```

---

## Task 6：前端接口列表页（InterfaceList.vue + 路由 + API）

**Files:**
- 新建: `frontend/src/views/interface/InterfaceList.vue`
- 修改: `frontend/src/api/interface.js`
- 修改: `frontend/src/router/index.js`

- [ ] **Step 1：在 api/interface.js 追加 publish/disable 方法**

在 `frontend/src/api/interface.js` 文件末尾追加：

```js
/** 发布接口（M2-7） */
export function publishInterface(id) {
  return request.post(`/interface/${id}/publish`)
}

/** 禁用接口（M2-7） */
export function disableInterface(id) {
  return request.post(`/interface/${id}/disable`)
}
```

- [ ] **Step 2：新建 InterfaceList.vue**

新建 `frontend/src/views/interface/InterfaceList.vue`：

```vue
<template>
  <div class="interface-list">
    <div class="toolbar">
      <el-input
        v-model="searchName"
        placeholder="搜索接口名称"
        clearable
        style="width: 260px"
        @keyup.enter="loadList"
        @clear="loadList"
      />
      <el-button type="primary" @click="loadList">查询</el-button>
    </div>

    <el-table :data="list" stripe border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="name" label="接口名称" min-width="160" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="访问路径" min-width="200">
        <template #default="{ row }">
          <span v-if="row.status === 'published'" class="path-text">{{ row.path }}</span>
          <span v-else style="color:#999">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'published'"
            type="success"
            size="small"
            @click="handlePublish(row)"
          >发布</el-button>
          <el-button
            v-if="row.status === 'published'"
            type="warning"
            size="small"
            @click="handleDisable(row)"
          >禁用</el-button>
          <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-popconfirm
            :title="row.status === 'published' ? '已发布接口请先禁用再删除' : '确认删除该接口？'"
            :disabled="row.status === 'published'"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button type="danger" size="small" :disabled="row.status === 'published'">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; text-align: right"
      @current-change="loadList"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  listInterfaces,
  deleteInterface,
  publishInterface,
  disableInterface
} from '@/api/interface'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const searchName = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const TYPE_ROUTE = {
  SELECT: '/interface/dev',
  INSERT: '/interface/insert',
  UPDATE: '/interface/update',
  DELETE: '/interface/delete'
}

function typeTagType(type) {
  return { SELECT: '', INSERT: 'success', UPDATE: 'warning', DELETE: 'danger' }[type] ?? 'info'
}

function statusTagType(status) {
  return { published: 'success', draft: 'info', disabled: 'danger' }[status] ?? 'info'
}

function statusLabel(status) {
  return { published: '已发布', draft: '草稿', disabled: '已禁用' }[status] ?? status
}

async function loadList() {
  loading.value = true
  try {
    const res = await listInterfaces(searchName.value || undefined, page.value, pageSize.value)
    list.value = res ?? []
    total.value = list.value.length // 后端暂无分页 total，先用长度
  } finally {
    loading.value = false
  }
}

async function handlePublish(row) {
  await publishInterface(row.id)
  ElMessage.success('发布成功')
  await loadList()
}

async function handleDisable(row) {
  await disableInterface(row.id)
  ElMessage.success('已禁用')
  await loadList()
}

function handleEdit(row) {
  const path = TYPE_ROUTE[row.type]
  if (path) {
    router.push({ path, query: { id: row.id } })
  } else {
    ElMessage.warning('未知接口类型')
  }
}

async function handleDelete(row) {
  await deleteInterface(row.id)
  ElMessage.success('删除成功')
  await loadList()
}

onMounted(loadList)
</script>

<style scoped>
.interface-list { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
.path-text { font-family: monospace; font-size: 12px; color: #409eff; }
</style>
```

- [ ] **Step 3：在 router/index.js 新增 interface/list 路由**

在 `frontend/src/router/index.js` 中，找到 `interface/delete` 路由之后，追加一条新路由：

```js
{
  path: 'interface/list',
  name: 'InterfaceList',
  component: () => import('@/views/interface/InterfaceList.vue'),
  meta: { title: '接口管理' }
},
```

- [ ] **Step 4：在侧边菜单中添加"接口管理"菜单项**

修改 `frontend/src/components/layout/SideMenu.vue`，在第 50 行 `/interface/delete` 菜单项**下方**、`/interface/shard` 菜单项**上方**追加一行：

```html
        <el-menu-item index="/interface/list">接口管理</el-menu-item>
```

修改后该区块如下（第 46～54 行）：

```html
        <el-menu-item index="/interface/db">数据库连接管理</el-menu-item>
        <el-menu-item index="/interface/table">表结构管理</el-menu-item>
        <el-menu-item index="/interface/dev">查询接口配置</el-menu-item>
        <el-menu-item index="/interface/insert">插入接口配置</el-menu-item>
        <el-menu-item index="/interface/update">修改接口配置</el-menu-item>
        <el-menu-item index="/interface/delete">删除接口配置</el-menu-item>
        <el-menu-item index="/interface/list">接口管理</el-menu-item>
        <el-menu-item index="/interface/shard">分库分表配置</el-menu-item>
        <el-menu-item index="/interface/formula">字段公式管理</el-menu-item>
        <el-menu-item index="/interface/cache">缓存查询管理</el-menu-item>
```

- [ ] **Step 5：启动前端验证**

```bash
cd frontend && npm run dev
```

浏览器访问 `http://localhost:5173/interface/list`，确认：
1. 接口列表正常渲染（即使列表为空也不报错）
2. 状态 tag 颜色正确
3. 已发布接口的"删除"按钮为禁用状态
4. 点击"编辑"按钮跳转到对应配置页

- [ ] **Step 6：提交**

```bash
cd frontend
git add src/views/interface/InterfaceList.vue \
        src/api/interface.js \
        src/router/index.js \
        src/components/layout/SideMenu.vue
git commit -m "feat(M2-7): 前端接口列表页（发布/禁用/编辑/删除）"
```

---

## Task 7：推送远程并运行全量验收

- [ ] **Step 1：后端全量测试**

```bash
cd backend && mvn test -q 2>&1 | tail -20
```

预期：`BUILD SUCCESS`，所有测试（含 M27 新增用例）全部通过。

- [ ] **Step 2：推送**

```bash
git push origin master
```

- [ ] **Step 3：验收清单**

- [ ] `POST /api/interface/{id}/publish` 成功后 status=published，path=/api/exec/{id}
- [ ] `POST /api/exec/{id}` 无需 token 可访问
- [ ] SELECT 接口传 page/pageSize 返回分页结果，不传返回全量
- [ ] disabled 接口调用返回 code=403
- [ ] draft 接口调用返回 code=400
- [ ] 已发布接口 `DELETE /api/interface/{id}` 返回 400
- [ ] Swagger UI 能看到已发布接口的文档条目
- [ ] 前端接口列表状态 tag 正确，发布/禁用按钮随状态切换
