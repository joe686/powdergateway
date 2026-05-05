# M2-6 删除接口配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 可视化配置 DELETE 接口，支持多表（最多3张）、删除前预查记录数写入审计日志、批量删除保护开关。

**Architecture:** 后端新增 `DeleteConfigJson`（config_json 结构）和 `DeleteBuilder`（SQL 构建），在 `InterfaceConfigService` 扩展 `deletePreview`（预查前10条）和 `executeDelete`（COUNT 预检 → 批量保护 → 多表事务 DELETE → 审计日志），`InterfaceConfigController` 增加 `/delete-preview` 端点并在 `execute` 中增加 DELETE 分支；前端增加 `DeleteConfig.vue` 3步向导并注册路由。

**Tech Stack:** Spring Boot 2.7 / MyBatis-Plus / JDBC 手动事务 / H2（测试）/ MockMvc / Vue3 / Element Plus / ConditionBuilder.vue（复用）

---

## 文件清单

| 操作 | 路径 |
|------|------|
| 新增 | `backend/src/main/java/com/powergateway/model/dto/DeleteConfigJson.java` |
| 新增 | `backend/src/main/java/com/powergateway/utils/DeleteBuilder.java` |
| 新增 | `backend/src/test/java/com/powergateway/M26DeleteBuilderTest.java` |
| 新增 | `backend/src/test/java/com/powergateway/M26DeleteConfigTest.java` |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java` |
| 修改 | `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java` |
| 修改 | `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java` |
| 新增 | `frontend/src/views/interface/DeleteConfig.vue` |
| 修改 | `frontend/src/api/interface.js` |
| 修改 | `frontend/src/router/index.js` |
| 修改 | `frontend/src/components/layout/SideMenu.vue` |

---

## Task 1: DeleteConfigJson + DeleteBuilder（纯单元测试 TDD）

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/DeleteConfigJson.java`
- Create: `backend/src/main/java/com/powergateway/utils/DeleteBuilder.java`
- Create: `backend/src/test/java/com/powergateway/M26DeleteBuilderTest.java`

- [ ] **Step 1: 创建 DeleteConfigJson.java**

```java
// backend/src/main/java/com/powergateway/model/dto/DeleteConfigJson.java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class DeleteConfigJson {

    private List<TableDeleteConfig> tables;

    @Data
    public static class TableDeleteConfig {
        private String tableName;
        private List<ConditionItem> conditions;
    }

    @Data
    public static class ConditionItem {
        private String field;
        /** EQ / NE / GT / LT / LIKE */
        private String op;
        private String paramKey;
    }
}
```

- [ ] **Step 2: 写 M26DeleteBuilderTest.java（Red — DeleteBuilder 尚不存在，编译失败）**

```java
// backend/src/test/java/com/powergateway/M26DeleteBuilderTest.java
package com.powergateway;

import com.powergateway.model.dto.DeleteConfigJson.ConditionItem;
import com.powergateway.utils.DeleteBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class M26DeleteBuilderTest {

    private ConditionItem cond(String field, String op, String paramKey) {
        ConditionItem c = new ConditionItem();
        c.setField(field);
        c.setOp(op);
        c.setParamKey(paramKey);
        return c;
    }

    @Test
    void 单条件EQ_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", 42L);

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "orders",
                Collections.singletonList(cond("id", "EQ", "orderId")),
                params);

        assertEquals("DELETE FROM orders WHERE id = ?", r.sql);
        assertEquals(Collections.singletonList(42L), r.params);
    }

    @Test
    void 多条件AND连接_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("status", "DELETED");
        params.put("userId", 10L);

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "orders",
                Arrays.asList(
                        cond("status", "EQ", "status"),
                        cond("user_id", "EQ", "userId")),
                params);

        assertEquals("DELETE FROM orders WHERE status = ? AND user_id = ?", r.sql);
        assertEquals(Arrays.asList("DELETED", 10L), r.params);
    }

    @Test
    void LIKE操作符_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("nameKey", "%test%");

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "items",
                Collections.singletonList(cond("name", "LIKE", "nameKey")),
                params);

        assertEquals("DELETE FROM items WHERE name LIKE ?", r.sql);
    }

    @Test
    void NE操作符_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("typeKey", "VIP");

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "items",
                Collections.singletonList(cond("type", "NE", "typeKey")),
                params);

        assertEquals("DELETE FROM items WHERE type <> ?", r.sql);
    }

    @Test
    void GT操作符_生成正确SQL() {
        Map<String, Object> params = new HashMap<>();
        params.put("ageKey", 18);

        DeleteBuilder.SqlResult r = DeleteBuilder.build(
                "users",
                Collections.singletonList(cond("age", "GT", "ageKey")),
                params);

        assertEquals("DELETE FROM users WHERE age > ?", r.sql);
    }

    @Test
    void 条件为空列表_抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DeleteBuilder.build("orders", Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void 条件为null_抛IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DeleteBuilder.build("orders", null, Collections.emptyMap()));
    }
}
```

- [ ] **Step 3: 运行测试，确认编译失败（DeleteBuilder 不存在）**

```bash
cd backend && mvn test -Dtest=M26DeleteBuilderTest -q 2>&1 | head -20
```

预期：编译错误 `cannot find symbol: class DeleteBuilder`

- [ ] **Step 4: 创建 DeleteBuilder.java（Green）**

```java
// backend/src/main/java/com/powergateway/utils/DeleteBuilder.java
package com.powergateway.utils;

import com.powergateway.model.dto.DeleteConfigJson.ConditionItem;

import java.util.*;

public class DeleteBuilder {

    public static SqlResult build(String tableName,
                                   List<ConditionItem> conditions,
                                   Map<String, Object> params) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件不能为空");
        }

        StringBuilder sql = new StringBuilder("DELETE FROM ").append(tableName).append(" WHERE ");
        List<Object> bindParams = new ArrayList<>();

        for (int i = 0; i < conditions.size(); i++) {
            ConditionItem cond = conditions.get(i);
            if (i > 0) sql.append(" AND ");
            sql.append(cond.getField()).append(opToSql(cond.getOp()));
            bindParams.add(params.get(cond.getParamKey()));
        }

        return new SqlResult(sql.toString(), bindParams);
    }

    public static String opToSql(String op) {
        if (op == null) return " = ?";
        switch (op) {
            case "NE":   return " <> ?";
            case "GT":   return " > ?";
            case "LT":   return " < ?";
            case "LIKE": return " LIKE ?";
            default:     return " = ?";
        }
    }

    public static class SqlResult {
        public final String sql;
        public final List<Object> params;

        public SqlResult(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
```

- [ ] **Step 5: 运行测试，确认全绿**

```bash
cd backend && mvn test -Dtest=M26DeleteBuilderTest
```

预期：`Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/powergateway/model/dto/DeleteConfigJson.java \
        backend/src/main/java/com/powergateway/utils/DeleteBuilder.java \
        backend/src/test/java/com/powergateway/M26DeleteBuilderTest.java
git commit -m "feat(m2-6): add DeleteConfigJson and DeleteBuilder with unit tests"
```

---

## Task 2: deletePreview + Controller delete-preview 端点（TDD）

**Files:**
- Modify: `backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java`
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`
- Modify: `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java`
- Create: `backend/src/test/java/com/powergateway/M26DeleteConfigTest.java`（仅 preview 部分）

- [ ] **Step 1: 扩展 InterfaceSaveRequest — 增加 allowBatchDelete 字段**

在 `InterfaceSaveRequest.java` 中 `configJson` 字段之后添加：

```java
/** 是否允许批量删除：0=否，1=是；仅 DELETE 类型接口使用 */
private Integer allowBatchDelete;
```

- [ ] **Step 2: 写 M26DeleteConfigTest.java preview 部分（Red）**

```java
// backend/src/test/java/com/powergateway/M26DeleteConfigTest.java
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-6 删除接口配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M26DeleteConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL  = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m26_order_item");
            stmt.execute("DROP TABLE IF EXISTS m26_order");
            stmt.execute(
                "CREATE TABLE m26_order (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT NOT NULL," +
                "  status VARCHAR(32) NOT NULL" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE m26_order_item (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  order_id BIGINT NOT NULL," +
                "  product_id BIGINT NOT NULL" +
                ")"
            );
            // user_id=10：2条（用于批量保护测试）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (1, 10, 'PENDING')");
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (2, 10, 'PENDING')");
            // user_id=20：1条（用于单条删除）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (3, 20, 'DONE')");
            // user_id=30：1条 + order_item（用于多表删除）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (4, 30, 'DONE')");
            // user_id=40：1条（用于事务回滚测试）
            stmt.execute("INSERT INTO m26_order (id, user_id, status) VALUES (5, 40, 'PENDING')");
            // order_items
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (1, 1, 100)");
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (2, 1, 101)");
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (3, 4, 200)");
            stmt.execute("INSERT INTO m26_order_item (id, order_id, product_id) VALUES (4, 4, 201)");
        }

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.token");

        DbConnection dbConn = new DbConnection();
        dbConn.setName("m26-test-db");
        dbConn.setDbType("MySQL");
        dbConn.setUrl(H2_URL);
        dbConn.setUsername("sa");
        dbConn.setPassword(AesUtil.encrypt("", AES_KEY));
        dbConn.setEnv("dev");
        dbConnectionMapper.insert(dbConn);
        testDbId = dbConn.getId();
    }

    /** 保存一个 DELETE 接口配置，返回接口 id */
    private Long saveDeleteInterface(String name, String configJson, int allowBatchDelete) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"DELETE\",\"allowBatchDelete\":" + allowBatchDelete
                + ",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        MvcResult result = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).longValue();
    }

    /** 直接查 H2 记录数 */
    private int countInH2(String table, String whereClause) throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + whereClause);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ─── Preview Tests ────────────────────────────────────────────────────────

    @Test @Order(1)
    void 预览待删数据_单表_返回匹配行() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
                "\"conditions\":[{\"field\":\"user_id\",\"op\":\"EQ\",\"paramKey\":\"userId\"}]}]}";
        Long id = saveDeleteInterface("prev-single", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("userId", 10); }});

        mockMvc.perform(post("/api/interface/" + id + "/delete-preview")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.m26_order").isArray())
                .andExpect(jsonPath("$.data.m26_order", hasSize(2)));
    }

    @Test @Order(2)
    void 预览待删数据_多表_分表返回结果() throws Exception {
        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m26_order\","
                + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]},"
                + "{\"tableName\":\"m26_order_item\","
                + "\"conditions\":[{\"field\":\"order_id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
                + "]}";
        Long id = saveDeleteInterface("prev-multi", configJson, 0);

        Map<String, Object> body = new HashMap<>();
        body.put("params", new HashMap<String, Object>() {{ put("orderId", 4); }});

        mockMvc.perform(post("/api/interface/" + id + "/delete-preview")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.m26_order", hasSize(1)))
                .andExpect(jsonPath("$.data.m26_order_item", hasSize(2)));
    }
}
```

- [ ] **Step 3: 运行测试，确认失败（端点不存在，404）**

```bash
cd backend && mvn test -Dtest=M26DeleteConfigTest#预览待删数据_单表_返回匹配行
```

预期：FAIL — `Status 404`（`/delete-preview` 端点不存在）

- [ ] **Step 4: 实现 InterfaceConfigService.deletePreview 方法**

在 `InterfaceConfigService.java` 中，`executeUpdate` 方法之后添加以下两个方法（`deletePreview` 和私有工具方法 `buildDeleteWhere`）：

```java
// ─── M2-6 DELETE 预览 ──────────────────────────────────────────────────────

public Map<String, List<Map<String, Object>>> deletePreview(Long id, Map<String, Object> params) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    if (!"DELETE".equals(config.getType())) throw new BusinessException(400, "非 DELETE 类型接口");

    DeleteConfigJson deleteConfig = parseDeleteConfig(config.getConfigJson());
    DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
    if (conn == null) throw new BusinessException(404, "数据库连接不存在");

    String password = aesUtil.decrypt(conn.getPassword());
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

    try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
        for (DeleteConfigJson.TableDeleteConfig tableConfig : deleteConfig.getTables()) {
            List<Object> bindParams = new ArrayList<>();
            String where = buildDeleteWhere(tableConfig.getConditions(), params, bindParams);
            String sql = "SELECT * FROM " + tableConfig.getTableName() + where + " LIMIT 10";

            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = jdbc.prepareStatement(sql)) {
                for (int i = 0; i < bindParams.size(); i++) ps.setObject(i + 1, bindParams.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                        rows.add(row);
                    }
                }
            }
            result.put(tableConfig.getTableName(), rows);
        }
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        throw new BusinessException(500, "预览查询失败: " + e.getMessage());
    }
    return result;
}

private DeleteConfigJson parseDeleteConfig(String configJson) {
    try {
        return objectMapper.readValue(configJson, DeleteConfigJson.class);
    } catch (Exception e) {
        throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
    }
}

private String buildDeleteWhere(List<DeleteConfigJson.ConditionItem> conditions,
                                 Map<String, Object> params,
                                 List<Object> bindParams) {
    if (conditions == null || conditions.isEmpty()) {
        throw new BusinessException(400, "删除条件不能为空");
    }
    StringBuilder where = new StringBuilder(" WHERE ");
    for (int i = 0; i < conditions.size(); i++) {
        DeleteConfigJson.ConditionItem cond = conditions.get(i);
        if (i > 0) where.append(" AND ");
        where.append(cond.getField()).append(DeleteBuilder.opToSql(cond.getOp()));
        bindParams.add(params.get(cond.getParamKey()));
    }
    return where.toString();
}
```

还需要在文件顶部 import 中补充：
```java
import com.powergateway.model.dto.DeleteConfigJson;
import com.powergateway.utils.DeleteBuilder;
```

- [ ] **Step 5: 实现 Controller delete-preview 端点**

在 `InterfaceConfigController.java` 中的 `preview` 方法之后添加：

```java
@PostMapping("/{id}/delete-preview")
@Operation(summary = "预览待删数据（执行 SELECT 前10条，M2-6）")
public Result<Map<String, List<Map<String, Object>>>> deletePreview(
        @PathVariable Long id,
        @RequestBody InterfacePreviewRequest req) {
    return Result.success(service.deletePreview(id, req.getParams()));
}
```

在文件顶部 import 中补充（如未引入）：
```java
import java.util.List;
import java.util.Map;
```

- [ ] **Step 6: 运行 preview 测试，确认全绿**

```bash
cd backend && mvn test -Dtest=M26DeleteConfigTest#预览待删数据_单表_返回匹配行+M26DeleteConfigTest#预览待删数据_多表_分表返回结果
```

预期：`Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 7: 更新 InterfaceConfigService.save() 以保存 allowBatchDelete**

在 `save()` 方法中，`entity.setStatus("draft");` 这行之后添加：

```java
if (req.getId() == null) {
    entity.setAllowBatchDelete(req.getAllowBatchDelete() != null ? req.getAllowBatchDelete() : 0);
} else if (req.getAllowBatchDelete() != null) {
    entity.setAllowBatchDelete(req.getAllowBatchDelete());
}
```

- [ ] **Step 8: 运行全量测试，确认无退化**

```bash
cd backend && mvn test
```

预期：所有测试绿，`Tests run: N, Failures: 0`

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java \
        backend/src/main/java/com/powergateway/service/InterfaceConfigService.java \
        backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java \
        backend/src/test/java/com/powergateway/M26DeleteConfigTest.java
git commit -m "feat(m2-6): add deletePreview endpoint and service method"
```

---

## Task 3: executeDelete + 批量保护 + Controller execute DELETE 分支（TDD）

**Files:**
- Modify: `backend/src/test/java/com/powergateway/M26DeleteConfigTest.java`（追加 execute 测试）
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`
- Modify: `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java`

- [ ] **Step 1: 在 M26DeleteConfigTest.java 中追加 execute 测试（Red）**

在 `M26DeleteConfigTest` 类最后（`}` 前）追加以下方法：

```java
// ─── Execute Tests ────────────────────────────────────────────────────────

@Test @Order(3)
void 批量保护_多条记录_拒绝执行() throws Exception {
    // user_id=10 有2条记录，allowBatchDelete=0 → 应被拒绝
    String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
            "\"conditions\":[{\"field\":\"user_id\",\"op\":\"EQ\",\"paramKey\":\"userId\"}]}]}";
    Long id = saveDeleteInterface("exec-batch-protect", configJson, 0);

    Map<String, Object> body = new HashMap<>();
    body.put("params", new HashMap<String, Object>() {{ put("userId", 10); }});

    mockMvc.perform(post("/api/interface/" + id + "/execute")
            .header("satoken", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(jsonPath("$.code").value(not(200)));

    // 数据未被删除
    assertEquals(2, countInH2("m26_order", "user_id = 10"));
}

@Test @Order(4)
void 开启批量删除_多条成功_返回影响行数() throws Exception {
    // user_id=10 有2条，allowBatchDelete=1 → 应成功
    String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
            "\"conditions\":[{\"field\":\"user_id\",\"op\":\"EQ\",\"paramKey\":\"userId\"}]}]}";
    Long id = saveDeleteInterface("exec-batch-ok", configJson, 1);

    Map<String, Object> body = new HashMap<>();
    body.put("params", new HashMap<String, Object>() {{ put("userId", 10); }});

    MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
            .header("satoken", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(jsonPath("$.code").value(200))
            .andReturn();

    int affected = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
    assertEquals(2, affected);
    assertEquals(0, countInH2("m26_order", "user_id = 10"));
}

@Test @Order(5)
void 单条记录DELETE_成功_数据消失() throws Exception {
    // id=3 (user_id=20)
    String configJson = "{\"tables\":[{\"tableName\":\"m26_order\"," +
            "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}]}";
    Long id = saveDeleteInterface("exec-single", configJson, 0);

    Map<String, Object> body = new HashMap<>();
    body.put("params", new HashMap<String, Object>() {{ put("orderId", 3); }});

    MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
            .header("satoken", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(jsonPath("$.code").value(200))
            .andReturn();

    int affected = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
    assertEquals(1, affected);
    assertEquals(0, countInH2("m26_order", "id = 3"));
}

@Test @Order(6)
void 多表DELETE_两表均成功() throws Exception {
    // m26_order id=4, m26_order_item order_id=4（各2条）
    String configJson = "{\"tables\":["
            + "{\"tableName\":\"m26_order\","
            + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]},"
            + "{\"tableName\":\"m26_order_item\","
            + "\"conditions\":[{\"field\":\"order_id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
            + "]}";
    Long id = saveDeleteInterface("exec-multi", configJson, 0);

    Map<String, Object> body = new HashMap<>();
    body.put("params", new HashMap<String, Object>() {{ put("orderId", 4); }});

    MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
            .header("satoken", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(jsonPath("$.code").value(200))
            .andReturn();

    int affected = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data")).intValue();
    assertEquals(3, affected); // 1(m26_order) + 2(m26_order_item)
    assertEquals(0, countInH2("m26_order", "id = 4"));
    assertEquals(0, countInH2("m26_order_item", "order_id = 4"));
}

@Test @Order(7)
void 多表DELETE_第二表列不存在_事务回滚() throws Exception {
    // id=5 在 m26_order 中存在；m26_order_item 用不存在的列 → 触发 SQL 错误
    assertEquals(1, countInH2("m26_order", "id = 5"));

    String configJson = "{\"tables\":["
            + "{\"tableName\":\"m26_order\","
            + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]},"
            + "{\"tableName\":\"m26_order_item\","
            + "\"conditions\":[{\"field\":\"nonexistent_col\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
            + "]}";
    Long id = saveDeleteInterface("exec-rollback", configJson, 1);

    Map<String, Object> body = new HashMap<>();
    body.put("params", new HashMap<String, Object>() {{ put("orderId", 5); }});

    mockMvc.perform(post("/api/interface/" + id + "/execute")
            .header("satoken", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(jsonPath("$.code").value(not(200)));

    // m26_order id=5 应因回滚而保留
    assertEquals(1, countInH2("m26_order", "id = 5"));
}

@Test @Order(8)
void 审计日志含各表COUNT信息() throws Exception {
    // 此时 m26_order id=5 仍存在，m26_order_item 无 order_id=5 的数据
    String configJson = "{\"tables\":["
            + "{\"tableName\":\"m26_order\","
            + "\"conditions\":[{\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"orderId\"}]}"
            + "]}";
    Long id = saveDeleteInterface("exec-audit", configJson, 0);

    Map<String, Object> body = new HashMap<>();
    body.put("params", new HashMap<String, Object>() {{ put("orderId", 5); }});

    mockMvc.perform(post("/api/interface/" + id + "/execute")
            .header("satoken", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
            .andExpect(jsonPath("$.code").value(200));

    Thread.sleep(500); // 等待异步审计日志写入

    try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
         PreparedStatement ps = conn.prepareStatement(
                 "SELECT before_snapshot FROM sql_audit_log WHERE interface_id = ? AND op_type = 'DELETE'")) {
        ps.setLong(1, id);
        try (ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "应有 DELETE 审计记录");
            String snapshot = rs.getString("before_snapshot");
            assertNotNull(snapshot);
            assertTrue(snapshot.contains("m26_order"), "before_snapshot 应含表名");
        }
    }
}
```

- [ ] **Step 2: 运行 execute 测试，确认失败（execute 不处理 DELETE）**

```bash
cd backend && mvn test -Dtest=M26DeleteConfigTest#单条记录DELETE_成功_数据消失
```

预期：FAIL — `"不支持的接口类型: DELETE"`（code 非200）

- [ ] **Step 3: 实现 InterfaceConfigService.executeDelete 和私有辅助方法**

在 `InterfaceConfigService.java` 中，`deletePreview` 方法之后添加：

```java
// ─── M2-6 DELETE 执行 ──────────────────────────────────────────────────────

@AuditLog
public int executeDelete(Long id, Map<String, Object> params) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    if (!"DELETE".equals(config.getType())) throw new BusinessException(400, "非 DELETE 类型接口");

    DeleteConfigJson deleteConfig = parseDeleteConfig(config.getConfigJson());
    List<DeleteConfigJson.TableDeleteConfig> tables = deleteConfig.getTables();
    if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置删除表");
    if (tables.size() > 3) throw new BusinessException(400, "最多支持3张表");

    DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
    if (conn == null) throw new BusinessException(404, "数据库连接不存在");

    String password = aesUtil.decrypt(conn.getPassword());

    try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
        // Step 1: COUNT 预检，汇总各表待删记录数
        Map<String, Integer> countMap = new LinkedHashMap<>();
        int totalCount = 0;
        for (DeleteConfigJson.TableDeleteConfig tableConfig : tables) {
            int cnt = countDeleteRows(jdbc, tableConfig, params);
            countMap.put(tableConfig.getTableName(), cnt);
            totalCount += cnt;
        }

        // Step 2: 批量删除保护
        int allowBatch = config.getAllowBatchDelete() != null ? config.getAllowBatchDelete() : 0;
        if (totalCount > 1 && allowBatch == 0) {
            StringBuilder msg = new StringBuilder("批量删除保护：待删记录共 ")
                    .append(totalCount).append(" 条，各表：");
            countMap.forEach((table, cnt) -> msg.append(table).append("=").append(cnt).append(" "));
            throw new BusinessException(400, msg.toString().trim() + "，请先开启允许批量删除");
        }

        // Step 3: 构建 DELETE SQL
        List<DeleteBuilder.SqlResult> sqlResults = new ArrayList<>();
        for (DeleteConfigJson.TableDeleteConfig tableConfig : tables) {
            sqlResults.add(DeleteBuilder.build(tableConfig.getTableName(),
                    tableConfig.getConditions(), params));
        }

        // Step 4: 写审计上下文
        AuditContext auditCtx = AuditContextHolder.get();
        if (auditCtx != null) {
            try {
                auditCtx.setBeforeSnapshot(objectMapper.writeValueAsString(countMap));
            } catch (Exception ignored) {}
            auditCtx.setTargetTable(tables.stream()
                    .map(DeleteConfigJson.TableDeleteConfig::getTableName)
                    .collect(Collectors.joining(",")));
            StringBuilder sqlText = new StringBuilder();
            for (DeleteBuilder.SqlResult r : sqlResults) {
                if (sqlText.length() > 0) sqlText.append("; ");
                sqlText.append(r.sql);
            }
            auditCtx.setSqlText(sqlText.toString());
        }

        // Step 5: 事务执行 DELETE
        jdbc.setAutoCommit(false);
        int totalAffected = 0;
        try {
            for (DeleteBuilder.SqlResult sql : sqlResults) {
                log.info("[M2-6] SQL: {}", sql.sql);
                try (PreparedStatement ps = jdbc.prepareStatement(sql.sql)) {
                    for (int i = 0; i < sql.params.size(); i++) ps.setObject(i + 1, sql.params.get(i));
                    totalAffected += ps.executeUpdate();
                }
            }
            jdbc.commit();
        } catch (Exception e) {
            jdbc.rollback();
            throw new BusinessException(500, "删除执行失败，已回滚: " + e.getMessage());
        }
        return totalAffected;

    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        throw new BusinessException(500, "数据库连接失败: " + e.getMessage());
    }
}

private int countDeleteRows(Connection jdbc,
                             DeleteConfigJson.TableDeleteConfig tableConfig,
                             Map<String, Object> params) {
    List<Object> bindParams = new ArrayList<>();
    String where = buildDeleteWhere(tableConfig.getConditions(), params, bindParams);
    String sql = "SELECT COUNT(*) FROM " + tableConfig.getTableName() + where;
    try (PreparedStatement ps = jdbc.prepareStatement(sql)) {
        for (int i = 0; i < bindParams.size(); i++) ps.setObject(i + 1, bindParams.get(i));
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    } catch (Exception e) {
        throw new BusinessException(500, "COUNT 查询失败: " + e.getMessage());
    }
}
```

- [ ] **Step 4: 在 Controller.execute() 中增加 DELETE 分支**

在 `InterfaceConfigController.java` 的 `execute` 方法中，`} else {` 最后一行（抛 `不支持的接口类型`）之前添加：

```java
} else if ("DELETE".equals(type)) {
    AuditContextHolder.set(new AuditContext()
            .setInterfaceId(id)
            .setOpType("DELETE")
            .setTargetDb(config.getDbConnectionId() != null
                    ? config.getDbConnectionId().toString() : "unknown"));
    return Result.success(service.executeDelete(id, req.getParams()));
```

- [ ] **Step 5: 运行全部 M26 测试，确认全绿**

```bash
cd backend && mvn test -Dtest=M26DeleteConfigTest
```

预期：`Tests run: 8, Failures: 0, Errors: 0`（Order 1-8 全绿）

- [ ] **Step 6: 运行全量测试，确认无退化**

```bash
cd backend && mvn test
```

预期：所有测试绿，`Failures: 0`

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/powergateway/service/InterfaceConfigService.java \
        backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java \
        backend/src/test/java/com/powergateway/M26DeleteConfigTest.java
git commit -m "feat(m2-6): add executeDelete with batch protection and audit log"
```

---

## Task 4: 前端 DeleteConfig.vue + 路由 + 菜单

**Files:**
- Create: `frontend/src/views/interface/DeleteConfig.vue`
- Modify: `frontend/src/api/interface.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/layout/SideMenu.vue`

- [ ] **Step 1: 在 api/interface.js 中添加 deletePreview 函数**

在文件末尾追加：

```js
/** 预览待删数据（M2-6） */
export function deletePreview(id, params) {
  return request.post(`/interface/${id}/delete-preview`, { params })
}
```

- [ ] **Step 2: 在 router/index.js 中注册路由**

在 `InterfaceUpdate` 路由对象之后（`path: 'interface/shard'` 之前）添加：

```js
{
  path: 'interface/delete',
  name: 'InterfaceDelete',
  component: () => import('@/views/interface/DeleteConfig.vue'),
  meta: { title: '删除接口配置' }
},
```

- [ ] **Step 3: 在 SideMenu.vue 中添加菜单项**

在 `<el-menu-item index="/interface/update">修改接口配置</el-menu-item>` 之后添加：

```html
<el-menu-item index="/interface/delete">删除接口配置</el-menu-item>
```

- [ ] **Step 4: 创建 DeleteConfig.vue**

```vue
<!-- frontend/src/views/interface/DeleteConfig.vue -->
<template>
  <div class="delete-config-page">
    <div class="page-header">
      <h2>删除接口配置</h2>
      <el-button @click="router.push('/interface/dev')">返回列表</el-button>
    </div>

    <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="基本信息" />
      <el-step title="多表条件配置" />
      <el-step title="预览与保存" />
    </el-steps>

    <!-- Step 1：基本信息 -->
    <div v-show="step === 0">
      <el-card>
        <template #header>Step 1 · 基本信息</template>
        <el-form label-width="140px" style="max-width: 600px">
          <el-form-item label="接口名称" required>
            <el-input v-model="form.name" placeholder="请输入接口名称" />
          </el-form-item>
          <el-form-item label="数据库连接" required>
            <el-select
              v-model="form.dbConnectionId"
              placeholder="请选择数据库连接"
              style="width: 100%"
              @change="onDbChange"
            >
              <el-option v-for="db in dbList" :key="db.id" :label="db.name" :value="db.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="允许批量删除">
            <el-switch v-model="form.allowBatchDelete" :active-value="1" :inactive-value="0" />
            <el-text v-if="form.allowBatchDelete === 0" type="warning" style="margin-left: 12px; font-size: 12px">
              关闭后单次删除超过1条将被拒绝
            </el-text>
            <el-text v-else type="danger" style="margin-left: 12px; font-size: 12px">
              ⚠ 已开启批量删除，请谨慎操作
            </el-text>
          </el-form-item>
        </el-form>
        <div class="step-footer">
          <el-button
            type="primary"
            :disabled="!form.name || !form.dbConnectionId"
            @click="step = 1"
          >
            下一步
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Step 2：多表条件配置 -->
    <div v-show="step === 1">
      <el-card>
        <template #header>
          <span>Step 2 · 多表条件配置</span>
          <el-button
            style="float: right"
            size="small"
            type="primary"
            :disabled="tables.length >= 3"
            @click="addTable"
          >
            + 添加表（最多3张）
          </el-button>
        </template>

        <div v-for="(tbl, tIdx) in tables" :key="tIdx" class="table-block">
          <div class="table-block-header">
            <span class="table-block-title">表 {{ tIdx + 1 }}</span>
            <el-select
              v-model="tbl.tableName"
              placeholder="请选择目标表"
              filterable
              style="width: 260px; margin: 0 12px"
            >
              <el-option
                v-for="t in tableList"
                :key="t.tableName"
                :label="t.tableName + (t.comment ? ` (${t.comment})` : '')"
                :value="t.tableName"
              />
            </el-select>
            <el-button
              v-if="tables.length > 1"
              size="small"
              type="danger"
              plain
              @click="removeTable(tIdx)"
            >
              删除
            </el-button>
          </div>

          <div style="margin-top: 10px; padding-left: 8px">
            <ConditionBuilder
              v-model="tbl.conditions"
              :field-options="tableFieldOptions(tbl.tableName)"
            />
          </div>
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :disabled="!canProceedStep2" @click="step = 2">下一步</el-button>
      </div>
    </div>

    <!-- Step 3：预览与保存 -->
    <div v-show="step === 2">
      <el-card>
        <template #header>Step 3 · 预览与保存</template>

        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="接口名称">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="接口类型">DELETE（删除）</el-descriptions-item>
          <el-descriptions-item label="目标表数量">{{ tables.length }} 张</el-descriptions-item>
          <el-descriptions-item label="允许批量删除">
            <el-tag :type="form.allowBatchDelete ? 'danger' : 'success'">
              {{ form.allowBatchDelete ? '是（危险）' : '否（安全）' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse style="margin-bottom: 16px">
          <el-collapse-item title="查看 config_json">
            <pre style="background:#f5f5f5;padding:12px;border-radius:4px;font-size:12px;overflow:auto">{{ configJsonPreview }}</pre>
          </el-collapse-item>
        </el-collapse>

        <div style="margin-bottom: 16px">
          <el-button :loading="previewing" @click="doPreview">预览待删数据</el-button>
          <span style="font-size: 12px; color: #909399; margin-left: 8px">（需先保存接口才可预览）</span>
        </div>

        <div class="step-footer">
          <el-button @click="step = 1">上一步</el-button>
          <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
        </div>
      </el-card>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="待删数据预览（前10条）" width="80%">
      <el-empty v-if="Object.keys(previewData).length === 0" description="暂无数据" />
      <el-tabs v-else>
        <el-tab-pane
          v-for="(rows, tableName) in previewData"
          :key="tableName"
          :label="tableName + ' (' + rows.length + ' 条)'"
        >
          <el-table :data="rows" border size="small" max-height="400">
            <el-table-column
              v-for="col in tablePreviewColumns(rows)"
              :key="col"
              :label="col"
              :prop="col"
              min-width="120"
            />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ConditionBuilder from '@/components/ConditionBuilder.vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { saveInterface, deletePreview } from '@/api/interface'

const router = useRouter()
const step = ref(0)

const dbList      = ref([])
const tableList   = ref([])
const tableColumns = ref({})   // tableName → columns[]

const form = ref({ name: '', dbConnectionId: null, allowBatchDelete: 0 })
const tables = ref([{ tableName: '', conditions: [] }])

const saving    = ref(false)
const previewing = ref(false)
const previewVisible = ref(false)
const previewData    = ref({})
const savedId    = ref(null)

function addTable()       { if (tables.value.length < 3) tables.value.push({ tableName: '', conditions: [] }) }
function removeTable(idx) { tables.value.splice(idx, 1) }

function tableFieldOptions(tableName) {
  return (tableColumns.value[tableName] || []).map(col => ({
    label: col.name,
    value: col.name,
    isPrimary: col.isPrimary,
    isUnique: col.isUnique
  }))
}

function tablePreviewColumns(rows) {
  if (!rows || rows.length === 0) return []
  return Object.keys(rows[0])
}

const canProceedStep2 = computed(() =>
  tables.value.every(t => t.tableName && t.conditions.length > 0)
)

async function onDbChange(dbId) {
  tableList.value = []
  tableColumns.value = {}
  tables.value.forEach(t => { t.tableName = ''; t.conditions = [] })
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    tableList.value = list || []
    tableList.value.forEach(t => { tableColumns.value[t.tableName] = t.columns || [] })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

const configJsonPreview = computed(() => JSON.stringify(buildConfigJson(), null, 2))

function buildConfigJson() {
  return {
    tables: tables.value.map(t => ({
      tableName: t.tableName,
      conditions: t.conditions.map(c => ({
        field: c.field,
        op: c.op,
        paramKey: c.paramKey
      }))
    }))
  }
}

async function saveConfig() {
  saving.value = true
  try {
    const id = await saveInterface({
      name: form.value.name,
      dbConnectionId: form.value.dbConnectionId,
      type: 'DELETE',
      allowBatchDelete: form.value.allowBatchDelete,
      configJson: JSON.stringify(buildConfigJson())
    })
    savedId.value = id
    ElMessage.success('保存成功，可点击「预览待删数据」测试')
  } catch {
    // request.js 拦截器已处理
  } finally {
    saving.value = false
  }
}

async function doPreview() {
  if (!savedId.value) {
    ElMessage.warning('请先点击「保存」后再预览')
    return
  }
  previewing.value = true
  try {
    // 收集所有条件中的 paramKey，提示用户填写；此处用空 Map 展示结构
    const params = {}
    tables.value.forEach(t => {
      t.conditions.forEach(c => { if (c.paramKey) params[c.paramKey] = null })
    })
    const data = await deletePreview(savedId.value, params)
    previewData.value = data || {}
    previewVisible.value = true
  } catch {
    ElMessage.error('预览失败')
  } finally {
    previewing.value = false
  }
}

onMounted(async () => {
  try { dbList.value = (await listConnections()) || [] } catch { ElMessage.error('加载数据库连接失败') }
})
</script>

<style scoped>
.delete-config-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.table-block { padding: 12px; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 16px; }
.table-block-header { display: flex; align-items: center; margin-bottom: 6px; }
.table-block-title { font-weight: 600; color: #303133; }
.step-footer { margin-top: 20px; display: flex; gap: 12px; }
</style>
```

- [ ] **Step 5: 启动前端开发服务器，手动验证页面**

```bash
cd frontend && npm run dev
```

访问 `http://localhost:5173/interface/delete`，验证：
- 侧边菜单"可视化接口开发"下出现「删除接口配置」
- Step 1 可填写接口名、选择连接、切换批量删除开关
- 切换到 Step 2 可添加表并用 ConditionBuilder 配条件
- Step 3 可展开 config_json 预览
- 「保存」按钮调用后端返回 200

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/interface/DeleteConfig.vue \
        frontend/src/api/interface.js \
        frontend/src/router/index.js \
        frontend/src/components/layout/SideMenu.vue
git commit -m "feat(m2-6): add DeleteConfig frontend page with multi-table condition builder"
```

- [ ] **Step 7: 推送远端**

```bash
git push origin master
```

---

## 自检清单（规格覆盖）

| 规格要求 | 覆盖位置 |
|---------|---------|
| 多表支持（最多3张） | Task 3 Service + Task 4 前端"最多3张"限制 |
| 各表独立条件配置 | DeleteConfigJson.TableDeleteConfig + Task 4 ConditionBuilder |
| deletePreview 返回前10条 | Task 2 Service + Controller |
| 批量保护拦截 | Task 3 executeDelete + Order(3)测试 |
| 批量保护可关闭 | Task 3 Order(4)测试 + Task 4 allowBatchDelete switch |
| 删除前记录COUNT写审计日志 | Task 3 auditCtx.setBeforeSnapshot + Order(8)测试 |
| 多表事务回滚 | Task 3 Order(7)测试 |
| allowBatchDelete 保存 | Task 2 InterfaceSaveRequest + save()方法 |
| 前端3步向导 | Task 4 el-steps |
| 预览弹窗分表展示 | Task 4 el-dialog + el-tabs |
| 路由+菜单注册 | Task 4 router + SideMenu |
