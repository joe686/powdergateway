# M2-5 修改接口配置 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 可视化配置 UPDATE 接口，支持多表（最多3张），强制唯一条件校验，执行前快照写入审计日志。

**Architecture:** 后端新增 `UpdateConfigJson` DTO 和 `UpdateBuilder` 工具类，扩展 `InterfaceConfigService` 新增 `executeUpdate()`，Controller `execute` 端点按类型分发。`SqlAuditAspect` 通过 `AuditContextHolder` ThreadLocal 读取修改前快照（在 `executeUpdate` 内部通过业务库连接执行 SELECT 后写入 ctx）。前端在现有 `ConditionBuilder.vue` 扩展两个 prop 后，新建 `UpdateConfig.vue`。

**Tech Stack:** Spring Boot 2.7 / Java 8 / MyBatis-Plus / JDBC 手动事务 / Jackson / Vue 3 / Element Plus

---

## 文件地图

| 操作 | 文件 |
|------|------|
| Modify | `backend/src/main/java/com/powergateway/model/dto/ColumnMeta.java` |
| Modify | `backend/src/main/java/com/powergateway/service/TableMetaService.java` |
| Create | `backend/src/main/java/com/powergateway/model/dto/UpdateConfigJson.java` |
| Create | `backend/src/main/java/com/powergateway/utils/UpdateBuilder.java` |
| Modify | `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java` |
| Create | `backend/src/test/java/com/powergateway/M25UpdateConfigTest.java` |
| Modify | `frontend/src/components/ConditionBuilder.vue` |
| Create | `frontend/src/views/interface/UpdateConfig.vue` |
| Modify | `frontend/src/router/index.js` |
| Modify | `frontend/src/components/layout/SideMenu.vue` |

---

## Task 1：扩展 ColumnMeta + TableMetaService（支持唯一索引）

**Files:**
- Modify: `backend/src/main/java/com/powergateway/model/dto/ColumnMeta.java`
- Modify: `backend/src/main/java/com/powergateway/service/TableMetaService.java`

- [ ] **Step 1.1：在 ColumnMeta 新增 `isUnique` 字段**

将 `ColumnMeta.java` 全文替换为：

```java
package com.powergateway.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ColumnMeta {
    private String name;
    private String type;
    @JsonProperty("isPrimary")
    private boolean isPrimary;
    @JsonProperty("isUnique")
    private boolean isUnique;
    private boolean nullable;
    private String remarks;
}
```

- [ ] **Step 1.2：TableMetaService 查询唯一索引**

在 `TableMetaService.queryFromJdbc()` 中，for 循环 `for (TableMeta table : tables)` 的末尾（获取列信息的 `cs` ResultSet 关闭之后），追加：

```java
// 标记唯一索引列
Set<String> uniqueCols = getUniqueIndexColumns(meta, catalog, table.getTableName());
for (ColumnMeta col : table.getColumns()) {
    if (uniqueCols.contains(col.getName())) {
        col.setUnique(true);
    }
}
```

在 `TableMetaService` 类末尾（紧跟 `getPrimaryKeys` 方法之后）新增私有方法：

```java
private Set<String> getUniqueIndexColumns(DatabaseMetaData meta, String catalog, String tableName) {
    Set<String> uniqueCols = new HashSet<>();
    try (ResultSet rs = meta.getIndexInfo(catalog, null, tableName, true, false)) {
        while (rs.next()) {
            String colName = rs.getString("COLUMN_NAME");
            if (colName != null) {
                uniqueCols.add(colName);
            }
        }
    } catch (Exception e) {
        log.warn("[M2-5] 获取唯一索引失败: {}", e.getMessage());
    }
    return uniqueCols;
}
```

- [ ] **Step 1.3：运行已有测试确认无退化**

```bash
cd backend
mvn test -Dtest=M22TableStructureTest
```

期望：全绿（`isUnique` 字段序列化为 `false`，不影响已有 JSON 解析）。

- [ ] **Step 1.4：提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/ColumnMeta.java \
        backend/src/main/java/com/powergateway/service/TableMetaService.java
git commit -m "feat(m2-5): ColumnMeta add isUnique field, TableMetaService fetch unique indexes"
```

---

## Task 2：新建 UpdateConfigJson DTO

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/UpdateConfigJson.java`

- [ ] **Step 2.1：创建 UpdateConfigJson.java**

```java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

/**
 * UPDATE 接口的 config_json 结构（M2-5）。
 *
 * 升级点：conditions 当前为共享列表（方案B），通过 tableName 字段区分目标表。
 * 未来可扩展为每表独立条件组，JSON Schema 无需变更。
 */
@Data
public class UpdateConfigJson {

    /** 修改字段配置，最多3张表 */
    private List<TableUpdateConfig> tables;

    /**
     * 修改条件（共享条件集）。
     * 每条含 tableName，映射到对应表的 WHERE 子句。
     * 至少一张表的条件字段必须是主键或唯一索引，否则保存报错。
     */
    private List<ConditionConfig> conditions;

    @Data
    public static class TableUpdateConfig {
        private String tableName;
        /** 复用 InsertConfigJson.FieldInsertConfig 结构（REQUEST/CONST/CALC） */
        private List<InsertConfigJson.FieldInsertConfig> fields;
    }

    @Data
    public static class ConditionConfig {
        /** 目标表名 */
        private String tableName;
        /** WHERE 字段名 */
        private String field;
        /** 操作符：EQ / NE / GT / LT / LIKE */
        private String op;
        /** 从请求参数中取值的 key */
        private String paramKey;
    }
}
```

- [ ] **Step 2.2：提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/UpdateConfigJson.java
git commit -m "feat(m2-5): add UpdateConfigJson DTO"
```

---

## Task 3：新建 UpdateBuilder 工具类

**Files:**
- Create: `backend/src/main/java/com/powergateway/utils/UpdateBuilder.java`
- Create: `backend/src/test/java/com/powergateway/UpdateBuilderTest.java`

- [ ] **Step 3.1：先写 UpdateBuilder 单元测试（Red）**

新建 `backend/src/test/java/com/powergateway/UpdateBuilderTest.java`：

```java
package com.powergateway;

import com.powergateway.model.dto.UpdateConfigJson.ConditionConfig;
import com.powergateway.utils.UpdateBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class UpdateBuilderTest {

    @Test
    void 单字段单条件_生成正确SQL() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", "active");

        ConditionConfig cond = new ConditionConfig();
        cond.setTableName("orders");
        cond.setField("id");
        cond.setOp("EQ");
        cond.setParamKey("orderId");

        Map<String, Object> params = new HashMap<>();
        params.put("orderId", 42L);

        UpdateBuilder.SqlResult result = UpdateBuilder.build(
                "orders", fields, Collections.singletonList(cond), params);

        assertEquals("UPDATE orders SET status = ? WHERE id = ?", result.sql);
        assertEquals(Arrays.asList("active", 42L), result.params);
    }

    @Test
    void 多字段多条件_生成正确SQL() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("price", 99.9);
        fields.put("category", "A");

        ConditionConfig c1 = new ConditionConfig();
        c1.setTableName("products");
        c1.setField("id");
        c1.setOp("EQ");
        c1.setParamKey("productId");

        ConditionConfig c2 = new ConditionConfig();
        c2.setTableName("products");
        c2.setField("brand");
        c2.setOp("EQ");
        c2.setParamKey("brand");

        Map<String, Object> params = new HashMap<>();
        params.put("productId", 10L);
        params.put("brand", "Nike");

        UpdateBuilder.SqlResult result = UpdateBuilder.build(
                "products", fields, Arrays.asList(c1, c2), params);

        assertEquals("UPDATE products SET price = ?, category = ? WHERE id = ? AND brand = ?", result.sql);
        assertEquals(Arrays.asList(99.9, "A", 10L, "Nike"), result.params);
    }

    @Test
    void 无修改字段_抛异常() {
        assertThrows(IllegalArgumentException.class, () ->
                UpdateBuilder.build("orders", Collections.emptyMap(),
                        Collections.emptyList(), Collections.emptyMap()));
    }

    @Test
    void 无条件_抛异常() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", "x");
        assertThrows(IllegalArgumentException.class, () ->
                UpdateBuilder.build("orders", fields,
                        Collections.emptyList(), Collections.emptyMap()));
    }
}
```

- [ ] **Step 3.2：运行确认 Red**

```bash
mvn test -Dtest=UpdateBuilderTest
```

期望：编译失败（`UpdateBuilder` 不存在）。

- [ ] **Step 3.3：实现 UpdateBuilder.java**

```java
package com.powergateway.utils;

import com.powergateway.model.dto.UpdateConfigJson.ConditionConfig;

import java.util.*;

/**
 * UPDATE SQL 构建器（M2-5）。
 * 调用方按 tableName 过滤 conditions 后传入（只传该表的条件）。
 */
public class UpdateBuilder {

    public static SqlResult build(String tableName,
                                   Map<String, Object> fieldValues,
                                   List<ConditionConfig> conditions,
                                   Map<String, Object> params) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            throw new IllegalArgumentException("修改字段不能为空");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("WHERE 条件不能为空");
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        List<Object> bindParams = new ArrayList<>();

        // SET 子句
        Iterator<Map.Entry<String, Object>> it = fieldValues.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            sql.append(entry.getKey()).append(" = ?");
            bindParams.add(entry.getValue());
            if (it.hasNext()) sql.append(", ");
        }

        // WHERE 子句
        sql.append(" WHERE ");
        for (int i = 0; i < conditions.size(); i++) {
            ConditionConfig cond = conditions.get(i);
            if (i > 0) sql.append(" AND ");
            sql.append(cond.getField()).append(opToSql(cond.getOp()));
            bindParams.add(params.get(cond.getParamKey()));
        }

        return new SqlResult(sql.toString(), bindParams);
    }

    private static String opToSql(String op) {
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

- [ ] **Step 3.4：运行确认 Green**

```bash
mvn test -Dtest=UpdateBuilderTest
```

期望：4条全绿。

- [ ] **Step 3.5：提交**

```bash
git add backend/src/main/java/com/powergateway/utils/UpdateBuilder.java \
        backend/src/test/java/com/powergateway/UpdateBuilderTest.java
git commit -m "feat(m2-5): add UpdateBuilder with TDD"
```

---

## Task 4：写集成测试（Red）

**Files:**
- Create: `backend/src/test/java/com/powergateway/M25UpdateConfigTest.java`

- [ ] **Step 4.1：新建测试类**

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

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * M2-5 修改接口配置验收测试
 *
 * 验收标准：
 * 1. 保存 UPDATE 配置（含主键条件）→ 成功
 * 2. 单表 UPDATE（REQUEST）→ 字段正确更新
 * 3. 单表 UPDATE（CONST）→ 字段为固定值
 * 4. 多表 UPDATE 成功 → 两表均更新
 * 5. 审计日志含 before_snapshot
 * 6. 无条件保存 → 报错
 * 7. 条件字段非主键/唯一键 → 报错
 * 8. 多表 UPDATE 第二表失败 → 事务回滚
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-5 修改接口配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M25UpdateConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL  = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        // 创建测试用表
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS m25_product");
            stmt.execute("DROP TABLE IF EXISTS m25_inventory");
            stmt.execute(
                "CREATE TABLE m25_product (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  name VARCHAR(64) NOT NULL," +
                "  price DECIMAL(10,2)," +
                "  category VARCHAR(32)" +
                ")"
            );
            stmt.execute(
                "CREATE TABLE m25_inventory (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  product_id BIGINT NOT NULL," +
                "  stock INT NOT NULL" +
                ")"
            );
            stmt.execute("INSERT INTO m25_product (name, price, category) VALUES ('商品A', 100.00, 'FOOD')");
            stmt.execute("INSERT INTO m25_product (name, price, category) VALUES ('商品B', 200.00, 'ELEC')");
            stmt.execute("INSERT INTO m25_inventory (product_id, stock) VALUES (1, 50)");
            stmt.execute("INSERT INTO m25_inventory (product_id, stock) VALUES (2, 30)");
        }

        // 登录获取 token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data");

        // 注册指向 H2 的数据库连接
        DbConnection dbConn = new DbConnection();
        dbConn.setName("m25-test-db");
        dbConn.setDbType("MySQL");
        dbConn.setUrl(H2_URL);
        dbConn.setUsername("sa");
        AesUtil aesUtil = new AesUtil(AES_KEY);
        dbConn.setPassword(aesUtil.encrypt(""));
        dbConn.setEnv("dev");
        dbConnectionMapper.insert(dbConn);
        testDbId = dbConn.getId();
    }

    // ─── 辅助方法 ─────────────────────────────────────────────────

    private Long saveUpdateInterface(String name, String configJson) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        MvcResult result = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data");
    }

    private int executeUpdate(Long id, Map<String, Object> params) throws Exception {
        String body = objectMapper.writeValueAsString(buildExecBody(params));
        MvcResult result = mockMvc.perform(post("/api/interface/" + id + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data");
    }

    private Map<String, Object> buildExecBody(Map<String, Object> params) {
        Map<String, Object> body = new HashMap<>();
        body.put("params", params);
        return body;
    }

    private Object queryField(String table, String field, long id) throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + field + " FROM " + table + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(field) : null;
            }
        }
    }

    // ─── 测试 1 ────────────────────────────────────────────────

    @Test @Order(1)
    void 保存UPDATE配置_含主键条件_成功() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"category\",\"sourceType\":\"REQUEST\",\"paramKey\":\"category\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        String body = "{\"name\":\"test-upd-1\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        MvcResult result = mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andReturn();
        String resp = result.getResponse().getContentAsString();
        assertEquals(200, (int) JsonPath.read(resp, "$.code"));
        assertNotNull(JsonPath.read(resp, "$.data"));
    }

    // ─── 测试 2 ────────────────────────────────────────────────

    @Test @Order(2)
    void 单表UPDATE_REQUEST数据来源_字段正确更新() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"category\",\"sourceType\":\"REQUEST\",\"paramKey\":\"category\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-request", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 1);
        params.put("category", "DRINK");
        int affected = executeUpdate(id, params);
        assertEquals(1, affected);
        assertEquals("DRINK", queryField("m25_product", "category", 1));
    }

    // ─── 测试 3 ────────────────────────────────────────────────

    @Test @Order(3)
    void 单表UPDATE_CONST数据来源_字段为固定值() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"category\",\"sourceType\":\"CONST\",\"constValue\":\"FIXED\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-const", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 2);
        executeUpdate(id, params);
        assertEquals("FIXED", queryField("m25_product", "category", 2));
    }

    // ─── 测试 4 ────────────────────────────────────────────────

    @Test @Order(4)
    void 多表UPDATE_成功_两表均正确更新() throws Exception {
        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"REQUEST\",\"paramKey\":\"price\"}]},"
                + "{\"tableName\":\"m25_inventory\",\"fields\":["
                + "{\"column\":\"stock\",\"sourceType\":\"REQUEST\",\"paramKey\":\"stock\"}]}"
                + "],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"},"
                + "{\"tableName\":\"m25_inventory\",\"field\":\"product_id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-multi", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 1);
        params.put("price", 150.0);
        params.put("stock", 88);
        int affected = executeUpdate(id, params);
        assertEquals(2, affected);
        assertNotNull(queryField("m25_product", "price", 1));
        assertNotNull(queryField("m25_inventory", "stock", 1));
    }

    // ─── 测试 5 ────────────────────────────────────────────────

    @Test @Order(5)
    void 审计日志含beforeSnapshot() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"999.00\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-snapshot", configJson);
        Map<String, Object> params = new HashMap<>();
        params.put("productId", 1);
        executeUpdate(id, params);

        // 等待异步写入
        Thread.sleep(500);

        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT before_snapshot FROM sql_audit_log WHERE interface_id = ? AND op_type = 'UPDATE'")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "应有审计记录");
                String snapshot = rs.getString("before_snapshot");
                assertNotNull(snapshot, "before_snapshot 不能为 null");
                assertFalse(snapshot.isBlank(), "before_snapshot 不能为空字符串");
            }
        }
    }

    // ─── 测试 6 ────────────────────────────────────────────────

    @Test @Order(6)
    void 保存UPDATE_无条件_报错() throws Exception {
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}"
                + "]}],\"conditions\":[]}";
        String body = "{\"name\":\"upd-no-cond\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    // ─── 测试 7 ────────────────────────────────────────────────

    @Test @Order(7)
    void 保存UPDATE_条件字段非主键非唯一键_报错() throws Exception {
        // name 字段不是主键也无唯一索引
        String configJson = "{\"tables\":[{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}"
                + "]}],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"name\",\"op\":\"EQ\",\"paramKey\":\"name\"}"
                + "]}";
        String body = "{\"name\":\"upd-non-pk\",\"dbConnectionId\":" + testDbId
                + ",\"type\":\"UPDATE\",\"configJson\":" + objectMapper.writeValueAsString(configJson) + "}";
        mockMvc.perform(post("/api/interface/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(not(200)));
    }

    // ─── 测试 8 ────────────────────────────────────────────────

    @Test @Order(8)
    void 多表UPDATE_第二表字段不存在_事务回滚() throws Exception {
        Object originalPrice = queryField("m25_product", "price", 2);

        String configJson = "{\"tables\":["
                + "{\"tableName\":\"m25_product\",\"fields\":["
                + "{\"column\":\"price\",\"sourceType\":\"CONST\",\"constValue\":\"1.0\"}]},"
                + "{\"tableName\":\"m25_inventory\",\"fields\":["
                + "{\"column\":\"nonexistent_col\",\"sourceType\":\"CONST\",\"constValue\":\"1\"}]}"
                + "],\"conditions\":["
                + "{\"tableName\":\"m25_product\",\"field\":\"id\",\"op\":\"EQ\",\"paramKey\":\"productId\"},"
                + "{\"tableName\":\"m25_inventory\",\"field\":\"product_id\",\"op\":\"EQ\",\"paramKey\":\"productId\"}"
                + "]}";
        Long id = saveUpdateInterface("upd-rollback", configJson);

        Map<String, Object> params = new HashMap<>();
        params.put("productId", 2);
        String body = objectMapper.writeValueAsString(buildExecBody(params));
        mockMvc.perform(post("/api/interface/" + id + "/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(jsonPath("$.code").value(not(200)));

        // 验证第一表未修改（已回滚）
        Object priceAfter = queryField("m25_product", "price", 2);
        assertEquals(originalPrice.toString(), priceAfter.toString());
    }
}
```

- [ ] **Step 4.2：运行确认 Red**

```bash
mvn test -Dtest=M25UpdateConfigTest
```

期望：编译通过，但大多数测试失败（`executeUpdate` 未实现，controller dispatch 未实现）。

---

## Task 5：扩展 InterfaceConfigService

**Files:**
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`

- [ ] **Step 5.1：新增 import**

在 `InterfaceConfigService.java` 文件头部 import 块末尾添加：

```java
import com.fasterxml.jackson.core.JsonProcessingException;
import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.aop.AuditLog;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.model.dto.UpdateConfigJson;
import com.powergateway.model.dto.UpdateConfigJson.ConditionConfig;
import com.powergateway.model.dto.UpdateConfigJson.TableUpdateConfig;
import com.powergateway.utils.UpdateBuilder;
import java.util.stream.Collectors;
```

同时确保类中已 `@Autowired` 注入 `TableMetaService`（若尚未注入）：

```java
@Autowired
private TableMetaService tableMetaService;
```

- [ ] **Step 5.2：扩展 save() 方法——UPDATE 类型校验**

在 `save()` 方法中，`if (req.getId() != null)` 分支之前（即 `entity.setStatus("draft")` 之后），添加：

```java
// UPDATE 类型：校验条件字段必须含主键或唯一索引
if ("UPDATE".equals(entity.getType())) {
    try {
        UpdateConfigJson updateConfig = objectMapper.readValue(
                req.getConfigJson(), UpdateConfigJson.class);
        validateUpdateConditions(updateConfig, req.getDbConnectionId());
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        throw new BusinessException(400, "UPDATE 配置 JSON 解析失败: " + e.getMessage());
    }
}
```

- [ ] **Step 5.3：新增 validateUpdateConditions 私有方法**

在类末尾（`executeQuery` 方法之前）添加：

```java
private void validateUpdateConditions(UpdateConfigJson config, Long dbId) {
    if (config.getConditions() == null || config.getConditions().isEmpty()) {
        throw new BusinessException(400, "UPDATE 接口必须配置 WHERE 条件");
    }

    List<TableMeta> tables = tableMetaService.getTables(dbId);

    for (TableUpdateConfig tableConfig : config.getTables()) {
        String tableName = tableConfig.getTableName();
        List<ConditionConfig> tableConds = filterConditions(config.getConditions(), tableName);

        TableMeta meta = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "目标表不存在: " + tableName));

        boolean hasUniqueKey = tableConds.stream().anyMatch(cond -> {
            String field = cond.getField();
            return meta.getColumns().stream()
                    .filter(col -> col.getName().equalsIgnoreCase(field))
                    .anyMatch(col -> col.isPrimary() || col.isUnique());
        });

        if (!hasUniqueKey) {
            throw new BusinessException(400,
                    "表 " + tableName + " 的 WHERE 条件中必须包含主键或唯一索引字段");
        }
    }
}

private List<ConditionConfig> filterConditions(List<ConditionConfig> all, String tableName) {
    if (all == null) return Collections.emptyList();
    return all.stream()
            .filter(c -> tableName.equalsIgnoreCase(c.getTableName()))
            .collect(Collectors.toList());
}
```

- [ ] **Step 5.4：新增 executeUpdate 方法**

在类末尾（`executeQuery` 方法之前）添加：

```java
// ─── M2-5 UPDATE 执行 ──────────────────────────────────────────

/**
 * 执行 UPDATE 接口：多表 JDBC 手动事务，任意表失败则全部回滚。
 * 执行前在同一连接内执行 SELECT 快照，写入 AuditContextHolder（供 @AuditLog AOP 读取）。
 *
 * 注意：Controller 须在调用本方法前通过 AuditContextHolder.set() 设置基础审计上下文。
 */
@AuditLog
public int executeUpdate(Long id, Map<String, Object> params) {
    InterfaceConfig config = interfaceConfigMapper.selectById(id);
    if (config == null) throw new BusinessException(404, "接口配置不存在");
    if (!"UPDATE".equals(config.getType())) throw new BusinessException(400, "非 UPDATE 类型接口");

    UpdateConfigJson updateConfig;
    try {
        updateConfig = objectMapper.readValue(config.getConfigJson(), UpdateConfigJson.class);
    } catch (Exception e) {
        throw new BusinessException(400, "配置 JSON 解析失败: " + e.getMessage());
    }

    List<TableUpdateConfig> tables = updateConfig.getTables();
    if (tables == null || tables.isEmpty()) throw new BusinessException(400, "未配置修改表");
    if (tables.size() > 3) throw new BusinessException(400, "最多支持3张表");

    DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
    if (conn == null) throw new BusinessException(404, "数据库连接不存在");

    // 解析字段值 + 校验 + 构建 SQL
    List<UpdateBuilder.SqlResult> sqlResults = new ArrayList<>();
    List<String> tableNames = new ArrayList<>();
    for (TableUpdateConfig tableConfig : tables) {
        Map<String, Object> fieldValues = new LinkedHashMap<>();
        for (InsertConfigJson.FieldInsertConfig field : tableConfig.getFields()) {
            Object value = dataSourceResolver.resolve(field, params);
            fieldValues.put(field.getColumn(), value);
        }
        columnValidator.validate(tableConfig.getTableName(), fieldValues, config.getDbConnectionId());
        List<ConditionConfig> tableConditions = filterConditions(
                updateConfig.getConditions(), tableConfig.getTableName());
        if (tableConditions.isEmpty()) {
            throw new BusinessException(400, "表 " + tableConfig.getTableName() + " 无对应 WHERE 条件");
        }
        sqlResults.add(UpdateBuilder.build(tableConfig.getTableName(), fieldValues, tableConditions, params));
        tableNames.add(tableConfig.getTableName());
    }

    log.info("[M2-5] 执行 UPDATE，接口 id={}，共{}张表", id, sqlResults.size());

    String password = aesUtil.decrypt(conn.getPassword());
    int totalAffected = 0;

    try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password)) {
        jdbc.setAutoCommit(false);
        try {
            // 1. 修改前快照（在事务内 SELECT，与 UPDATE 共享连接保证数据一致性）
            AuditContext auditCtx = AuditContextHolder.get();
            if (auditCtx != null) {
                String snapshot = captureSnapshot(jdbc, updateConfig.getConditions(), params, tableNames);
                auditCtx.setBeforeSnapshot(snapshot);
                auditCtx.setTargetTable(String.join(",", tableNames));
                StringBuilder sqlText = new StringBuilder();
                for (UpdateBuilder.SqlResult r : sqlResults) {
                    if (sqlText.length() > 0) sqlText.append("; ");
                    sqlText.append(r.sql);
                }
                auditCtx.setSqlText(sqlText.toString());
            }

            // 2. 执行 UPDATE
            for (UpdateBuilder.SqlResult sql : sqlResults) {
                log.info("[M2-5] SQL: {}", sql.sql);
                try (PreparedStatement ps = jdbc.prepareStatement(sql.sql)) {
                    for (int i = 0; i < sql.params.size(); i++) {
                        ps.setObject(i + 1, sql.params.get(i));
                    }
                    totalAffected += ps.executeUpdate();
                }
            }
            jdbc.commit();
        } catch (Exception e) {
            jdbc.rollback();
            throw new BusinessException(500, "修改执行失败，已回滚: " + e.getMessage());
        }
    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        throw new BusinessException(500, "数据库连接失败: " + e.getMessage());
    }

    return totalAffected;
}

private String captureSnapshot(Connection jdbc,
                                List<ConditionConfig> allConditions,
                                Map<String, Object> params,
                                List<String> tableNames) {
    List<Map<String, Object>> allRows = new ArrayList<>();
    for (String tableName : tableNames) {
        List<ConditionConfig> tableConds = filterConditions(allConditions, tableName);
        if (tableConds.isEmpty()) continue;

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ");
        List<Object> bindParams = new ArrayList<>();
        for (int i = 0; i < tableConds.size(); i++) {
            ConditionConfig cond = tableConds.get(i);
            if (i > 0) sql.append(" AND ");
            sql.append(cond.getField()).append(" = ?");
            bindParams.add(params.get(cond.getParamKey()));
        }
        sql.append(" LIMIT 100");

        try (PreparedStatement ps = jdbc.prepareStatement(sql.toString())) {
            for (int i = 0; i < bindParams.size(); i++) {
                ps.setObject(i + 1, bindParams.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("__table__", tableName);
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    allRows.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("[M2-5] 快照查询失败，表={}: {}", tableName, e.getMessage());
        }
    }

    try {
        return objectMapper.writeValueAsString(allRows);
    } catch (JsonProcessingException e) {
        return "[]";
    }
}
```

- [ ] **Step 5.5：运行测试（部分变绿）**

```bash
mvn test -Dtest=M25UpdateConfigTest
```

期望：测试 1、6、7 变绿（save 校验已实现）；其他测试仍失败（controller dispatch 未更新）。

---

## Task 6：更新 Controller execute 端点分发

**Files:**
- Modify: `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java`

- [ ] **Step 6.1：新增 import**

在 `InterfaceConfigController.java` 文件头部 import 块末尾添加：

```java
import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.exception.BusinessException;
```

- [ ] **Step 6.2：替换 execute() 方法**

将现有的 `execute()` 方法整体替换为：

```java
@PostMapping("/{id}/execute")
@Operation(summary = "执行接口（INSERT/UPDATE，按 type 分发，M2-4/M2-5，对外开放）")
public Result<Integer> execute(
        @PathVariable Long id,
        @RequestBody InterfaceExecuteRequest req) {
    com.powergateway.model.InterfaceConfig config = service.getById(id);
    String type = config.getType();

    if ("INSERT".equals(type)) {
        return Result.success(service.executeInsert(id, req.getParams()));
    } else if ("UPDATE".equals(type)) {
        // 设置基础审计上下文（before_snapshot 由 executeUpdate 内部填充）
        AuditContextHolder.set(new AuditContext()
                .setInterfaceId(id)
                .setOpType("UPDATE")
                .setTargetDb(config.getDbConnectionId() != null
                        ? config.getDbConnectionId().toString() : "unknown"));
        return Result.success(service.executeUpdate(id, req.getParams()));
    } else {
        throw new BusinessException(400, "不支持的接口类型: " + type);
    }
}
```

- [ ] **Step 6.3：运行 M2-5 测试**

```bash
mvn test -Dtest=M25UpdateConfigTest
```

期望：8条测试全绿。若测试 5（before_snapshot）失败，检查 `AuditLogService` 的异步队列在测试环境是否正常写入。

- [ ] **Step 6.4：全量回归**

```bash
mvn test
```

期望：全部已有测试通过 + M2-5 新增测试通过（总数 = 原183 + 12 = 195条）。

- [ ] **Step 6.5：提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/UpdateConfigJson.java \
        backend/src/main/java/com/powergateway/utils/UpdateBuilder.java \
        backend/src/main/java/com/powergateway/service/InterfaceConfigService.java \
        backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java \
        backend/src/test/java/com/powergateway/M25UpdateConfigTest.java \
        backend/src/test/java/com/powergateway/UpdateBuilderTest.java
git commit -m "feat(m2-5): implement UPDATE interface config backend with TDD (8+4 tests)"
```

---

## Task 7：扩展 ConditionBuilder.vue

**Files:**
- Modify: `frontend/src/components/ConditionBuilder.vue`

- [ ] **Step 7.1：替换整个 ConditionBuilder.vue**

将文件完整内容替换为：

```vue
<template>
  <div class="condition-builder">
    <div v-if="conditions.length === 0" class="empty-hint">
      <el-empty description="暂无查询条件，点击下方按钮添加" :image-size="60" />
    </div>

    <el-table v-else :data="conditions" border size="small">
      <!-- 目标表列（仅 showTableColumn=true 时显示） -->
      <el-table-column v-if="showTableColumn" label="目标表" width="160">
        <template #default="{ row }">
          <el-select v-model="row.tableName" placeholder="选择表" size="small" style="width: 100%">
            <el-option v-for="t in tableOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="字段" min-width="160">
        <template #default="{ row }">
          <el-select v-model="row.field" placeholder="选择字段" filterable style="width: 100%">
            <el-option
              v-for="opt in computedFieldOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="操作符" width="130">
        <template #default="{ row }">
          <el-select v-model="row.op" style="width: 100%">
            <el-option label="等于 (=)" value="EQ" />
            <el-option label="不等于 (≠)" value="NE" />
            <el-option label="大于 (>)" value="GT" />
            <el-option label="小于 (<)" value="LT" />
            <el-option label="包含 (LIKE)" value="LIKE" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="参数名" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.paramKey" placeholder="请求参数名（如 status）" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="70" align="center">
        <template #default="{ $index }">
          <el-button type="danger" link size="small" @click="removeCondition($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 10px">
      <el-button type="primary" plain size="small" @click="addCondition">+ 添加条件</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  /**
   * 字段选项，格式：[{ label, value, isPrimary, isUnique }, ...]
   * M2-3 传入格式为 { label, value }，兼容（isPrimary/isUnique 缺省为 falsy）
   */
  fieldOptions: {
    type: Array,
    default: () => []
  },
  /**
   * 为 true 时主键/唯一键字段名后追加 ★。
   * M2-5 修改条件区使用；M2-3 不传，默认 false，行为不变。
   */
  highlightPrimaryKeys: {
    type: Boolean,
    default: false
  },
  /**
   * 为 true 时每行条件前增加"目标表"列。
   * M2-5 多表 UPDATE 使用；M2-3 不传，默认 false。
   */
  showTableColumn: {
    type: Boolean,
    default: false
  },
  /** showTableColumn=true 时的可选表名列表 */
  tableOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const conditions = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const computedFieldOptions = computed(() => {
  if (!props.highlightPrimaryKeys) return props.fieldOptions
  return props.fieldOptions.map(opt => ({
    ...opt,
    label: (opt.isPrimary || opt.isUnique) ? opt.label + ' ★' : opt.label
  }))
})

function addCondition() {
  emit('update:modelValue', [
    ...props.modelValue,
    { tableName: '', field: '', op: 'EQ', paramKey: '' }
  ])
}

function removeCondition(index) {
  const updated = [...props.modelValue]
  updated.splice(index, 1)
  emit('update:modelValue', updated)
}
</script>

<style scoped>
.condition-builder { padding: 4px 0; }
.empty-hint { padding: 8px 0; }
</style>
```

- [ ] **Step 7.2：验证 M2-3 现有页面未受影响**

启动前端，访问 `http://localhost:5173/interface/dev`，确认条件配置区正常，无 Vue warning。

```bash
cd frontend && npm run dev
```

- [ ] **Step 7.3：提交**

```bash
git add frontend/src/components/ConditionBuilder.vue
git commit -m "feat(m2-5): extend ConditionBuilder with highlightPrimaryKeys and showTableColumn props"
```

---

## Task 8：新建 UpdateConfig.vue

**Files:**
- Create: `frontend/src/views/interface/UpdateConfig.vue`

- [ ] **Step 8.1：创建 UpdateConfig.vue**

```vue
<template>
  <div class="update-config-page">
    <div class="page-header">
      <h2>修改接口配置</h2>
      <el-button @click="router.push('/interface/dev')">返回列表</el-button>
    </div>

    <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="基本信息" />
      <el-step title="修改字段配置" />
      <el-step title="修改条件配置" />
      <el-step title="保存配置" />
    </el-steps>

    <!-- Step 1：基本信息 -->
    <div v-show="step === 0">
      <el-card>
        <template #header>Step 1 · 基本信息</template>
        <el-form label-width="120px" style="max-width: 600px">
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
        </el-form>
        <div class="step-footer">
          <el-button type="primary" :disabled="!form.name || !form.dbConnectionId" @click="step = 1">
            下一步
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Step 2：修改字段配置 -->
    <div v-show="step === 1">
      <el-card style="margin-bottom: 16px">
        <template #header>
          <span>Step 2 · 修改字段配置</span>
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
              @change="(v) => onTableSelect(tIdx, v)"
            >
              <el-option
                v-for="t in tableList"
                :key="t.tableName"
                :label="t.tableName + (t.comment ? ` (${t.comment})` : '')"
                :value="t.tableName"
              />
            </el-select>
            <el-button v-if="tables.length > 1" size="small" type="danger" plain @click="removeTable(tIdx)">
              删除
            </el-button>
          </div>

          <el-table :data="tbl.fields" border size="small" style="margin-top: 10px">
            <el-table-column label="字段名" width="200">
              <template #default="{ row }">
                <el-select
                  v-model="row.column"
                  filterable
                  allow-create
                  placeholder="选择或输入字段名"
                  size="small"
                  style="width: 100%"
                  @change="(v) => onColumnSelect(tIdx, row, v)"
                >
                  <el-option
                    v-for="col in (tableColumns[tbl.tableName] || [])"
                    :key="col.name"
                    :label="col.name"
                    :value="col.name"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <span style="color: #909399; font-size: 12px">{{ row.columnType || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数据来源" width="150">
              <template #default="{ row }">
                <el-select v-model="row.sourceType" size="small" style="width: 100%">
                  <el-option label="请求字段" value="REQUEST" />
                  <el-option label="固定值" value="CONST" />
                  <el-option label="运算表达式" value="CALC" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="值配置">
              <template #default="{ row }">
                <el-input v-if="row.sourceType === 'REQUEST'" v-model="row.paramKey" size="small" placeholder="请求参数名" />
                <el-input v-else-if="row.sourceType === 'CONST'" v-model="row.constValue" size="small" placeholder="固定值" />
                <el-input v-else-if="row.sourceType === 'CALC'" v-model="row.expression" size="small" placeholder="四则运算表达式" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button size="small" type="danger" link @click="removeField(tIdx, $index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-button size="small" style="margin-top: 8px" @click="addField(tIdx)">+ 添加字段</el-button>
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :disabled="!canProceedStep2" @click="step = 2">下一步</el-button>
      </div>
    </div>

    <!-- Step 3：修改条件配置 -->
    <div v-show="step === 2">
      <el-card>
        <template #header>
          Step 3 · 修改条件配置
          <span style="font-size: 12px; color: #909399; margin-left: 8px">
            ★ = 主键或唯一索引，每张表至少需要一个
          </span>
        </template>

        <ConditionBuilder
          v-model="conditions"
          :field-options="allFieldOptions"
          :highlight-primary-keys="true"
          :show-table-column="true"
          :table-options="selectedTableNames"
        />

        <div v-if="conditionError" style="color: #f56c6c; margin-top: 8px; font-size: 13px">
          {{ conditionError }}
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 1">上一步</el-button>
        <el-button type="primary" :disabled="conditions.length === 0" @click="validateAndNext">
          下一步
        </el-button>
      </div>
    </div>

    <!-- Step 4：保存配置 -->
    <div v-show="step === 3">
      <el-card>
        <template #header>Step 4 · 保存配置</template>
        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="接口名称">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="接口类型">UPDATE（修改）</el-descriptions-item>
          <el-descriptions-item label="修改表数量">{{ tables.length }} 张</el-descriptions-item>
          <el-descriptions-item label="条件数量">{{ conditions.length }} 个</el-descriptions-item>
        </el-descriptions>

        <el-collapse style="margin-bottom: 16px">
          <el-collapse-item title="查看 config_json">
            <pre style="background:#f5f5f5;padding:12px;border-radius:4px;font-size:12px;overflow:auto">{{ configJsonPreview }}</pre>
          </el-collapse-item>
        </el-collapse>

        <div class="step-footer">
          <el-button @click="step = 2">上一步</el-button>
          <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ConditionBuilder from '@/components/ConditionBuilder.vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { saveInterface } from '@/api/interface'

const router = useRouter()
const step = ref(0)

const dbList    = ref([])
const tableList = ref([])
const tableColumns = ref({})

const form = ref({ name: '', dbConnectionId: null })
const tables = ref([{ tableName: '', fields: [newField()] }])
const conditions = ref([])
const conditionError = ref('')
const saving = ref(false)

function newField() {
  return { column: '', columnType: '', sourceType: 'REQUEST', paramKey: '', constValue: '', expression: '' }
}

function addTable() {
  if (tables.value.length < 3) tables.value.push({ tableName: '', fields: [newField()] })
}
function removeTable(idx) { tables.value.splice(idx, 1) }
function addField(tIdx)   { tables.value[tIdx].fields.push(newField()) }
function removeField(tIdx, fIdx) { tables.value[tIdx].fields.splice(fIdx, 1) }

const selectedTableNames = computed(() =>
  tables.value.map(t => t.tableName).filter(Boolean)
)

const allFieldOptions = computed(() => {
  const opts = []
  for (const tbl of tables.value) {
    if (!tbl.tableName) continue
    const cols = tableColumns.value[tbl.tableName] || []
    for (const col of cols) {
      opts.push({ label: col.name, value: col.name, isPrimary: col.isPrimary, isUnique: col.isUnique })
    }
  }
  return opts
})

async function onDbChange(dbId) {
  tableList.value = []
  tableColumns.value = {}
  tables.value.forEach(t => { t.tableName = ''; t.fields = [newField()] })
  conditions.value = []
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    tableList.value = list || []
    tableList.value.forEach(t => { tableColumns.value[t.tableName] = t.columns || [] })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

function onTableSelect(tIdx) { tables.value[tIdx].fields = [newField()] }

function onColumnSelect(tIdx, row, colName) {
  const cols = tableColumns.value[tables.value[tIdx].tableName] || []
  const meta = cols.find(c => c.name === colName)
  row.columnType = meta ? meta.type : ''
}

const canProceedStep2 = computed(() =>
  tables.value.every(t => t.tableName && t.fields.length > 0 &&
    t.fields.every(f => f.column && f.sourceType))
)

function validateAndNext() {
  conditionError.value = ''
  for (const tbl of tables.value) {
    if (!tbl.tableName) continue
    const tableConds = conditions.value.filter(c => c.tableName === tbl.tableName)
    if (tableConds.length === 0) {
      conditionError.value = `表 "${tbl.tableName}" 没有配置 WHERE 条件`
      return
    }
    const cols = tableColumns.value[tbl.tableName] || []
    const hasUnique = tableConds.some(cond => {
      const col = cols.find(c => c.name === cond.field)
      return col && (col.isPrimary || col.isUnique)
    })
    if (!hasUnique) {
      conditionError.value = `表 "${tbl.tableName}" 的条件中必须包含主键（★）或唯一索引（★）字段`
      return
    }
  }
  step.value = 3
}

const configJsonPreview = computed(() => JSON.stringify(buildConfigJson(), null, 2))

function buildConfigJson() {
  return {
    tables: tables.value.map(t => ({
      tableName: t.tableName,
      fields: t.fields.filter(f => f.column).map(f => {
        const field = { column: f.column, sourceType: f.sourceType }
        if (f.sourceType === 'REQUEST') field.paramKey   = f.paramKey
        if (f.sourceType === 'CONST')   field.constValue = f.constValue
        if (f.sourceType === 'CALC')    field.expression = f.expression
        return field
      })
    })),
    conditions: conditions.value.map(c => ({
      tableName: c.tableName,
      field:     c.field,
      op:        c.op,
      paramKey:  c.paramKey
    }))
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await saveInterface({
      name:           form.value.name,
      dbConnectionId: form.value.dbConnectionId,
      type:           'UPDATE',
      configJson:     JSON.stringify(buildConfigJson())
    })
    ElMessage.success('保存成功')
    router.push('/interface/dev')
  } catch {
    // request.js 拦截器已处理报错提示
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    dbList.value = (await listConnections()) || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
})
</script>

<style scoped>
.update-config-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.table-block { padding: 12px; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 16px; }
.table-block-header { display: flex; align-items: center; margin-bottom: 6px; }
.table-block-title { font-weight: 600; color: #303133; }
.step-footer { margin-top: 20px; display: flex; gap: 12px; }
</style>
```

- [ ] **Step 8.2：提交**

```bash
git add frontend/src/views/interface/UpdateConfig.vue
git commit -m "feat(m2-5): add UpdateConfig.vue with 4-step wizard"
```

---

## Task 9：路由 + 菜单注册

**Files:**
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/components/layout/SideMenu.vue`

- [ ] **Step 9.1：在 router/index.js 注册路由**

在 `interface/insert` 路由条目之后添加：

```js
{
  path: 'interface/update',
  name: 'InterfaceUpdate',
  component: () => import('@/views/interface/UpdateConfig.vue'),
  meta: { title: '修改接口配置' }
},
```

- [ ] **Step 9.2：在 SideMenu.vue 添加菜单项**

在 `<el-menu-item index="/interface/insert">插入接口配置</el-menu-item>` 之后添加：

```html
<el-menu-item index="/interface/update">修改接口配置</el-menu-item>
```

- [ ] **Step 9.3：浏览器验证（golden path）**

1. 访问 `http://localhost:5173`，侧边菜单出现"修改接口配置"
2. 点击进入 `/interface/update`
3. Step 1：选择数据库连接
4. Step 2：添加表，配置修改字段
5. Step 3：添加条件，主键字段显示 ★；未配置主键条件点"下一步"弹出错误提示
6. Step 4：点击保存，后端返回 200 成功

- [ ] **Step 9.4：提交**

```bash
git add frontend/src/router/index.js \
        frontend/src/components/layout/SideMenu.vue
git commit -m "feat(m2-5): register UpdateConfig route and menu entry"
```

---

## 验收检查

```bash
# 后端全量测试（含 M25UpdateConfigTest 8条 + UpdateBuilderTest 4条）
cd backend && mvn test

# 前端构建验证（无编译错误）
cd frontend && npm run build
```
