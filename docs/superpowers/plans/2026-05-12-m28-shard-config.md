# M2-8 分库分表配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现分库分表规则的可视化配置与路由（取模/范围，含补查），并将路由逻辑集成到已有的 SELECT/INSERT/UPDATE/DELETE 执行链路中。

**Architecture:** 新增纯工具类 `ShardRouter`（无 Spring 依赖）执行路由算法；`ShardConfigService` 负责 CRUD 与补查流程；`InterfaceConfigService` 中新增 `resolveSharding()` 在执行时动态替换目标库和表名。缓存接口（cache_enabled=1）跳过分片路由。

**Tech Stack:** Spring Boot 2.7 / MyBatis-Plus / H2（测试）/ Vue 3 / Element Plus

---

## 文件清单

| 操作 | 文件 |
|------|------|
| 新建 | `backend/src/main/java/com/powergateway/model/dto/ShardRuleJson.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/ShardRouteResult.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/ShardSaveRequest.java` |
| 新建 | `backend/src/main/java/com/powergateway/utils/ShardRouter.java` |
| 新建 | `backend/src/main/java/com/powergateway/service/ShardConfigService.java` |
| 新建 | `backend/src/main/java/com/powergateway/controller/ShardConfigController.java` |
| 新建 | `backend/src/test/java/com/powergateway/M28ShardRouterTest.java` |
| 新建 | `backend/src/test/java/com/powergateway/M28ShardConfigTest.java` |
| 新建 | `frontend/src/api/shardConfig.js` |
| 新建 | `frontend/src/views/interface/ShardConfig.vue` |
| 修改 | `backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java` |
| 修改 | `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java` |
| 修改 | `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java` |
| 修改 | `frontend/src/api/interface.js` |
| 修改 | `frontend/src/router/index.js` |
| 修改 | `frontend/src/views/interface/InterfaceList.vue` |

---

## Task 1: 三个 DTO 类

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/ShardRuleJson.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/ShardRouteResult.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/ShardSaveRequest.java`

- [ ] **Step 1: 创建 ShardRuleJson.java**

```java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ShardRuleJson {

    private String routingField;
    private FieldLookupConfig fieldLookup;
    private AlgorithmConfig algorithm;
    /** 取模路由分段列表（MODULO 使用） */
    private List<DbSegment> dbSegments;
    /** 范围路由列表（RANGE 使用） */
    private List<ShardItem> shards;

    @Data
    public static class FieldLookupConfig {
        private Long dbConnectionId;
        private String table;
        private String conditionColumn;
        private String conditionParamKey;
        private String targetColumn;
    }

    @Data
    public static class AlgorithmConfig {
        /** MODULO 或 RANGE */
        private String type;
        /** MODULO 时必填 */
        private Integer divisor;
    }

    @Data
    public static class DbSegment {
        private Long dbConnectionId;
        private String tablePrefix;
        private int indexStart;
        private int indexEnd;
        /** 0=不补零；2=两位补零（orders_03）；以此类推 */
        private Integer indexPadding;
    }

    @Data
    public static class ShardItem {
        private Long rangeStart;
        private Long rangeEnd;
        private Long dbConnectionId;
        private String tableName;
    }
}
```

- [ ] **Step 2: 创建 ShardRouteResult.java**

```java
package com.powergateway.model.dto;

import lombok.Data;

@Data
public class ShardRouteResult {
    private Long dbConnectionId;
    private String dbName;
    private String tableName;
}
```

- [ ] **Step 3: 创建 ShardSaveRequest.java**

```java
package com.powergateway.model.dto;

import lombok.Data;

@Data
public class ShardSaveRequest {
    /** null = 新增，非 null = 更新 */
    private Long id;
    private String name;
    /** 完整 shard_rule JSON 字符串 */
    private String shardRule;
}
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/ShardRuleJson.java \
        backend/src/main/java/com/powergateway/model/dto/ShardRouteResult.java \
        backend/src/main/java/com/powergateway/model/dto/ShardSaveRequest.java
git commit -m "feat(M2-8): add ShardRuleJson, ShardRouteResult, ShardSaveRequest DTOs"
```

---

## Task 2: ShardRouter 路由工具类（TDD）

**Files:**
- Create: `backend/src/test/java/com/powergateway/M28ShardRouterTest.java`
- Create: `backend/src/main/java/com/powergateway/utils/ShardRouter.java`

- [ ] **Step 1: 写测试文件 M28ShardRouterTest.java（先写，确保编译通过但运行失败）**

```java
package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.ShardRuleJson;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.utils.ShardRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("M2-8 ShardRouter 路由算法单元测试")
class M28ShardRouterTest {

    // ─── 辅助构建方法 ─────────────────────────────────────────────────────────────

    /** 构建取模路由规则：divisor=16，两段：0-7→db1，8-15→db2 */
    private ShardRuleJson buildModuloRule(int indexPadding) {
        ShardRuleJson rule = new ShardRuleJson();
        rule.setRoutingField("userId");

        ShardRuleJson.AlgorithmConfig algo = new ShardRuleJson.AlgorithmConfig();
        algo.setType("MODULO");
        algo.setDivisor(16);
        rule.setAlgorithm(algo);

        ShardRuleJson.DbSegment seg1 = new ShardRuleJson.DbSegment();
        seg1.setDbConnectionId(1L);
        seg1.setTablePrefix("orders_");
        seg1.setIndexStart(0);
        seg1.setIndexEnd(7);
        seg1.setIndexPadding(indexPadding);

        ShardRuleJson.DbSegment seg2 = new ShardRuleJson.DbSegment();
        seg2.setDbConnectionId(2L);
        seg2.setTablePrefix("orders_");
        seg2.setIndexStart(8);
        seg2.setIndexEnd(15);
        seg2.setIndexPadding(indexPadding);

        rule.setDbSegments(Arrays.asList(seg1, seg2));
        return rule;
    }

    /** 构建范围路由规则：1-999→db1/trade_001，1000-1999→db2/trade_002 */
    private ShardRuleJson buildRangeRule() {
        ShardRuleJson rule = new ShardRuleJson();
        rule.setRoutingField("tradeId");

        ShardRuleJson.AlgorithmConfig algo = new ShardRuleJson.AlgorithmConfig();
        algo.setType("RANGE");
        rule.setAlgorithm(algo);

        ShardRuleJson.ShardItem s1 = new ShardRuleJson.ShardItem();
        s1.setRangeStart(1L); s1.setRangeEnd(999L);
        s1.setDbConnectionId(1L); s1.setTableName("trade_001");

        ShardRuleJson.ShardItem s2 = new ShardRuleJson.ShardItem();
        s2.setRangeStart(1000L); s2.setRangeEnd(1999L);
        s2.setDbConnectionId(2L); s2.setTableName("trade_002");

        rule.setShards(Arrays.asList(s1, s2));
        return rule;
    }

    // ─── 取模路由 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("取模路由_低位索引_路由到第一库")
    void modulo_lowIndex_routesToDb1() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "3");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_3");
    }

    @Test
    @DisplayName("取模路由_边界值indexStart=0")
    void modulo_boundary_zero() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "0");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_0");
    }

    @Test
    @DisplayName("取模路由_边界值indexEnd=7")
    void modulo_boundary_seven() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "7");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_7");
    }

    @Test
    @DisplayName("取模路由_高位索引_路由到第二库")
    void modulo_highIndex_routesToDb2() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "8");
        assertThat(r.getDbConnectionId()).isEqualTo(2L);
        assertThat(r.getTableName()).isEqualTo("orders_8");
    }

    @Test
    @DisplayName("取模路由_大数字取模后命中低位")
    void modulo_largeNumber_moduloToLow() {
        // 32 % 16 = 0 → orders_0
        ShardRouteResult r = ShardRouter.route(buildModuloRule(0), "32");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("orders_0");
    }

    @Test
    @DisplayName("取模路由_indexPadding=2_表名补零")
    void modulo_padding2_tableNamePadded() {
        ShardRouteResult r = ShardRouter.route(buildModuloRule(2), "3");
        assertThat(r.getTableName()).isEqualTo("orders_03");
    }

    @Test
    @DisplayName("取模路由_空分段列表_抛BusinessException")
    void modulo_emptySegments_throws() {
        ShardRuleJson rule = buildModuloRule(0);
        rule.setDbSegments(Collections.emptyList());
        assertThatThrownBy(() -> ShardRouter.route(rule, "3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置分段");
    }

    // ─── 范围路由 ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("范围路由_命中第一段")
    void range_firstShard() {
        ShardRouteResult r = ShardRouter.route(buildRangeRule(), "500");
        assertThat(r.getDbConnectionId()).isEqualTo(1L);
        assertThat(r.getTableName()).isEqualTo("trade_001");
    }

    @Test
    @DisplayName("范围路由_边界值rangeStart")
    void range_boundary_start() {
        assertThat(ShardRouter.route(buildRangeRule(), "1").getTableName()).isEqualTo("trade_001");
    }

    @Test
    @DisplayName("范围路由_边界值rangeEnd")
    void range_boundary_end() {
        assertThat(ShardRouter.route(buildRangeRule(), "999").getTableName()).isEqualTo("trade_001");
    }

    @Test
    @DisplayName("范围路由_命中第二段")
    void range_secondShard() {
        ShardRouteResult r = ShardRouter.route(buildRangeRule(), "1000");
        assertThat(r.getDbConnectionId()).isEqualTo(2L);
        assertThat(r.getTableName()).isEqualTo("trade_002");
    }

    @Test
    @DisplayName("范围路由_超出所有范围_抛BusinessException")
    void range_outOfRange_throws() {
        assertThatThrownBy(() -> ShardRouter.route(buildRangeRule(), "9999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无匹配分片范围");
    }

    // ─── pad 工具方法 ──────────────────────────────────────────────────────────────

    @Test @DisplayName("pad_不补零") void pad_zero() {
        assertThat(ShardRouter.pad(3, 0)).isEqualTo("3");
    }

    @Test @DisplayName("pad_补2位") void pad_two() {
        assertThat(ShardRouter.pad(3, 2)).isEqualTo("03");
        assertThat(ShardRouter.pad(15, 2)).isEqualTo("15");
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败（ShardRouter 未创建）**

```bash
cd backend && mvn test -Dtest=M28ShardRouterTest -q 2>&1 | tail -5
```

期望输出：`COMPILATION ERROR` 或 `cannot find symbol`

- [ ] **Step 3: 创建 ShardRouter.java**

```java
package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.ShardRuleJson;
import com.powergateway.model.dto.ShardRuleJson.DbSegment;
import com.powergateway.model.dto.ShardRuleJson.ShardItem;
import com.powergateway.model.dto.ShardRouteResult;

import java.util.List;

public class ShardRouter {

    public static ShardRouteResult route(ShardRuleJson rule, String fieldValue) {
        if (rule == null || rule.getAlgorithm() == null) {
            throw new BusinessException(400, "分片规则配置不完整");
        }
        long val;
        try {
            val = Long.parseLong(fieldValue.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "路由字段值非数字: " + fieldValue);
        }
        String type = rule.getAlgorithm().getType();
        if ("MODULO".equalsIgnoreCase(type)) return routeModulo(rule, val);
        if ("RANGE".equalsIgnoreCase(type))  return routeRange(rule, val);
        throw new BusinessException(400, "不支持的路由算法: " + type);
    }

    private static ShardRouteResult routeModulo(ShardRuleJson rule, long val) {
        Integer divisor = rule.getAlgorithm().getDivisor();
        if (divisor == null || divisor <= 0) {
            throw new BusinessException(400, "取模路由除数必须大于 0");
        }
        int idx = (int)(val % divisor);
        if (idx < 0) idx += divisor;

        List<DbSegment> segments = rule.getDbSegments();
        if (segments == null || segments.isEmpty()) {
            throw new BusinessException(400, "取模路由未配置分段（dbSegments）");
        }
        for (DbSegment seg : segments) {
            if (idx >= seg.getIndexStart() && idx <= seg.getIndexEnd()) {
                int padding = seg.getIndexPadding() != null ? seg.getIndexPadding() : 0;
                ShardRouteResult result = new ShardRouteResult();
                result.setDbConnectionId(seg.getDbConnectionId());
                result.setTableName(seg.getTablePrefix() + pad(idx, padding));
                return result;
            }
        }
        throw new BusinessException(400, "取模索引 " + idx + " 无匹配分段，请检查 dbSegments 配置");
    }

    private static ShardRouteResult routeRange(ShardRuleJson rule, long val) {
        List<ShardItem> shards = rule.getShards();
        if (shards == null || shards.isEmpty()) {
            throw new BusinessException(400, "范围路由未配置分片列表（shards）");
        }
        for (ShardItem shard : shards) {
            if (val >= shard.getRangeStart() && val <= shard.getRangeEnd()) {
                ShardRouteResult result = new ShardRouteResult();
                result.setDbConnectionId(shard.getDbConnectionId());
                result.setTableName(shard.getTableName());
                return result;
            }
        }
        throw new BusinessException(400, "值 " + val + " 无匹配分片范围，请检查 shards 配置");
    }

    /** package-private，供测试直接调用 */
    static String pad(int idx, int padding) {
        if (padding <= 0) return String.valueOf(idx);
        return String.format("%0" + padding + "d", idx);
    }
}
```

- [ ] **Step 4: 运行测试，确认全绿**

```bash
mvn test -Dtest=M28ShardRouterTest -q
```

期望输出：`BUILD SUCCESS`，14 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/test/java/com/powergateway/M28ShardRouterTest.java \
        backend/src/main/java/com/powergateway/utils/ShardRouter.java
git commit -m "feat(M2-8): add ShardRouter routing algorithm (TDD, 14 tests)"
```

---

## Task 3: ShardConfigService（TDD）

**Files:**
- Create: `backend/src/test/java/com/powergateway/M28ShardConfigTest.java`（Service 部分）
- Create: `backend/src/main/java/com/powergateway/service/ShardConfigService.java`

- [ ] **Step 1: 创建 M28ShardConfigTest.java（Service CRUD + preview 部分，Controller 部分 Task 4 追加）**

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.ShardConfigMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.ShardConfig;
import com.powergateway.model.dto.ShardSaveRequest;
import com.powergateway.service.ShardConfigService;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-8 分库分表配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M28ShardConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ShardConfigService shardConfigService;
    @Autowired private ShardConfigMapper shardConfigMapper;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    private String moduloRule(Long dbId) {
        return "{" +
            "\"routingField\":\"userId\"," +
            "\"algorithm\":{\"type\":\"MODULO\",\"divisor\":16}," +
            "\"dbSegments\":[" +
                "{\"dbConnectionId\":" + dbId + ",\"tablePrefix\":\"orders_\",\"indexStart\":0,\"indexEnd\":7,\"indexPadding\":0}," +
                "{\"dbConnectionId\":" + dbId + ",\"tablePrefix\":\"orders_\",\"indexStart\":8,\"indexEnd\":15,\"indexPadding\":0}" +
            "]}";
    }

    @BeforeAll
    void setUp() {
        DbConnection conn = new DbConnection();
        conn.setName("H2_Shard_" + System.currentTimeMillis());
        conn.setDbType("MySQL");
        conn.setUrl(H2_URL);
        conn.setUsername("sa");
        conn.setPassword(AesUtil.encrypt("", AES_KEY));
        conn.setEnv("test");
        conn.setPoolSize(2);
        conn.setTimeout(3000);
        dbConnectionMapper.insert(conn);
        testDbId = conn.getId();
    }

    @BeforeEach
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @AfterAll
    void cleanup() {
        if (testDbId != null) dbConnectionMapper.deleteById(testDbId);
    }

    // ─── Service CRUD ────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("save_新增_返回ID且能查到")
    void save_new_returnIdAndListable() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("测试规则_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        assertThat(id).isNotNull().isPositive();
        List<ShardConfig> list = shardConfigService.list(null, 1, 100);
        assertThat(list).anyMatch(c -> c.getId().equals(id));
        shardConfigMapper.deleteById(id);
    }

    @Test @Order(2)
    @DisplayName("save_名称为空_抛异常")
    void save_emptyName_throws() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setShardRule(moduloRule(testDbId));
        assertThatThrownBy(() -> shardConfigService.save(req))
                .hasMessageContaining("名称不能为空");
    }

    @Test @Order(3)
    @DisplayName("save_分片规则JSON格式错误_抛异常")
    void save_invalidJson_throws() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("bad");
        req.setShardRule("not-json{{");
        assertThatThrownBy(() -> shardConfigService.save(req))
                .hasMessageContaining("JSON 格式错误");
    }

    @Test @Order(4)
    @DisplayName("delete_逻辑删除后list查不到")
    void delete_notInListAfterDelete() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("待删_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        shardConfigService.delete(id);
        assertThat(shardConfigService.list(null, 1, 100)).noneMatch(c -> c.getId().equals(id));
    }

    @Test @Order(5)
    @DisplayName("preview_取模路由_直接从参数取值_返回正确库表名")
    void preview_modulo_direct_correctTableName() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("预览_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", "3"); // 3 % 16 = 3 → orders_3
            var result = shardConfigService.preview(id, params);
            assertThat(result.getDbConnectionId()).isEqualTo(testDbId);
            assertThat(result.getTableName()).isEqualTo("orders_3");
            assertThat(result.getDbName()).isNotNull();
        } finally {
            shardConfigMapper.deleteById(id);
        }
    }

    @Test @Order(6)
    @DisplayName("preview_路由字段不在参数中_抛异常")
    void preview_missingRoutingField_throws() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("缺参数_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        try {
            assertThatThrownBy(() -> shardConfigService.preview(id, new HashMap<>()))
                    .hasMessageContaining("路由字段");
        } finally {
            shardConfigMapper.deleteById(id);
        }
    }
}
```

- [ ] **Step 2: 运行测试，确认编译失败（ShardConfigService 未创建）**

```bash
mvn test -Dtest=M28ShardConfigTest -q 2>&1 | tail -5
```

期望：`COMPILATION ERROR`

- [ ] **Step 3: 创建 ShardConfigService.java**

```java
package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.ShardConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.ShardConfig;
import com.powergateway.model.dto.ShardRuleJson;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.model.dto.ShardSaveRequest;
import com.powergateway.utils.AesUtil;
import com.powergateway.utils.ShardRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ShardConfigService {

    @Autowired private ShardConfigMapper shardConfigMapper;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private AesUtil aesUtil;
    @Autowired private ObjectMapper objectMapper;

    public List<ShardConfig> list(String name, int page, int size) {
        LambdaQueryWrapper<ShardConfig> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(ShardConfig::getName, name);
        }
        wrapper.orderByDesc(ShardConfig::getCreateTime);
        return shardConfigMapper.selectList(wrapper);
    }

    public Long save(ShardSaveRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException(400, "配置名称不能为空");
        }
        if (req.getShardRule() == null || req.getShardRule().trim().isEmpty()) {
            throw new BusinessException(400, "分片规则不能为空");
        }
        ShardRuleJson rule;
        try {
            rule = objectMapper.readValue(req.getShardRule(), ShardRuleJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "分片规则 JSON 格式错误: " + e.getMessage());
        }

        ShardConfig entity = new ShardConfig();
        entity.setName(req.getName());
        entity.setShardRule(req.getShardRule());
        entity.setRequestField(rule.getRoutingField());

        if (req.getId() != null) {
            entity.setId(req.getId());
            shardConfigMapper.updateById(entity);
            return req.getId();
        }
        shardConfigMapper.insert(entity);
        return entity.getId();
    }

    public void delete(Long id) {
        if (shardConfigMapper.selectById(id) == null) {
            throw new BusinessException(404, "分片配置不存在");
        }
        shardConfigMapper.deleteById(id);
    }

    public ShardRouteResult preview(Long shardConfigId, Map<String, Object> params) {
        ShardConfig config = shardConfigMapper.selectById(shardConfigId);
        if (config == null) throw new BusinessException(404, "分片配置不存在");

        ShardRuleJson rule;
        try {
            rule = objectMapper.readValue(config.getShardRule(), ShardRuleJson.class);
        } catch (Exception e) {
            throw new BusinessException(400, "分片规则 JSON 解析失败: " + e.getMessage());
        }

        if (rule.getFieldLookup() != null) {
            String lookedUp = doFieldLookup(rule.getFieldLookup(), params);
            params.put(rule.getRoutingField(), lookedUp);
        }

        Object fieldVal = params.get(rule.getRoutingField());
        if (fieldVal == null) {
            throw new BusinessException(400, "路由字段 '" + rule.getRoutingField() + "' 不在请求参数中");
        }

        ShardRouteResult result = ShardRouter.route(rule, String.valueOf(fieldVal));

        DbConnection conn = dbConnectionMapper.selectById(result.getDbConnectionId());
        if (conn != null) result.setDbName(conn.getName());

        return result;
    }

    private String doFieldLookup(ShardRuleJson.FieldLookupConfig lookup, Map<String, Object> params) {
        Object condVal = params.get(lookup.getConditionParamKey());
        if (condVal == null) {
            throw new BusinessException(400, "补查条件字段 '" + lookup.getConditionParamKey() + "' 不在请求参数中");
        }
        DbConnection conn = dbConnectionMapper.selectById(lookup.getDbConnectionId());
        if (conn == null) throw new BusinessException(404, "补查数据源不存在");

        String password = aesUtil.decrypt(conn.getPassword());
        String sql = "SELECT " + lookup.getTargetColumn() +
                     " FROM "  + lookup.getTable() +
                     " WHERE " + lookup.getConditionColumn() + " = ?";

        try (Connection jdbc = DriverManager.getConnection(conn.getUrl(), conn.getUsername(), password);
             PreparedStatement ps = jdbc.prepareStatement(sql)) {
            ps.setObject(1, condVal);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object val = rs.getObject(1);
                    return val != null ? val.toString() : null;
                }
                throw new BusinessException(404, "补查无结果: " + lookup.getTable() +
                        " WHERE " + lookup.getConditionColumn() + "=" + condVal);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "补查执行失败: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 4: 运行 Service 部分测试，确认全绿**

```bash
mvn test -Dtest=M28ShardConfigTest -q
```

期望：`BUILD SUCCESS`，6 个测试通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/test/java/com/powergateway/M28ShardConfigTest.java \
        backend/src/main/java/com/powergateway/service/ShardConfigService.java
git commit -m "feat(M2-8): add ShardConfigService with CRUD and preview (TDD, 6 tests)"
```

---

## Task 4: ShardConfigController（TDD）

**Files:**
- Modify: `backend/src/test/java/com/powergateway/M28ShardConfigTest.java`（追加 Controller 测试）
- Create: `backend/src/main/java/com/powergateway/controller/ShardConfigController.java`

- [ ] **Step 1: 在 M28ShardConfigTest.java 末尾（最后一个 `}` 前）追加 Controller 测试方法**

```java
    // ─── Controller 测试 ─────────────────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("POST /api/shard/save → 200 返回 id")
    void api_save_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "API_" + System.currentTimeMillis(),
                "shardRule", moduloRule(testDbId)
        ));
        MvcResult r = mockMvc.perform(post("/api/shard/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();
        Long id = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
        shardConfigMapper.deleteById(id);
    }

    @Test @Order(11)
    @DisplayName("GET /api/shard/list → 200 返回数组")
    void api_list_returns200() throws Exception {
        mockMvc.perform(get("/api/shard/list").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(12)
    @DisplayName("DELETE /api/shard/{id} → 200")
    void api_delete_returns200() throws Exception {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("待删_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        mockMvc.perform(delete("/api/shard/" + id).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(13)
    @DisplayName("POST /api/shard/{id}/preview → 200 返回路由结果")
    void api_preview_returns200WithTableName() throws Exception {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("预览API_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        try {
            mockMvc.perform(post("/api/shard/" + id + "/preview")
                            .header("satoken", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"params\":{\"userId\":\"3\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.tableName").value("orders_3"))
                    .andExpect(jsonPath("$.data.dbConnectionId").isNumber());
        } finally {
            shardConfigMapper.deleteById(id);
        }
    }

    @Test @Order(14)
    @DisplayName("未登录访问 /api/shard/list → 401")
    void api_noToken_401() throws Exception {
        mockMvc.perform(get("/api/shard/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }
```

- [ ] **Step 2: 运行测试，确认 Controller 测试失败（Controller 未创建）**

```bash
mvn test -Dtest=M28ShardConfigTest -q 2>&1 | tail -5
```

期望：Controller 相关测试 FAIL（404 Not Found）

- [ ] **Step 3: 创建 ShardConfigController.java**

```java
package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.ShardConfig;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.model.dto.ShardSaveRequest;
import com.powergateway.service.ShardConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shard")
@Tag(name = "分库分表配置", description = "分片规则 CRUD + 路由预览（M2-8）")
public class ShardConfigController {

    @Autowired
    private ShardConfigService shardConfigService;

    @GetMapping("/list")
    @Operation(summary = "分库分表配置列表")
    public Result<List<ShardConfig>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(shardConfigService.list(name, page, size));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/更新分片配置（id 为空=新增）")
    public Result<Long> save(@RequestBody ShardSaveRequest req) {
        return Result.success(shardConfigService.save(req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分片配置（逻辑删除）")
    public Result<Void> delete(@PathVariable Long id) {
        shardConfigService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "路由预览：传入请求参数，返回路由到的库名和表名")
    public Result<ShardRouteResult> preview(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map
                ? new HashMap<>((Map<String, Object>) body.get("params"))
                : Collections.emptyMap();
        return Result.success(shardConfigService.preview(id, params));
    }
}
```

- [ ] **Step 4: 运行全部测试，确认全绿**

```bash
mvn test -Dtest=M28ShardConfigTest -q
```

期望：`BUILD SUCCESS`，11 个测试全绿。

- [ ] **Step 5: 提交**

```bash
git add backend/src/test/java/com/powergateway/M28ShardConfigTest.java \
        backend/src/main/java/com/powergateway/controller/ShardConfigController.java
git commit -m "feat(M2-8): add ShardConfigController with CRUD and preview endpoints (TDD)"
```

---

## Task 5: InterfaceSaveRequest + 绑定端点

**Files:**
- Modify: `backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java`
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`（save + bindShardConfig）
- Modify: `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java`（新增 PATCH 端点）

- [ ] **Step 1: 在 InterfaceSaveRequest.java 末尾追加字段（在最后一个 `}` 前）**

在 `private String cacheKeyTemplate;` 后追加：

```java
    /** 关联分库分表配置 id（可选，null 表示不启用分片路由）*/
    private Long shardConfigId;
```

- [ ] **Step 2: 修改 InterfaceConfigService.save() 处理 shardConfigId**

在 `save()` 方法的 `if (req.getCacheKeyTemplate() != null)` 块后追加：

```java
        if (req.getShardConfigId() != null) entity.setShardConfigId(req.getShardConfigId());
```

在同一个 save() 方法内，`if (req.getId() != null)` 分支的 `updateById` 之前追加（允许清除 shardConfigId）：

```java
        // 显式允许将 shardConfigId 清空（传 null 表示解绑）
        entity.setShardConfigId(req.getShardConfigId());
```

> 注意：由于 `entity.setShardConfigId(req.getShardConfigId())` 在新增和更新两条路径都应执行，将上面两处合并为在 `entity.setStatus("draft")` 后、`if (req.getId() != null)` 前统一写入一次：
> `entity.setShardConfigId(req.getShardConfigId());`
> 删掉其他零散的 shardConfigId 赋值。

完整的 save() 中，在 `entity.setLogEnabled(1);` 后插入：

```java
        entity.setShardConfigId(req.getShardConfigId());
```

- [ ] **Step 3: 在 InterfaceConfigService.java 中追加 bindShardConfig 方法**

在 `delete()` 方法后追加：

```java
    /** 单独绑定/解绑分库分表配置（M2-8），shardConfigId=null 表示解绑 */
    public void bindShardConfig(Long id, Long shardConfigId) {
        InterfaceConfig config = interfaceConfigMapper.selectById(id);
        if (config == null) throw new BusinessException(404, "接口配置不存在");
        InterfaceConfig update = new InterfaceConfig();
        update.setId(id);
        update.setShardConfigId(shardConfigId);
        interfaceConfigMapper.updateById(update);
    }
```

- [ ] **Step 4: 在 InterfaceConfigController.java 末尾（最后一个 `}` 前）追加 PATCH 端点**

```java
    @PatchMapping("/{id}/shard-config")
    @Operation(summary = "绑定/解绑分库分表配置（M2-8），shardConfigId=null 表示解绑")
    public Result<Void> bindShardConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Object raw = body.get("shardConfigId");
        Long shardConfigId = raw != null ? Long.parseLong(raw.toString()) : null;
        service.bindShardConfig(id, shardConfigId);
        return Result.success();
    }
```

- [ ] **Step 5: 运行全量测试，确认无退化**

```bash
mvn test -q
```

期望：`BUILD SUCCESS`，原有 241 个测试 + M28 新增测试全绿。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/InterfaceSaveRequest.java \
        backend/src/main/java/com/powergateway/service/InterfaceConfigService.java \
        backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java
git commit -m "feat(M2-8): add shardConfigId to InterfaceSaveRequest and bind/unbind endpoint"
```

---

## Task 6: InterfaceConfigService 分片路由集成（TDD）

**Files:**
- Modify: `backend/src/test/java/com/powergateway/M28ShardConfigTest.java`（追加 exec 集成测试）
- Modify: `backend/src/main/java/com/powergateway/service/InterfaceConfigService.java`（resolveSharding + 4个 exec 方法）

- [ ] **Step 1: 在 M28ShardConfigTest.java 末尾（最后一个 `}` 前）追加 exec 集成测试**

```java
    // ─── Exec 集成测试：分片路由替换主表名 ────────────────────────────────────────

    @Test @Order(20)
    @DisplayName("executeQuery_配置分片路由_替换主表名后成功查询")
    void execQuery_withSharding_replacesTableNameAndSucceeds() throws Exception {
        // shard 规则：RANGE 0~MAX → sys_user（H2 中存在的表）
        ShardSaveRequest shardReq = new ShardSaveRequest();
        shardReq.setName("ExecShardTest_" + System.currentTimeMillis());
        shardReq.setShardRule("{" +
            "\"routingField\":\"userId\"," +
            "\"algorithm\":{\"type\":\"RANGE\"}," +
            "\"shards\":[{\"rangeStart\":0,\"rangeEnd\":9999999999,\"dbConnectionId\":" + testDbId +
            ",\"tableName\":\"sys_user\"}]}");
        Long shardId = shardConfigService.save(shardReq);

        // 接口配置：SELECT from "nonexistent_table"（分片路由将替换为 sys_user）
        String configJson = "{" +
            "\"tables\":[{\"name\":\"nonexistent_table\",\"alias\":\"u\"}]," +
            "\"joins\":[]," +
            "\"fields\":[{\"table\":\"u\",\"column\":\"id\",\"alias\":\"uid\"}]," +
            "\"conditions\":[]," +
            "\"processRules\":[]}";

        String saveBody = objectMapper.writeValueAsString(Map.of(
                "name", "ShardExec_" + System.currentTimeMillis(),
                "dbConnectionId", testDbId,
                "type", "SELECT",
                "configJson", configJson,
                "shardConfigId", shardId
        ));
        MvcResult saveRes = mockMvc.perform(post("/api/interface/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Long interfaceId = ((Number) JsonPath.read(saveRes.getResponse().getContentAsString(), "$.data")).longValue();

        try {
            // 发布
            mockMvc.perform(post("/api/interface/" + interfaceId + "/publish")
                            .header("satoken", token))
                    .andExpect(jsonPath("$.code").value(200));

            // 执行：userId=5 → RANGE 命中 → tableName=sys_user → 查询成功返回数据
            mockMvc.perform(post("/api/exec/" + interfaceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"params\":{\"userId\":\"5\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        } finally {
            shardConfigMapper.deleteById(shardId);
            mockMvc.perform(post("/api/interface/" + interfaceId + "/disable")
                    .header("satoken", token));
            mockMvc.perform(delete("/api/interface/" + interfaceId)
                    .header("satoken", token));
        }
    }
```

- [ ] **Step 2: 运行测试，确认新测试失败（resolveSharding 未实现）**

```bash
mvn test -Dtest="M28ShardConfigTest#execQuery_withSharding_replacesTableNameAndSucceeds" -q 2>&1 | tail -10
```

期望：FAIL，查到 `nonexistent_table` 报错。

- [ ] **Step 3: 在 InterfaceConfigService.java 中注入 ShardConfigService，追加 resolveSharding 方法**

在类的 `@Autowired` 字段区追加：

```java
    @Autowired
    private ShardConfigService shardConfigService;
```

在 `list()` 方法前追加私有方法：

```java
    private ShardRouteResult resolveSharding(InterfaceConfig config, Map<String, Object> params) {
        if (config.getShardConfigId() == null) return null;
        if (Integer.valueOf(1).equals(config.getCacheEnabled())) {
            log.warn("[M2-8] 接口 id={} 同时配置了缓存和分片路由，分片路由已跳过（缓存优先）", config.getId());
            return null;
        }
        return shardConfigService.preview(config.getShardConfigId(), params);
    }
```

同时在文件顶部 import 区追加：

```java
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.service.ShardConfigService;
```

- [ ] **Step 4: 修改 doExecuteQuery()，在解析 config 后、取 DbConnection 前插入分片路由**

找到 `doExecuteQuery` 中 `QueryConfigJson queryConfig;` 解析完成后（即 `} catch (Exception e) { throw new BusinessException... }` 之后），把原来的：

```java
        QueryBuilder.SqlResult sqlResult;
```

改为：

```java
        ShardRouteResult shard = resolveSharding(config, params);
        if (shard != null && queryConfig.getTables() != null && !queryConfig.getTables().isEmpty()) {
            queryConfig.getTables().get(0).setName(shard.getTableName());
        }
        Long queryDbConnId = shard != null ? shard.getDbConnectionId() : config.getDbConnectionId();

        QueryBuilder.SqlResult sqlResult;
```

再把后面的：

```java
        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");
```

改为：

```java
        DbConnection conn = dbConnectionMapper.selectById(queryDbConnId);
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");
```

- [ ] **Step 5: 修改 executeInsert()，在解析 insertConfig 后、validateInsert 前插入分片路由**

找到 `InsertConfigJson insertConfig;` 解析完成后，把 `List<InsertBuilder.SqlResult> sqlResults = new ArrayList<>();` 前插入：

```java
        ShardRouteResult insertShard = resolveSharding(config, params);
        if (insertShard != null && insertConfig.getTables() != null && !insertConfig.getTables().isEmpty()) {
            insertConfig.getTables().get(0).setTableName(insertShard.getTableName());
        }
        Long insertDbConnId = insertShard != null ? insertShard.getDbConnectionId() : config.getDbConnectionId();
```

把后面的：

```java
        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
```

改为：

```java
        DbConnection conn = dbConnectionMapper.selectById(insertDbConnId);
```

- [ ] **Step 6: 修改 executeUpdate()，在解析 updateConfig 后插入分片路由（同步更新 condition.tableName）**

找到 `UpdateConfigJson updateConfig;` 解析完成后，在 `List<TableUpdateConfig> tables = updateConfig.getTables();` 前插入：

```java
        ShardRouteResult updateShard = resolveSharding(config, params);
        if (updateShard != null && updateConfig.getTables() != null && !updateConfig.getTables().isEmpty()) {
            String origName = updateConfig.getTables().get(0).getTableName();
            updateConfig.getTables().get(0).setTableName(updateShard.getTableName());
            if (updateConfig.getConditions() != null) {
                updateConfig.getConditions().stream()
                        .filter(c -> origName.equalsIgnoreCase(c.getTableName()))
                        .forEach(c -> c.setTableName(updateShard.getTableName()));
            }
        }
        Long updateDbConnId = updateShard != null ? updateShard.getDbConnectionId() : config.getDbConnectionId();
```

把后面的：

```java
        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
```

改为：

```java
        DbConnection conn = dbConnectionMapper.selectById(updateDbConnId);
```

- [ ] **Step 7: 修改 executeDelete()，在解析 deleteConfig 后插入分片路由**

找到 `DeleteConfigJson deleteConfig = parseDeleteConfig(config.getConfigJson());` 之后，在 `List<DeleteConfigJson.TableDeleteConfig> tables = deleteConfig.getTables();` 前插入：

```java
        ShardRouteResult deleteShard = resolveSharding(config, params);
        if (deleteShard != null && deleteConfig.getTables() != null && !deleteConfig.getTables().isEmpty()) {
            deleteConfig.getTables().get(0).setTableName(deleteShard.getTableName());
        }
        Long deleteDbConnId = deleteShard != null ? deleteShard.getDbConnectionId() : config.getDbConnectionId();
```

把后面的：

```java
        DbConnection conn = dbConnectionMapper.selectById(config.getDbConnectionId());
```

改为：

```java
        DbConnection conn = dbConnectionMapper.selectById(deleteDbConnId);
```

- [ ] **Step 8: 运行全量测试，确认全绿无退化**

```bash
mvn test -q
```

期望：`BUILD SUCCESS`，所有测试通过（含新增 exec 集成测试）。

- [ ] **Step 9: 提交**

```bash
git add backend/src/test/java/com/powergateway/M28ShardConfigTest.java \
        backend/src/main/java/com/powergateway/service/InterfaceConfigService.java
git commit -m "feat(M2-8): integrate ShardRouter into SELECT/INSERT/UPDATE/DELETE exec flow (TDD)"
```

---

## Task 7: 前端 ShardConfig.vue + api/shardConfig.js

**Files:**
- Create: `frontend/src/api/shardConfig.js`
- Create: `frontend/src/views/interface/ShardConfig.vue`

- [ ] **Step 1: 创建 frontend/src/api/shardConfig.js**

```js
import request from '@/api/request'

export function listShardConfigs(name, page = 1, size = 100) {
  return request.get('/shard/list', { params: { name, page, size } })
}

export function saveShardConfig(data) {
  return request.post('/shard/save', data)
}

export function deleteShardConfig(id) {
  return request.delete(`/shard/${id}`)
}

export function previewShardRoute(id, params) {
  return request.post(`/shard/${id}/preview`, { params })
}
```

- [ ] **Step 2: 创建 frontend/src/views/interface/ShardConfig.vue**

```vue
<template>
  <div class="shard-config">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input v-model="searchName" placeholder="搜索配置名称" clearable
        style="width: 240px" @keyup.enter="loadList" @clear="loadList" />
      <el-button type="primary" @click="loadList">查询</el-button>
      <el-button type="success" @click="openForm(null)">新建</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="list" stripe border v-loading="loading" style="margin-top:16px">
      <el-table-column prop="name" label="配置名称" min-width="160" />
      <el-table-column prop="requestField" label="路由字段" width="140" />
      <el-table-column label="路由算法" width="120">
        <template #default="{ row }">
          <el-tag :type="algoTag(row)" size="small">{{ algoLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openForm(row)">编辑</el-button>
          <el-button size="small" type="primary" plain @click="openPreview(row.id)">路由预览</el-button>
          <el-popconfirm title="确认删除该分片配置？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑分片配置' : '新建分片配置'"
      width="760px" @close="resetForm">
      <el-form :model="form" label-width="110px" style="padding-right:16px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.name" placeholder="如：用户订单分片" />
        </el-form-item>
        <el-form-item label="路由字段" required>
          <el-input v-model="form.routingField" placeholder="请求参数中用于路由的字段名，如 userId" />
        </el-form-item>
        <el-form-item label="路由算法" required>
          <el-radio-group v-model="form.algorithmType">
            <el-radio value="MODULO">取模路由（MODULO）</el-radio>
            <el-radio value="RANGE">范围路由（RANGE）</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 取模路由专属 -->
        <template v-if="form.algorithmType === 'MODULO'">
          <el-form-item label="取模除数" required>
            <el-input-number v-model="form.divisor" :min="1" :max="10000" style="width:160px" />
            <span style="margin-left:8px;color:#999">分表总数</span>
          </el-form-item>
          <el-form-item label="库段配置">
            <div style="width:100%">
              <el-table :data="form.dbSegments" border size="small">
                <el-table-column label="数据源" min-width="160">
                  <template #default="{ row }">
                    <el-select v-model="row.dbConnectionId" placeholder="选择连接" style="width:100%">
                      <el-option v-for="c in dbList" :key="c.id" :label="c.name" :value="c.id" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="表前缀" width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.tablePrefix" placeholder="如 orders_" />
                  </template>
                </el-table-column>
                <el-table-column label="起始索引" width="90">
                  <template #default="{ row }">
                    <el-input-number v-model="row.indexStart" :min="0" style="width:80px" controls-position="right" />
                  </template>
                </el-table-column>
                <el-table-column label="结束索引" width="90">
                  <template #default="{ row }">
                    <el-input-number v-model="row.indexEnd" :min="0" style="width:80px" controls-position="right" />
                  </template>
                </el-table-column>
                <el-table-column label="补零位数" width="90">
                  <template #default="{ row }">
                    <el-input-number v-model="row.indexPadding" :min="0" :max="6"
                      style="width:80px" controls-position="right" />
                  </template>
                </el-table-column>
                <el-table-column label="" width="60">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" link @click="form.dbSegments.splice($index, 1)">删</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" style="margin-top:8px" @click="addSegment">+ 添加库段</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- 范围路由专属 -->
        <template v-if="form.algorithmType === 'RANGE'">
          <el-form-item label="范围分片">
            <div style="width:100%">
              <el-table :data="form.shards" border size="small">
                <el-table-column label="起始值" width="110">
                  <template #default="{ row }">
                    <el-input v-model="row.rangeStart" placeholder="如 1" />
                  </template>
                </el-table-column>
                <el-table-column label="结束值" width="110">
                  <template #default="{ row }">
                    <el-input v-model="row.rangeEnd" placeholder="如 999" />
                  </template>
                </el-table-column>
                <el-table-column label="数据源" min-width="140">
                  <template #default="{ row }">
                    <el-select v-model="row.dbConnectionId" style="width:100%">
                      <el-option v-for="c in dbList" :key="c.id" :label="c.name" :value="c.id" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="表名" width="130">
                  <template #default="{ row }">
                    <el-input v-model="row.tableName" placeholder="如 trade_001" />
                  </template>
                </el-table-column>
                <el-table-column label="" width="60">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" link @click="form.shards.splice($index, 1)">删</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" style="margin-top:8px" @click="addShard">+ 添加分片</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- 补查配置（折叠） -->
        <el-form-item label="补查配置">
          <el-collapse style="width:100%">
            <el-collapse-item title="若路由字段不在请求中，配置补查（可选）">
              <el-form label-width="100px">
                <el-form-item label="补查数据源">
                  <el-select v-model="form.lookup.dbConnectionId" clearable placeholder="选择连接" style="width:200px">
                    <el-option v-for="c in dbList" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="查询表名">
                  <el-input v-model="form.lookup.table" placeholder="如 orders" style="width:200px" />
                </el-form-item>
                <el-form-item label="条件列名">
                  <el-input v-model="form.lookup.conditionColumn" placeholder="如 order_id" style="width:200px" />
                </el-form-item>
                <el-form-item label="请求参数key">
                  <el-input v-model="form.lookup.conditionParamKey" placeholder="如 orderId" style="width:200px" />
                </el-form-item>
                <el-form-item label="目标列名">
                  <el-input v-model="form.lookup.targetColumn" placeholder="如 user_id" style="width:200px" />
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 路由预览弹窗 -->
    <el-dialog v-model="previewVisible" title="路由预览" width="480px">
      <div style="margin-bottom:12px">
        <div v-for="(item, idx) in previewParams" :key="idx" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="item.key" placeholder="参数名" style="width:160px" />
          <el-input v-model="item.val" placeholder="参数值" style="width:160px" />
          <el-button size="small" type="danger" link @click="previewParams.splice(idx, 1)">删</el-button>
        </div>
        <el-button size="small" @click="previewParams.push({key:'',val:''})">+ 添加参数</el-button>
      </div>
      <el-button type="primary" :loading="previewing" @click="doPreview">执行预览</el-button>
      <div v-if="previewResult" style="margin-top:16px;padding:12px;background:#f5f7fa;border-radius:4px">
        <div>命中库：<b>{{ previewResult.dbName || previewResult.dbConnectionId }}</b></div>
        <div>命中表：<b>{{ previewResult.tableName }}</b></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listShardConfigs, saveShardConfig, deleteShardConfig, previewShardRoute } from '@/api/shardConfig'
import { listConnections } from '@/api/dbConnection'

const list = ref([])
const loading = ref(false)
const searchName = ref('')
const dbList = ref([])

const formVisible = ref(false)
const saving = ref(false)
const form = ref(emptyForm())

const previewVisible = ref(false)
const previewing = ref(false)
const previewResult = ref(null)
const previewParams = ref([{ key: '', val: '' }])
let previewId = null

function emptyForm() {
  return {
    id: null,
    name: '',
    routingField: '',
    algorithmType: 'MODULO',
    divisor: 16,
    dbSegments: [],
    shards: [],
    lookup: { dbConnectionId: null, table: '', conditionColumn: '', conditionParamKey: '', targetColumn: '' }
  }
}

function algoTag(row) {
  try {
    const rule = JSON.parse(row.shardRule || '{}')
    return rule.algorithm?.type === 'RANGE' ? 'warning' : ''
  } catch { return 'info' }
}

function algoLabel(row) {
  try {
    const rule = JSON.parse(row.shardRule || '{}')
    return rule.algorithm?.type === 'RANGE' ? 'RANGE' : 'MODULO'
  } catch { return '—' }
}

async function loadList() {
  loading.value = true
  try {
    list.value = await listShardConfigs(searchName.value || undefined) ?? []
  } finally {
    loading.value = false
  }
}

async function loadDbList() {
  try {
    dbList.value = await listConnections() ?? []
  } catch {}
}

function openForm(row) {
  form.value = emptyForm()
  if (row) {
    form.value.id = row.id
    form.value.name = row.name
    try {
      const rule = JSON.parse(row.shardRule || '{}')
      form.value.routingField = rule.routingField || ''
      form.value.algorithmType = rule.algorithm?.type || 'MODULO'
      form.value.divisor = rule.algorithm?.divisor || 16
      form.value.dbSegments = rule.dbSegments || []
      form.value.shards = rule.shards || []
      if (rule.fieldLookup) form.value.lookup = { ...rule.fieldLookup }
    } catch {}
  }
  formVisible.value = true
}

function resetForm() {
  form.value = emptyForm()
}

function addSegment() {
  form.value.dbSegments.push({ dbConnectionId: null, tablePrefix: '', indexStart: 0, indexEnd: 0, indexPadding: 0 })
}

function addShard() {
  form.value.shards.push({ rangeStart: '', rangeEnd: '', dbConnectionId: null, tableName: '' })
}

async function handleSave() {
  if (!form.value.name.trim()) { ElMessage.error('请填写配置名称'); return }
  if (!form.value.routingField.trim()) { ElMessage.error('请填写路由字段名'); return }

  const rule = {
    routingField: form.value.routingField,
    algorithm: { type: form.value.algorithmType }
  }
  if (form.value.algorithmType === 'MODULO') {
    rule.algorithm.divisor = form.value.divisor
    rule.dbSegments = form.value.dbSegments
  } else {
    rule.shards = form.value.shards.map(s => ({
      ...s,
      rangeStart: Number(s.rangeStart),
      rangeEnd: Number(s.rangeEnd)
    }))
  }
  const lookup = form.value.lookup
  if (lookup.dbConnectionId) rule.fieldLookup = { ...lookup }

  saving.value = true
  try {
    await saveShardConfig({ id: form.value.id, name: form.value.name, shardRule: JSON.stringify(rule) })
    ElMessage.success(form.value.id ? '更新成功' : '创建成功')
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await deleteShardConfig(row.id)
  ElMessage.success('删除成功')
  await loadList()
}

function openPreview(id) {
  previewId = id
  previewResult.value = null
  previewParams.value = [{ key: '', val: '' }]
  previewVisible.value = true
}

async function doPreview() {
  const params = {}
  previewParams.value.forEach(({ key, val }) => { if (key) params[key] = val })
  previewing.value = true
  try {
    previewResult.value = await previewShardRoute(previewId, params)
  } finally {
    previewing.value = false
  }
}

onMounted(() => {
  loadList()
  loadDbList()
})
</script>

<style scoped>
.shard-config { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
</style>
```

- [ ] **Step 3: 提交**

```bash
git add frontend/src/api/shardConfig.js \
        frontend/src/views/interface/ShardConfig.vue
git commit -m "feat(M2-8): add ShardConfig.vue and shardConfig API"
```

---

## Task 8: 前端路由 + InterfaceList.vue 绑定分片入口

**Files:**
- Modify: `frontend/src/router/index.js`（替换 PlaceholderView）
- Modify: `frontend/src/api/interface.js`（追加 bindShardConfig）
- Modify: `frontend/src/views/interface/InterfaceList.vue`（添加「分片」按钮 + 绑定弹窗）

- [ ] **Step 1: 修改 frontend/src/router/index.js**

将：

```js
        {
          path: 'interface/shard',
          name: 'ShardConfig',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '分库分表配置' }
        },
```

改为：

```js
        {
          path: 'interface/shard',
          name: 'ShardConfig',
          component: () => import('@/views/interface/ShardConfig.vue'),
          meta: { title: '分库分表配置' }
        },
```

- [ ] **Step 2: 在 frontend/src/api/interface.js 末尾追加 bindShardConfig**

```js
/** 绑定/解绑分库分表配置（M2-8），shardConfigId=null 表示解绑 */
export function bindShardConfig(id, shardConfigId) {
  return request.patch(`/interface/${id}/shard-config`, { shardConfigId })
}
```

- [ ] **Step 3: 修改 frontend/src/views/interface/InterfaceList.vue**

**3a. 在 `<el-table-column label="操作"` 的操作列中，`<el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>` 前插入「分片」按钮：**

```html
          <el-button size="small" @click="openShardDialog(row)">分片</el-button>
```

**3b. 在 `</template>` 结束标签前（`<script setup>` 之前）追加绑定分片弹窗：**

```html
  <!-- 绑定分片配置弹窗 -->
  <el-dialog v-model="shardDialogVisible" title="绑定分库分表配置" width="420px">
    <el-form label-width="100px">
      <el-form-item label="分片配置">
        <el-select v-model="shardForm.shardConfigId" clearable placeholder="不启用分片路由"
          style="width:100%" @visible-change="onShardSelectOpen">
          <el-option v-for="s in shardList" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="shardDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="shardSaving" @click="handleBindShard">保存</el-button>
    </template>
  </el-dialog>
```

**3c. 在 `<script setup>` 中追加必要的 import 和响应式变量/方法：**

在 import 区追加：

```js
import { listShardConfigs } from '@/api/shardConfig'
import { bindShardConfig } from '@/api/interface'
```

在 `const total = ref(0)` 后追加：

```js
const shardDialogVisible = ref(false)
const shardSaving = ref(false)
const shardList = ref([])
const shardForm = ref({ interfaceId: null, shardConfigId: null })
```

在 `onMounted(loadList)` 前追加：

```js
function openShardDialog(row) {
  shardForm.value = { interfaceId: row.id, shardConfigId: row.shardConfigId ?? null }
  shardDialogVisible.value = true
}

async function onShardSelectOpen(visible) {
  if (visible && shardList.value.length === 0) {
    shardList.value = await listShardConfigs() ?? []
  }
}

async function handleBindShard() {
  shardSaving.value = true
  try {
    await bindShardConfig(shardForm.value.interfaceId, shardForm.value.shardConfigId)
    ElMessage.success('绑定成功')
    shardDialogVisible.value = false
    await loadList()
  } finally {
    shardSaving.value = false
  }
}
```

- [ ] **Step 4: 提交**

```bash
git add frontend/src/router/index.js \
        frontend/src/api/interface.js \
        frontend/src/views/interface/InterfaceList.vue
git commit -m "feat(M2-8): wire ShardConfig.vue to router, add bind-shard dialog in InterfaceList"
```

---

## Task 9: 全量验证与 git push

- [ ] **Step 1: 运行后端全量测试**

```bash
cd backend && mvn test -q
```

期望：`BUILD SUCCESS`，所有测试通过（原有 241 + M2-8 新增 ≥ 21 个）。

- [ ] **Step 2: 启动前端，手动验收（若前后端均可运行）**

```bash
cd frontend && npm run dev
```

访问 `http://localhost:5173/interface/shard`：
- [ ] 列表页加载正常
- [ ] 点击「新建」，切换取模/范围路由，库段/分片表格动态显示
- [ ] 填写后保存，列表出现新记录
- [ ] 编辑后保存，数据正确回填
- [ ] 删除后记录消失
- [ ] 访问 `http://localhost:5173/interface/list`，操作列有「分片」按钮，点击后弹窗可选择分片配置并保存

- [ ] **Step 3: 更新 CLAUDE.md 项目状态**

将根目录 `CLAUDE.md` 的当前状态行：

```
**当前状态：阶段一全部完成（P0-1 ～ P0-4），阶段二全部完成（M1-1 ～ M1-7），阶段三全部完成（M2-1、M2-2、M2-9、M2-3、M2-4、M2-5、M2-6、M2-7），阶段四 M2-10 完成，共 241 个测试全绿。下一阶段：阶段四剩余（M2-8、SYS-1 ～ SYS-4）。**
```

改为：

```
**当前状态：阶段一全部完成（P0-1 ～ P0-4），阶段二全部完成（M1-1 ～ M1-7），阶段三全部完成（M2-1、M2-2、M2-9、M2-3、M2-4、M2-5、M2-6、M2-7），阶段四 M2-10、M2-8 完成，共 262+ 个测试全绿。下一阶段：阶段四剩余（SYS-1 ～ SYS-4）。**
```

- [ ] **Step 4: 提交并推送**

```bash
git add CLAUDE.md
git commit -m "docs: mark M2-8 as complete, update test count"
git push
```
