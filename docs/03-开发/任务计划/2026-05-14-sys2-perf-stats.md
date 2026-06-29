# SYS-2 性能统计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为所有已发布接口的执行调用统计耗时/成功率，通过 ECharts 图表可视化展示，并配置告警阈值自动写告警记录。

**Architecture:** `@PerfStat` 注解标注在 `ExecController.execute()` 上，`PerfStatAspect` 切面拦截后将统计记录投入 `PerfStatService` 的 `LinkedBlockingQueue`，守护线程异步写 `perf_stat` 表；`PerfAlertJob` 每分钟扫最近1分钟数据，超阈值时写 `perf_alert` 表；前端 `Stats.vue` 用 vue-echarts 展示折线图+柱状图，并提供告警列表和阈值配置弹窗。

**Tech Stack:** Spring Boot AOP、MyBatis-Plus `@Select`、Vue 3 + vue-echarts + Element Plus

---

## 文件地图

**新建（后端）**
| 文件 | 职责 |
|------|------|
| `backend/src/main/java/com/powergateway/aop/PerfStat.java` | `@PerfStat` 注解 |
| `backend/src/main/java/com/powergateway/model/PerfStatRecord.java` | `perf_stat` 表实体 |
| `backend/src/main/java/com/powergateway/model/PerfAlert.java` | `perf_alert` 表实体 |
| `backend/src/main/java/com/powergateway/dao/PerfStatMapper.java` | Mapper（含聚合 @Select） |
| `backend/src/main/java/com/powergateway/dao/PerfAlertMapper.java` | Mapper |
| `backend/src/main/java/com/powergateway/service/PerfStatService.java` | 异步写入 + 图表查询 + 告警列表 |
| `backend/src/main/java/com/powergateway/aop/PerfStatAspect.java` | AOP 切面 |
| `backend/src/main/java/com/powergateway/job/PerfAlertJob.java` | 定时告警检查 |
| `backend/src/main/java/com/powergateway/model/dto/StatsSummaryDTO.java` | 图表数据响应 DTO |
| `backend/src/main/java/com/powergateway/model/dto/AlertConfigRequest.java` | 阈值配置请求 DTO |
| `backend/src/main/java/com/powergateway/controller/StatsController.java` | REST 接口层 |

**修改（后端）**
| 文件 | 改动内容 |
|------|----------|
| `backend/src/main/resources/db/init.sql` | 新增 `perf_stat`、`perf_alert` 表；`sys_config` 追加2条预置值 |
| `backend/src/test/resources/db/init-h2.sql` | 同上（H2 方言，去掉 COMMENT 和 INDEX） |
| `backend/src/main/java/com/powergateway/controller/ExecController.java` | `execute()` 方法加 `@PerfStat` 注解 |

**新建（测试）**
| 文件 | 覆盖点 |
|------|--------|
| `backend/src/test/java/com/powergateway/SYS2PerfStatAspectTest.java` | 切面写 perf_stat（成功=1/失败=0） |
| `backend/src/test/java/com/powergateway/SYS2PerfAlertJobTest.java` | 告警任务写 perf_alert |
| `backend/src/test/java/com/powergateway/SYS2StatsControllerTest.java` | summary 接口结构、alertConfig 更新 |

**新建（前端）**
| 文件 | 职责 |
|------|------|
| `frontend/src/api/stats.js` | 3个接口封装 |
| `frontend/src/views/system/Stats.vue` | 折线图 + 柱状图 + 告警列表 + 配置弹窗 |

**修改（前端）**
| 文件 | 改动 |
|------|------|
| `frontend/src/router/index.js` | `system/stats` 路由替换 PlaceholderView |

---

## Task 1：数据库 Schema

**Files:**
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/test/resources/db/init-h2.sql`

- [ ] **Step 1：在 `init.sql` 末尾追加两张表**

在 `sys_log_history` 表定义之后追加：

```sql
-- SYS-2 性能统计明细表
CREATE TABLE IF NOT EXISTS perf_stat (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  interface_id BIGINT       COMMENT '接口ID，关联 interface_config.id',
  op_type      VARCHAR(32)  COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  cost_ms      INT          COMMENT '耗时（毫秒）',
  success      TINYINT      COMMENT '1=成功 0=失败',
  stat_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_perf_stat_time (stat_time),
  INDEX idx_perf_interface (interface_id)
);

-- SYS-2 告警记录表
CREATE TABLE IF NOT EXISTS perf_alert (
  id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
  alert_type  VARCHAR(64)   COMMENT 'FAIL_RATE / AVG_RESPONSE',
  alert_value DECIMAL(10,2) COMMENT '实际值（失败率%或毫秒）',
  threshold   DECIMAL(10,2) COMMENT '触发时的阈值',
  message     VARCHAR(512),
  check_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  resolved    TINYINT       DEFAULT 0
);
```

- [ ] **Step 2：在 `init.sql` 中为 `sys_config` 追加2条预置值**

将现有 INSERT 块的最后一行（结尾是分号）改为逗号，再追加：

```sql
  ('alert_fail_rate', '5', '告警失败率阈值（百分比，超过此值触发告警）'),
  ('alert_response_ms', '1000', '告警响应时间阈值（毫秒，超过此值触发告警）');
```

- [ ] **Step 3：在 `init-h2.sql` 末尾追加相同两张表（H2 方言）**

H2 不支持 CREATE TABLE 内联 INDEX，去掉 COMMENT 和 INDEX 子句：

```sql
-- SYS-2 性能统计明细表（H2）
CREATE TABLE perf_stat (
  id           BIGINT        PRIMARY KEY AUTO_INCREMENT,
  interface_id BIGINT,
  op_type      VARCHAR(32),
  cost_ms      INT,
  success      TINYINT,
  stat_time    DATETIME      DEFAULT CURRENT_TIMESTAMP
);

-- SYS-2 告警记录表（H2）
CREATE TABLE perf_alert (
  id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
  alert_type  VARCHAR(64),
  alert_value DECIMAL(10,2),
  threshold   DECIMAL(10,2),
  message     VARCHAR(512),
  check_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  resolved    TINYINT       DEFAULT 0
);
```

- [ ] **Step 4：在 `init-h2.sql` 中追加2条 sys_config 预置值**

找到现有 sys_config INSERT 块末尾，同 Step 2 追加：

```sql
  ('alert_fail_rate', '5', '告警失败率阈值（百分比）'),
  ('alert_response_ms', '1000', '告警响应时间阈值（毫秒）');
```

- [ ] **Step 5：验证 SQL 文件语法**

```bash
cd backend
mvn test -Dtest=PowergatewayApplicationTests -q
```

期望输出：`BUILD SUCCESS`（H2 初始化脚本无语法错误）

- [ ] **Step 6：提交**

```bash
git add backend/src/main/resources/db/init.sql backend/src/test/resources/db/init-h2.sql
git commit -m "feat(SYS-2): add perf_stat and perf_alert tables to schema"
```

---

## Task 2：实体类 + Mapper

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/PerfStatRecord.java`
- Create: `backend/src/main/java/com/powergateway/model/PerfAlert.java`
- Create: `backend/src/main/java/com/powergateway/dao/PerfStatMapper.java`
- Create: `backend/src/main/java/com/powergateway/dao/PerfAlertMapper.java`

- [ ] **Step 1：创建 `PerfStatRecord.java`**

```java
package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("perf_stat")
public class PerfStatRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long interfaceId;
    private String opType;
    private Integer costMs;
    private Integer success;
    private LocalDateTime statTime;
}
```

- [ ] **Step 2：创建 `PerfAlert.java`**

```java
package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("perf_alert")
public class PerfAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertType;
    private BigDecimal alertValue;
    private BigDecimal threshold;
    private String message;
    private LocalDateTime checkTime;
    private Integer resolved;
}
```

- [ ] **Step 3：创建 `PerfStatMapper.java`**

```java
package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.PerfStatRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PerfStatMapper extends BaseMapper<PerfStatRecord> {

    @Select("SELECT DATE_FORMAT(stat_time, '%H:00') AS label, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount, " +
            "ROUND(COALESCE(AVG(cost_ms), 0), 0) AS avgCostMs " +
            "FROM perf_stat WHERE stat_time >= #{from} AND stat_time < #{to} " +
            "GROUP BY DATE_FORMAT(stat_time, '%H:00') " +
            "ORDER BY DATE_FORMAT(stat_time, '%H:00')")
    List<Map<String, Object>> groupByHour(@Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    @Select("SELECT DATE_FORMAT(stat_time, '%Y-%m-%d') AS label, " +
            "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCount, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount, " +
            "ROUND(COALESCE(AVG(cost_ms), 0), 0) AS avgCostMs " +
            "FROM perf_stat WHERE stat_time >= #{from} AND stat_time < #{to} " +
            "GROUP BY DATE_FORMAT(stat_time, '%Y-%m-%d') " +
            "ORDER BY DATE_FORMAT(stat_time, '%Y-%m-%d')")
    List<Map<String, Object>> groupByDay(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) AS failCount, " +
            "COALESCE(AVG(cost_ms), 0) AS avgMs " +
            "FROM perf_stat WHERE stat_time >= #{from} AND stat_time < #{to}")
    Map<String, Object> statBetween(@Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
```

- [ ] **Step 4：创建 `PerfAlertMapper.java`**

```java
package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.PerfAlert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PerfAlertMapper extends BaseMapper<PerfAlert> {
}
```

- [ ] **Step 5：编译验证**

```bash
cd backend
mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 6：提交**

```bash
git add backend/src/main/java/com/powergateway/model/PerfStatRecord.java \
        backend/src/main/java/com/powergateway/model/PerfAlert.java \
        backend/src/main/java/com/powergateway/dao/PerfStatMapper.java \
        backend/src/main/java/com/powergateway/dao/PerfAlertMapper.java
git commit -m "feat(SYS-2): add PerfStatRecord, PerfAlert entities and mappers"
```

---

## Task 3：PerfStatService + DTO

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/dto/StatsSummaryDTO.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/AlertConfigRequest.java`
- Create: `backend/src/main/java/com/powergateway/service/PerfStatService.java`

- [ ] **Step 1：创建 `StatsSummaryDTO.java`**

```java
package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class StatsSummaryDTO {
    private List<String> timeline;
    private List<Long> successCounts;
    private List<Long> failCounts;
    private List<Long> avgCostMs;
}
```

- [ ] **Step 2：创建 `AlertConfigRequest.java`**

```java
package com.powergateway.model.dto;

import lombok.Data;

@Data
public class AlertConfigRequest {
    private Double failRate;
    private Integer responseMs;
}
```

- [ ] **Step 3：创建 `PerfStatService.java`**

```java
package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.dto.StatsSummaryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PerfStatService {

    private static final int QUEUE_CAPACITY = 10000;
    private final LinkedBlockingQueue<PerfStatRecord> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;

    @PostConstruct
    public void startConsumer() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    writeSafely(queue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "perf-stat-consumer");
        t.setDaemon(true);
        t.start();
    }

    public void enqueue(PerfStatRecord record) {
        queue.offer(record);
    }

    /** 仅供测试使用，严禁在生产代码中调用 */
    public void flushForTest() {
        List<PerfStatRecord> pending = new ArrayList<>();
        queue.drainTo(pending);
        pending.forEach(this::writeSafely);
    }

    public StatsSummaryDTO summary(String dimension) {
        LocalDateTime to = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime from;
        boolean byHour;
        switch (dimension != null ? dimension : "today") {
            case "week":
                from = LocalDate.now().minusDays(6).atStartOfDay();
                byHour = false;
                break;
            case "month":
                from = LocalDate.now().minusDays(29).atStartOfDay();
                byHour = false;
                break;
            default:
                from = LocalDate.now().atStartOfDay();
                byHour = true;
        }

        List<Map<String, Object>> rows = byHour
                ? perfStatMapper.groupByHour(from, to)
                : perfStatMapper.groupByDay(from, to);

        List<String> timeline = new ArrayList<>();
        List<Long> successCounts = new ArrayList<>();
        List<Long> failCounts = new ArrayList<>();
        List<Long> avgCostMs = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            timeline.add(getStr(row, "label"));
            successCounts.add(getLong(row, "successCount"));
            failCounts.add(getLong(row, "failCount"));
            avgCostMs.add(getLong(row, "avgCostMs"));
        }

        StatsSummaryDTO dto = new StatsSummaryDTO();
        dto.setTimeline(timeline);
        dto.setSuccessCounts(successCounts);
        dto.setFailCounts(failCounts);
        dto.setAvgCostMs(avgCostMs);
        return dto;
    }

    public IPage<PerfAlert> listAlerts(int page, int pageSize) {
        return perfAlertMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<PerfAlert>().orderByDesc(PerfAlert::getCheckTime));
    }

    public Map<String, Object> statBetween(LocalDateTime from, LocalDateTime to) {
        return perfStatMapper.statBetween(from, to);
    }

    private void writeSafely(PerfStatRecord record) {
        try {
            perfStatMapper.insert(record);
        } catch (Exception ignored) {
        }
    }

    /** 大小写兼容的 Map 字符串读取（H2 返回大写别名，MySQL 返回小写） */
    private String getStr(Map<String, Object> row, String alias) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias))
                return e.getValue() != null ? String.valueOf(e.getValue()) : "";
        }
        return "";
    }

    private long getLong(Map<String, Object> row, String alias) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias) && e.getValue() instanceof Number)
                return ((Number) e.getValue()).longValue();
        }
        return 0L;
    }
}
```

- [ ] **Step 4：编译验证**

```bash
cd backend
mvn compile -q
```

期望：`BUILD SUCCESS`

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/powergateway/model/dto/StatsSummaryDTO.java \
        backend/src/main/java/com/powergateway/model/dto/AlertConfigRequest.java \
        backend/src/main/java/com/powergateway/service/PerfStatService.java
git commit -m "feat(SYS-2): add PerfStatService with async queue and summary query"
```

---

## Task 4：@PerfStat 注解 + PerfStatAspect（TDD）

**Files:**
- Create: `backend/src/main/java/com/powergateway/aop/PerfStat.java`
- Create: `backend/src/main/java/com/powergateway/aop/PerfStatAspect.java`
- Modify: `backend/src/main/java/com/powergateway/controller/ExecController.java`
- Test: `backend/src/test/java/com/powergateway/SYS2PerfStatAspectTest.java`

- [ ] **Step 1：写失败测试 `SYS2PerfStatAspectTest.java`**

```java
package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.service.PerfStatService;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-2 PerfStatAspect AOP 切面测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS2PerfStatAspectTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfStatService perfStatService;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;
    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    @BeforeAll
    void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS sys2_test_table");
            stmt.execute("CREATE TABLE sys2_test_table (id BIGINT AUTO_INCREMENT PRIMARY KEY, val VARCHAR(64))");
            stmt.execute("INSERT INTO sys2_test_table(val) VALUES('A'),('B')");
        }

        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk()).andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        DbConnection db = new DbConnection();
        db.setName("SYS2_H2_" + System.currentTimeMillis());
        db.setDbType("MySQL");
        db.setUrl(H2_URL);
        db.setUsername("sa");
        db.setPassword(AesUtil.encrypt("", AES_KEY));
        db.setEnv("dev");
        dbConnectionMapper.insert(db);
        testDbId = db.getId();
    }

    @AfterAll
    void cleanup() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .eq(PerfStatRecord::getOpType, "SELECT"));
    }

    private Long saveAndPublishSelect() throws Exception {
        String configJson = "{\"tables\":[{\"name\":\"sys2_test_table\",\"alias\":\"t\"}]," +
                "\"fields\":[{\"table\":\"t\",\"column\":\"id\",\"alias\":\"id\"}]," +
                "\"conditions\":[],\"joins\":[]}";
        Map<String, Object> req = new HashMap<>();
        req.put("name", "SYS2_SELECT_" + System.currentTimeMillis());
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
                .header("satoken", token));
        return id;
    }

    @Test
    @Order(1)
    void 执行成功_切面写入success为1的记录() throws Exception {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .eq(PerfStatRecord::getOpType, "SELECT"));

        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk());

        perfStatService.flushForTest();

        List<PerfStatRecord> records = perfStatMapper.selectList(
                new LambdaQueryWrapper<PerfStatRecord>()
                        .eq(PerfStatRecord::getInterfaceId, id));
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSuccess()).isEqualTo(1);
        assertThat(records.get(0).getCostMs()).isGreaterThanOrEqualTo(0);
        assertThat(records.get(0).getOpType()).isEqualTo("SELECT");
    }

    @Test
    @Order(2)
    void 执行禁用接口_切面写入success为0的记录() throws Exception {
        Long id = saveAndPublishSelect();
        mockMvc.perform(post("/api/interface/" + id + "/disable")
                .header("satoken", token));

        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .eq(PerfStatRecord::getInterfaceId, id));

        mockMvc.perform(post("/api/exec/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"params\":{}}"))
                .andExpect(status().isOk());

        perfStatService.flushForTest();

        List<PerfStatRecord> records = perfStatMapper.selectList(
                new LambdaQueryWrapper<PerfStatRecord>()
                        .eq(PerfStatRecord::getInterfaceId, id));
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).getSuccess()).isEqualTo(0);
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
cd backend
mvn test -Dtest=SYS2PerfStatAspectTest -q 2>&1 | tail -20
```

期望：`BUILD FAILURE`，错误信息含 `PerfStat` 或 `PerfStatAspect` 未找到

- [ ] **Step 3：创建 `PerfStat.java` 注解**

```java
package com.powergateway.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PerfStat {
}
```

- [ ] **Step 4：创建 `PerfStatAspect.java`**

```java
package com.powergateway.aop;

import com.powergateway.common.Result;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.service.PerfStatService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
public class PerfStatAspect {

    @Autowired private PerfStatService perfStatService;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    @Around("@annotation(com.powergateway.aop.PerfStat)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long startMs = System.currentTimeMillis();
        Object[] args = pjp.getArgs();
        Long interfaceId = args.length > 0 && args[0] instanceof Long ? (Long) args[0] : null;

        InterfaceConfig config = interfaceId != null
                ? interfaceConfigMapper.selectById(interfaceId) : null;
        String opType = config != null ? config.getType() : "UNKNOWN";

        try {
            Object result = pjp.proceed();
            int success = 1;
            if (result instanceof Result && ((Result<?>) result).getCode() != 200) {
                success = 0;
            }
            enqueue(interfaceId, opType, (int) (System.currentTimeMillis() - startMs), success);
            return result;
        } catch (Throwable t) {
            enqueue(interfaceId, opType, (int) (System.currentTimeMillis() - startMs), 0);
            throw t;
        }
    }

    private void enqueue(Long interfaceId, String opType, int costMs, int success) {
        PerfStatRecord record = new PerfStatRecord();
        record.setInterfaceId(interfaceId);
        record.setOpType(opType);
        record.setCostMs(costMs);
        record.setSuccess(success);
        record.setStatTime(LocalDateTime.now());
        perfStatService.enqueue(record);
    }
}
```

- [ ] **Step 5：在 `ExecController.java` 的 `execute()` 方法上加 `@PerfStat`**

在现有 `@PostMapping("/{interfaceId}")` 注解所在方法的上方，加一行：

```java
@PerfStat
@PostMapping("/{interfaceId}")
@Operation(summary = "执行已发布接口（SELECT/INSERT/UPDATE/DELETE）")
public Result<?> execute(@PathVariable Long interfaceId,
                         @RequestBody(required = false) ExecRequest req) {
```

同时在文件顶部 import 块中加：

```java
import com.powergateway.aop.PerfStat;
```

- [ ] **Step 6：运行测试，确认通过**

```bash
cd backend
mvn test -Dtest=SYS2PerfStatAspectTest -q 2>&1 | tail -10
```

期望：`BUILD SUCCESS`，2 tests passed

- [ ] **Step 7：提交**

```bash
git add backend/src/main/java/com/powergateway/aop/PerfStat.java \
        backend/src/main/java/com/powergateway/aop/PerfStatAspect.java \
        backend/src/main/java/com/powergateway/controller/ExecController.java \
        backend/src/test/java/com/powergateway/SYS2PerfStatAspectTest.java
git commit -m "feat(SYS-2): add @PerfStat annotation and PerfStatAspect AOP interceptor"
```

---

## Task 5：PerfAlertJob（TDD）

**Files:**
- Create: `backend/src/main/java/com/powergateway/job/PerfAlertJob.java`
- Test: `backend/src/test/java/com/powergateway/SYS2PerfAlertJobTest.java`

- [ ] **Step 1：写失败测试 `SYS2PerfAlertJobTest.java`**

```java
package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.job.PerfAlertJob;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.SysConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-2 PerfAlertJob 告警检查测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS2PerfAlertJobTest {

    @Autowired private PerfAlertJob perfAlertJob;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @BeforeEach
    void clearTestData() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>()
                .isNotNull(PerfAlert::getId));
    }

    private void insertStat(int success, int costMs) {
        PerfStatRecord r = new PerfStatRecord();
        r.setInterfaceId(999L);
        r.setOpType("SELECT");
        r.setCostMs(costMs);
        r.setSuccess(success);
        r.setStatTime(LocalDateTime.now());
        perfStatMapper.insert(r);
    }

    @Test
    @Order(1)
    void 失败率超阈值_写入FAIL_RATE告警() {
        // 设置阈值 5%
        SysConfig cfg = sysConfigMapper.selectById("alert_fail_rate");
        cfg.setConfigValue("5");
        sysConfigMapper.updateById(cfg);

        // 插入 10 条记录，4条失败（40%，超过5%阈值）
        for (int i = 0; i < 6; i++) insertStat(1, 50);
        for (int i = 0; i < 4; i++) insertStat(0, 50);

        perfAlertJob.checkAndAlert();

        List<PerfAlert> alerts = perfAlertMapper.selectList(
                new LambdaQueryWrapper<PerfAlert>().eq(PerfAlert::getAlertType, "FAIL_RATE"));
        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0).getAlertValue().doubleValue()).isGreaterThan(5.0);
    }

    @Test
    @Order(2)
    void 平均响应时间超阈值_写入AVG_RESPONSE告警() {
        // 设置阈值 100ms
        SysConfig cfg = sysConfigMapper.selectById("alert_response_ms");
        cfg.setConfigValue("100");
        sysConfigMapper.updateById(cfg);

        // 插入5条，平均 500ms，超过100ms阈值
        for (int i = 0; i < 5; i++) insertStat(1, 500);

        perfAlertJob.checkAndAlert();

        List<PerfAlert> alerts = perfAlertMapper.selectList(
                new LambdaQueryWrapper<PerfAlert>().eq(PerfAlert::getAlertType, "AVG_RESPONSE"));
        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0).getAlertValue().doubleValue()).isGreaterThan(100.0);
    }

    @Test
    @Order(3)
    void 无数据时_不写告警() {
        perfAlertJob.checkAndAlert();
        long count = perfAlertMapper.selectCount(null);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @Order(4)
    void 指标正常时_不写告警() {
        for (int i = 0; i < 10; i++) insertStat(1, 50);

        perfAlertJob.checkAndAlert();

        long count = perfAlertMapper.selectCount(null);
        assertThat(count).isEqualTo(0);
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
cd backend
mvn test -Dtest=SYS2PerfAlertJobTest -q 2>&1 | tail -10
```

期望：`BUILD FAILURE`（`PerfAlertJob` 未创建）

- [ ] **Step 3：创建 `PerfAlertJob.java`**

```java
package com.powergateway.job;

import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.SysConfig;
import com.powergateway.service.PerfStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class PerfAlertJob {

    private static final String KEY_FAIL_RATE   = "alert_fail_rate";
    private static final String KEY_RESPONSE_MS  = "alert_response_ms";
    private static final double DEFAULT_FAIL_RATE   = 5.0;
    private static final int    DEFAULT_RESPONSE_MS  = 1000;

    @Autowired private PerfStatService perfStatService;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void scheduled() {
        checkAndAlert();
    }

    /** 公开方法，供测试直接触发检查 */
    public void checkAndAlert() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusMinutes(1);

        Map<String, Object> stat = perfStatService.statBetween(from, to);
        if (stat == null) return;

        Object totalObj = getVal(stat, "total");
        if (totalObj == null) return;
        long total = ((Number) totalObj).longValue();
        if (total == 0) return;

        double failRate    = readDouble(KEY_FAIL_RATE, DEFAULT_FAIL_RATE);
        int    responseMs  = readInt(KEY_RESPONSE_MS, DEFAULT_RESPONSE_MS);

        Object failObj = getVal(stat, "failCount");
        Object avgObj  = getVal(stat, "avgMs");
        long failCount  = failObj instanceof Number ? ((Number) failObj).longValue() : 0L;
        double avgMs    = avgObj  instanceof Number ? ((Number) avgObj).doubleValue() : 0.0;

        double actualFailRate = (double) failCount / total * 100;

        if (actualFailRate > failRate) {
            insertAlert("FAIL_RATE",
                    BigDecimal.valueOf(actualFailRate).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(failRate),
                    String.format("失败率 %.2f%% 超过阈值 %.2f%%", actualFailRate, failRate));
        }
        if (avgMs > responseMs) {
            insertAlert("AVG_RESPONSE",
                    BigDecimal.valueOf(avgMs).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(responseMs),
                    String.format("平均响应时间 %.0fms 超过阈值 %dms", avgMs, responseMs));
        }
    }

    private void insertAlert(String type, BigDecimal value, BigDecimal threshold, String message) {
        PerfAlert alert = new PerfAlert();
        alert.setAlertType(type);
        alert.setAlertValue(value);
        alert.setThreshold(threshold);
        alert.setMessage(message);
        alert.setCheckTime(LocalDateTime.now());
        alert.setResolved(0);
        perfAlertMapper.insert(alert);
    }

    private double readDouble(String key, double defaultVal) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg == null || cfg.getConfigValue() == null) return defaultVal;
        try { return Double.parseDouble(cfg.getConfigValue().trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private int readInt(String key, int defaultVal) {
        SysConfig cfg = sysConfigMapper.selectById(key);
        if (cfg == null || cfg.getConfigValue() == null) return defaultVal;
        try { return Integer.parseInt(cfg.getConfigValue().trim()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private Object getVal(Map<String, Object> map, String alias) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(alias)) return e.getValue();
        }
        return null;
    }
}
```

- [ ] **Step 4：运行测试，确认通过**

```bash
cd backend
mvn test -Dtest=SYS2PerfAlertJobTest -q 2>&1 | tail -10
```

期望：`BUILD SUCCESS`，4 tests passed

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/powergateway/job/PerfAlertJob.java \
        backend/src/test/java/com/powergateway/SYS2PerfAlertJobTest.java
git commit -m "feat(SYS-2): add PerfAlertJob with per-minute alert checks"
```

---

## Task 6：StatsController（TDD）

**Files:**
- Create: `backend/src/main/java/com/powergateway/controller/StatsController.java`
- Test: `backend/src/test/java/com/powergateway/SYS2StatsControllerTest.java`

- [ ] **Step 1：写失败测试 `SYS2StatsControllerTest.java`**

```java
package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.SysConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-2 StatsController 接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS2StatsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    private String token;

    @BeforeAll
    void setup() throws Exception {
        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        // 清理并插入测试数据
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>().isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>().isNotNull(PerfAlert::getId));

        for (int i = 0; i < 3; i++) {
            PerfStatRecord r = new PerfStatRecord();
            r.setInterfaceId(1L);
            r.setOpType("SELECT");
            r.setCostMs(100 + i * 50);
            r.setSuccess(1);
            r.setStatTime(LocalDateTime.now());
            perfStatMapper.insert(r);
        }

        PerfAlert alert = new PerfAlert();
        alert.setAlertType("FAIL_RATE");
        alert.setAlertValue(new BigDecimal("10.00"));
        alert.setThreshold(new BigDecimal("5.00"));
        alert.setMessage("测试告警");
        alert.setCheckTime(LocalDateTime.now());
        alert.setResolved(0);
        perfAlertMapper.insert(alert);
    }

    @AfterAll
    void cleanup() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>().isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>().isNotNull(PerfAlert::getId));
    }

    @Test
    @Order(1)
    void summary接口_today维度_返回正确结构() throws Exception {
        mockMvc.perform(get("/api/stats/summary")
                .header("satoken", token)
                .param("dimension", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.timeline").isArray())
                .andExpect(jsonPath("$.data.successCounts").isArray())
                .andExpect(jsonPath("$.data.failCounts").isArray())
                .andExpect(jsonPath("$.data.avgCostMs").isArray());
    }

    @Test
    @Order(2)
    void summary接口_今日有数据_timeline非空() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/stats/summary")
                .header("satoken", token)
                .param("dimension", "today"))
                .andReturn();
        int timelineSize = ((java.util.List<?>) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.timeline")).size();
        assertThat(timelineSize).isGreaterThan(0);
    }

    @Test
    @Order(3)
    void alerts接口_返回分页告警列表() throws Exception {
        mockMvc.perform(get("/api/stats/alerts")
                .header("satoken", token)
                .param("page", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(4)
    void alertConfig接口_更新阈值后sys_config已修改() throws Exception {
        mockMvc.perform(put("/api/stats/alert-config")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"failRate\":10,\"responseMs\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        SysConfig failRateCfg = sysConfigMapper.selectById("alert_fail_rate");
        SysConfig responseCfg = sysConfigMapper.selectById("alert_response_ms");
        assertThat(failRateCfg.getConfigValue()).isEqualTo("10.0");
        assertThat(responseCfg.getConfigValue()).isEqualTo("2000");
    }
}
```

- [ ] **Step 2：运行测试，确认失败**

```bash
cd backend
mvn test -Dtest=SYS2StatsControllerTest -q 2>&1 | tail -10
```

期望：`BUILD FAILURE`（`StatsController` 未创建）

- [ ] **Step 3：创建 `StatsController.java`**

```java
package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.SysConfig;
import com.powergateway.model.dto.AlertConfigRequest;
import com.powergateway.model.dto.StatsSummaryDTO;
import com.powergateway.service.PerfStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "性能统计", description = "接口执行统计、告警查询与阈值配置（SYS-2）")
public class StatsController {

    @Autowired private PerfStatService perfStatService;
    @Autowired private SysConfigMapper sysConfigMapper;

    @GetMapping("/summary")
    @Operation(summary = "获取图表聚合数据（today/week/month）")
    public Result<StatsSummaryDTO> summary(
            @RequestParam(defaultValue = "today") String dimension) {
        return Result.success(perfStatService.summary(dimension));
    }

    @GetMapping("/alerts")
    @Operation(summary = "分页查询告警记录")
    public Result<IPage<PerfAlert>> alerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(perfStatService.listAlerts(page, pageSize));
    }

    @PutMapping("/alert-config")
    @Operation(summary = "更新告警阈值配置")
    public Result<Void> updateAlertConfig(@RequestBody AlertConfigRequest req) {
        if (req.getFailRate() != null) {
            upsertConfig("alert_fail_rate", String.valueOf(req.getFailRate()));
        }
        if (req.getResponseMs() != null) {
            upsertConfig("alert_response_ms", String.valueOf(req.getResponseMs()));
        }
        return Result.success();
    }

    private void upsertConfig(String key, String value) {
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
    }
}
```

- [ ] **Step 4：运行测试，确认通过**

```bash
cd backend
mvn test -Dtest=SYS2StatsControllerTest -q 2>&1 | tail -10
```

期望：`BUILD SUCCESS`，4 tests passed

- [ ] **Step 5：运行全量测试，确认无回归**

```bash
cd backend
mvn test -q 2>&1 | tail -10
```

期望：`BUILD SUCCESS`，所有测试通过（282+ tests）

- [ ] **Step 6：提交**

```bash
git add backend/src/main/java/com/powergateway/controller/StatsController.java \
        backend/src/test/java/com/powergateway/SYS2StatsControllerTest.java
git commit -m "feat(SYS-2): add StatsController (summary/alerts/alert-config)"
```

---

## Task 7：前端 Stats.vue + 路由接入

**Files:**
- Create: `frontend/src/api/stats.js`
- Create: `frontend/src/views/system/Stats.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1：创建 `frontend/src/api/stats.js`**

```js
import request from '@/api/request'

export const getStatsSummary = (dimension) =>
  request.get('/stats/summary', { params: { dimension } })

export const getAlerts = (page, pageSize) =>
  request.get('/stats/alerts', { params: { page, pageSize } })

export const updateAlertConfig = (data) =>
  request.put('/stats/alert-config', data)
```

- [ ] **Step 2：创建 `frontend/src/views/system/Stats.vue`**

```vue
<template>
  <div class="stats-page" style="padding: 20px">

    <!-- 图表区 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>接口调用趋势</span>
          <el-radio-group v-model="dimension" @change="loadSummary">
            <el-radio-button label="today">今天</el-radio-button>
            <el-radio-button label="week">本周</el-radio-button>
            <el-radio-button label="month">本月</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <div style="display:flex;gap:20px" v-loading="summaryLoading">
        <v-chart :option="lineOption" style="height:280px;flex:1" autoresize />
        <v-chart :option="barOption"  style="height:280px;flex:1" autoresize />
      </div>
    </el-card>

    <!-- 告警列表 + 配置按钮 -->
    <el-card>
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>告警记录</span>
          <el-button type="warning" @click="configVisible = true">配置告警阈值</el-button>
        </div>
      </template>

      <el-table :data="alertList" stripe border v-loading="alertLoading" style="margin-bottom:14px">
        <el-table-column prop="alertType"  label="告警类型"  width="140">
          <template #default="{ row }">
            <el-tag :type="row.alertType === 'FAIL_RATE' ? 'danger' : 'warning'" size="small">
              {{ row.alertType === 'FAIL_RATE' ? '失败率' : '响应时间' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertValue" label="实际值"   width="100" />
        <el-table-column prop="threshold"  label="阈值"     width="100" />
        <el-table-column prop="message"    label="消息"     min-width="200" show-overflow-tooltip />
        <el-table-column prop="checkTime"  label="检查时间" width="175" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'danger'" size="small">
              {{ row.resolved ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="alertPage"
        :page-size="alertPageSize"
        :total="alertTotal"
        layout="total, prev, pager, next"
        style="text-align:right"
        @current-change="loadAlerts"
      />
    </el-card>

    <!-- 告警阈值配置弹窗 -->
    <el-dialog v-model="configVisible" title="告警阈值配置" width="420px" :close-on-click-modal="false">
      <el-form :model="configForm" label-width="130px">
        <el-form-item label="失败率阈值 (%)">
          <el-input-number v-model="configForm.failRate" :min="1" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="响应时间阈值 (ms)">
          <el-input-number v-model="configForm.responseMs" :min="100" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getStatsSummary, getAlerts, updateAlertConfig } from '@/api/stats'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const dimension     = ref('today')
const summaryLoading = ref(false)
const summary       = ref({ timeline: [], successCounts: [], failCounts: [], avgCostMs: [] })

const alertList     = ref([])
const alertLoading  = ref(false)
const alertPage     = ref(1)
const alertPageSize = ref(10)
const alertTotal    = ref(0)

const configVisible = ref(false)
const configSaving  = ref(false)
const configForm    = ref({ failRate: 5, responseMs: 1000 })

const lineOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['成功次数', '失败次数'] },
  xAxis: { type: 'category', data: summary.value.timeline },
  yAxis: { type: 'value', name: '次数' },
  series: [
    { name: '成功次数', type: 'line', data: summary.value.successCounts, smooth: true,
      itemStyle: { color: '#67C23A' } },
    { name: '失败次数', type: 'line', data: summary.value.failCounts, smooth: true,
      itemStyle: { color: '#F56C6C' } }
  ]
}))

const barOption = computed(() => ({
  tooltip: { trigger: 'axis', formatter: (p) => `${p[0].name}<br/>平均耗时：${p[0].value} ms` },
  xAxis: { type: 'category', data: summary.value.timeline },
  yAxis: { type: 'value', name: '耗时 (ms)' },
  series: [
    { name: '平均响应时间', type: 'bar', data: summary.value.avgCostMs,
      itemStyle: { color: '#409EFF' } }
  ]
}))

async function loadSummary() {
  summaryLoading.value = true
  try {
    summary.value = await getStatsSummary(dimension.value)
  } finally {
    summaryLoading.value = false
  }
}

async function loadAlerts() {
  alertLoading.value = true
  try {
    const page = await getAlerts(alertPage.value, alertPageSize.value)
    alertList.value  = page.records
    alertTotal.value = page.total
  } finally {
    alertLoading.value = false
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    await updateAlertConfig(configForm.value)
    ElMessage.success('配置已更新')
    configVisible.value = false
  } finally {
    configSaving.value = false
  }
}

onMounted(() => {
  loadSummary()
  loadAlerts()
})
</script>
```

- [ ] **Step 3：修改 `frontend/src/router/index.js`，接入 Stats.vue**

将以下内容：

```js
        {
          path: 'system/stats',
          name: 'PerfStats',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '性能统计' }
        },
```

替换为：

```js
        {
          path: 'system/stats',
          name: 'PerfStats',
          component: () => import('@/views/system/Stats.vue'),
          meta: { title: '性能统计' }
        },
```

- [ ] **Step 4：启动后端，启动前端，浏览器验证**

```bash
# 终端1：后端
cd backend && mvn spring-boot:run

# 终端2：前端
cd frontend && npm run dev
```

1. 浏览器访问 `http://localhost:5173`，登录 `admin / Admin@123`
2. 侧边栏点击「性能统计」
3. 执行几次接口调用（可用 Postman 调 `/api/exec/{id}`）
4. 刷新页面，确认折线图和柱状图有数据
5. 点击「配置告警阈值」，修改值，点保存，确认 `ElMessage.success` 出现
6. 告警列表在 `perf_alert` 有数据时正确显示

- [ ] **Step 5：提交**

```bash
git add frontend/src/api/stats.js \
        frontend/src/views/system/Stats.vue \
        frontend/src/router/index.js
git commit -m "feat(SYS-2): add Stats.vue with ECharts charts, alert list and config dialog"
```

---

## 自查清单（实施者执行）

| 验收标准 | 覆盖任务 |
|---------|---------|
| 执行10次查询后统计页图表有数据 | Task 4（切面写入）+ Task 7（前端图表） |
| 修改告警阈值后下次检查生效 | Task 5（Job 读 sys_config）+ Task 6（Controller 写 sys_config） |
| 成功调用 success=1，失败调用 success=0 | Task 4 测试 Order 1/2 |
| 失败率/响应时间超阈值写 perf_alert | Task 5 测试 Order 1/2 |
| 正常指标不触发告警 | Task 5 测试 Order 3/4 |
| summary 接口返回 timeline/successCounts/failCounts/avgCostMs | Task 6 测试 Order 1 |
| 全量测试无回归 | Task 6 Step 5 |
