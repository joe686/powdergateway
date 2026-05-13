# SYS-1 日志管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现系统操作日志的写入（@SysLogRecord + AOP）、查询/导出、历史归档，以及前端双 Tab 日志管理页面。

**Architecture:** `@SysLogRecord` 注解标注 ~20 处 Controller 写方法，`SysLogAspect` 拦截后将 `SysLog` 对象投入 `LinkedBlockingQueue`，守护线程异步写 `sys_log` 表，与 M2-9 `@AuditLog` 模式完全一致。`SysLogArchiveJob` 每天凌晨将超过留存期的记录 INSERT 到 `sys_log_history` 再 DELETE，前端提供「查历史数据」开关切换查询目标。

**Tech Stack:** Spring Boot 2.7 / MyBatis-Plus 3.5 / Apache POI / Sa-Token / Vue 3 / Element Plus

**注意：注解类命名为 `SysLogRecord` 以避免与实体类 `SysLog` 同名冲突。**

---

## 文件清单

| 动作 | 文件 |
|------|------|
| Modify | `backend/src/main/resources/db/init.sql` |
| Modify | `backend/src/test/resources/db/init-h2.sql` |
| Create | `backend/src/main/java/com/powergateway/model/SysLog.java` |
| Create | `backend/src/main/java/com/powergateway/model/SysLogHistory.java` |
| Create | `backend/src/main/java/com/powergateway/dao/SysLogMapper.java` |
| Create | `backend/src/main/java/com/powergateway/dao/SysLogHistoryMapper.java` |
| Create | `backend/src/main/java/com/powergateway/aop/SysLogRecord.java` |
| Create | `backend/src/main/java/com/powergateway/aop/SysLogAspect.java` |
| Create | `backend/src/main/java/com/powergateway/service/SysLogService.java` |
| Create | `backend/src/main/java/com/powergateway/controller/SysLogController.java` |
| Create | `backend/src/main/java/com/powergateway/job/SysLogArchiveJob.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/AuthController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/TemplateController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/ChannelConfigController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/PortRouteController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/DbConnectionController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/UserController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/ShardConfigController.java` |
| Modify | `backend/src/main/java/com/powergateway/controller/CacheController.java` |
| Create | `backend/src/test/java/com/powergateway/SYS1SysLogServiceTest.java` |
| Create | `backend/src/test/java/com/powergateway/SYS1SysLogAopTest.java` |
| Create | `backend/src/test/java/com/powergateway/SYS1SysLogControllerTest.java` |
| Create | `backend/src/test/java/com/powergateway/SYS1SysLogArchiveJobTest.java` |
| Create | `backend/src/test/java/com/powergateway/SYS1AuditLogQueryTest.java` |
| Create | `frontend/src/api/log.js` |
| Create | `frontend/src/views/system/LogList.vue` |
| Modify | `frontend/src/router/index.js` |

---

### Task 1: Schema — 新增两张日志表 + sys_config 默认值

**Files:**
- Modify: `backend/src/main/resources/db/init.sql`
- Modify: `backend/src/test/resources/db/init-h2.sql`

- [ ] **Step 1: 在 `init.sql` 末尾追加两张新表 DDL 和 sys_config 默认值**

在文件最后（`sql_audit_log` 建表语句之后）追加：

```sql
-- SYS-1 操作日志表（配置库）
CREATE TABLE IF NOT EXISTS sys_log (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  module    VARCHAR(64)  COMMENT '操作模块（如"模板管理"）',
  action    VARCHAR(128) COMMENT '操作动作（如"保存模板"）',
  operator  VARCHAR(64)  COMMENT '操作人（Sa-Token loginId，未登录时为 system）',
  op_ip     VARCHAR(64),
  op_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  level     VARCHAR(16)  COMMENT 'INFO / ERROR',
  error_msg TEXT         COMMENT '失败时的错误信息',
  cost_ms   INT          COMMENT '执行耗时（ms）'
);

-- SYS-1 操作日志历史归档表（CHG-006）
CREATE TABLE IF NOT EXISTS sys_log_history (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module        VARCHAR(64),
  action        VARCHAR(128),
  operator      VARCHAR(64),
  op_ip         VARCHAR(64),
  op_time       DATETIME,
  level         VARCHAR(16),
  error_msg     TEXT,
  cost_ms       INT,
  archived_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间'
);
```

同时在 `INSERT IGNORE INTO sys_config` 块中追加两行（已有4行，追加在末尾的 `)` 之前）：

```sql
INSERT IGNORE INTO sys_config (config_key, config_value, description) VALUES
('cache.query.ttl', '300', '查询缓存 TTL（秒）'),
('cache.template.ttl', '600', '转换模板缓存 TTL（秒）'),
('audit.log.retention.days', '365', '审计日志保留天数'),
('sql.log.retention.days', '90', 'SQL 日志保留天数'),
('log_menu_enabled', 'true', '日志管理菜单显示开关（true/false）'),
('sys.log.retention.days', '30', '操作日志归档天数');
```

- [ ] **Step 2: 在 `init-h2.sql` 追加对应的 H2 建表语句 + sys_config 默认值**

在 `init-h2.sql` 的 `DROP TABLE` 列表开头追加两行（`sql_audit_log` 之前）：

```sql
DROP TABLE IF EXISTS sys_log_history;
DROP TABLE IF EXISTS sys_log;
```

在文件末尾（`sql_audit_log` 建表之后）追加：

```sql
-- SYS-1 操作日志表（H2）
CREATE TABLE sys_log (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  module    VARCHAR(64),
  action    VARCHAR(128),
  operator  VARCHAR(64),
  op_ip     VARCHAR(64),
  op_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  level     VARCHAR(16),
  error_msg TEXT,
  cost_ms   INT
);

-- SYS-1 操作日志历史归档表（H2，CHG-006）
CREATE TABLE sys_log_history (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module        VARCHAR(64),
  action        VARCHAR(128),
  operator      VARCHAR(64),
  op_ip         VARCHAR(64),
  op_time       DATETIME,
  level         VARCHAR(16),
  error_msg     TEXT,
  cost_ms       INT,
  archived_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

在 H2 的 `INSERT INTO sys_config` 块中追加两行（与 init.sql 保持一致）：

```sql
INSERT INTO sys_config (config_key, config_value, description) VALUES
  ('cache.query.ttl', '300', '查询缓存 TTL（秒）'),
  ('cache.template.ttl', '600', '转换模板缓存 TTL（秒）'),
  ('audit.log.retention.days', '365', '审计日志保留天数'),
  ('sql.log.retention.days', '90', 'SQL 日志保留天数'),
  ('log_menu_enabled', 'true', '日志管理菜单显示开关'),
  ('sys.log.retention.days', '30', '操作日志归档天数');
```

- [ ] **Step 3: 验证 H2 脚本语法**

```bash
cd backend && mvn test -Dtest=PowergatewayApplicationTests -q
```

预期：BUILD SUCCESS（H2 初始化不报错）

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/init.sql backend/src/test/resources/db/init-h2.sql
git commit -m "feat(SYS-1): add sys_log and sys_log_history tables to schema"
```

---

### Task 2: Model & Mapper — SysLog / SysLogHistory 实体 + Mapper

**Files:**
- Create: `backend/src/main/java/com/powergateway/model/SysLog.java`
- Create: `backend/src/main/java/com/powergateway/model/SysLogHistory.java`
- Create: `backend/src/main/java/com/powergateway/dao/SysLogMapper.java`
- Create: `backend/src/main/java/com/powergateway/dao/SysLogHistoryMapper.java`

- [ ] **Step 1: 创建 `SysLog.java`**

```java
package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;
    private String action;
    private String operator;
    private String opIp;
    private LocalDateTime opTime;
    private String level;
    private String errorMsg;
    private Integer costMs;
}
```

- [ ] **Step 2: 创建 `SysLogHistory.java`**

```java
package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log_history")
public class SysLogHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;
    private String action;
    private String operator;
    private String opIp;
    private LocalDateTime opTime;
    private String level;
    private String errorMsg;
    private Integer costMs;
    private LocalDateTime archivedTime;
}
```

- [ ] **Step 3: 创建 `SysLogMapper.java`**

```java
package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.SysLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 将 sys_log 中超期记录批量归档到 sys_log_history（CHG-006）。
     * 使用单条 INSERT...SELECT 语句，效率高于逐条循环。
     */
    @Insert("INSERT INTO sys_log_history (module, action, operator, op_ip, op_time, level, error_msg, cost_ms, archived_time) " +
            "SELECT module, action, operator, op_ip, op_time, level, error_msg, cost_ms, NOW() " +
            "FROM sys_log WHERE op_time < #{threshold}")
    int archiveTo(@Param("threshold") LocalDateTime threshold);
}
```

- [ ] **Step 4: 创建 `SysLogHistoryMapper.java`**

```java
package com.powergateway.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powergateway.model.SysLogHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysLogHistoryMapper extends BaseMapper<SysLogHistory> {
}
```

- [ ] **Step 5: 验证编译**

```bash
cd backend && mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/powergateway/model/SysLog.java \
        backend/src/main/java/com/powergateway/model/SysLogHistory.java \
        backend/src/main/java/com/powergateway/dao/SysLogMapper.java \
        backend/src/main/java/com/powergateway/dao/SysLogHistoryMapper.java
git commit -m "feat(SYS-1): add SysLog/SysLogHistory model and mappers"
```

---

### Task 3: SysLogRecord 注解 + SysLogService（Red → Green）

**Files:**
- Create: `backend/src/main/java/com/powergateway/aop/SysLogRecord.java`
- Create: `backend/src/main/java/com/powergateway/service/SysLogService.java`
- Create: `backend/src/test/java/com/powergateway/SYS1SysLogServiceTest.java`

- [ ] **Step 1: 创建 `@SysLogRecord` 注解（名称与实体类 `SysLog` 区分）**

```java
package com.powergateway.aop;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLogRecord {
    /** 操作模块，如"模板管理" */
    String module();
    /** 操作动作，如"保存模板" */
    String action();
}
```

- [ ] **Step 2: 编写失败测试 `SYS1SysLogServiceTest.java`（Red）**

```java
package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogService 基础写入/查询测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogServiceTest {

    @Autowired private SysLogService sysLogService;
    @Autowired private SysLogMapper sysLogMapper;

    @AfterAll
    void cleanup() {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .like(SysLog::getOperator, "test_svc_"));
    }

    @Test
    @Order(1)
    void enqueue后flushForTest_应写入数据库() {
        SysLog log = new SysLog();
        log.setModule("测试模块");
        log.setAction("测试动作");
        log.setOperator("test_svc_01");
        log.setOpIp("127.0.0.1");
        log.setOpTime(LocalDateTime.now());
        log.setLevel("INFO");
        log.setCostMs(10);

        sysLogService.enqueue(log);
        sysLogService.flushForTest();

        List<SysLog> result = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getOperator, "test_svc_01"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModule()).isEqualTo("测试模块");
        assertThat(result.get(0).getLevel()).isEqualTo("INFO");
    }

    @Test
    @Order(2)
    void list_按module过滤_只返回匹配记录() {
        // 插入两条不同 module 的记录
        insertLog("test_svc_02", "模块A", "INFO");
        insertLog("test_svc_02", "模块B", "INFO");

        var page = sysLogService.list("test_svc_02", "模块A", null, null, null, 1, 10);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getModule()).isEqualTo("模块A");
    }

    @Test
    @Order(3)
    void list_按level过滤_只返回ERROR记录() {
        insertLog("test_svc_03", "模块X", "INFO");
        insertLog("test_svc_03", "模块X", "ERROR");

        var page = sysLogService.list("test_svc_03", null, "ERROR", null, null, 1, 10);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getLevel()).isEqualTo("ERROR");
    }

    private void insertLog(String operator, String module, String level) {
        SysLog log = new SysLog();
        log.setModule(module);
        log.setAction("测试");
        log.setOperator(operator);
        log.setOpIp("127.0.0.1");
        log.setOpTime(LocalDateTime.now());
        log.setLevel(level);
        log.setCostMs(5);
        sysLogMapper.insert(log);
    }
}
```

- [ ] **Step 3: 运行测试，确认失败（SysLogService 不存在）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogServiceTest -q 2>&1 | tail -5
```

预期：BUILD FAILURE（ClassNotFound: SysLogService）

- [ ] **Step 4: 创建 `SysLogService.java`（Green）**

```java
package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.dao.SysLogHistoryMapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.model.SysLog;
import com.powergateway.model.SysLogHistory;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class SysLogService {

    private static final int QUEUE_CAPACITY = 10000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LinkedBlockingQueue<SysLog> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired private SysLogMapper sysLogMapper;
    @Autowired private SysLogHistoryMapper sysLogHistoryMapper;
    @Autowired private SqlAuditLogMapper auditLogMapper;

    @PostConstruct
    public void startConsumer() {
        Thread consumer = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SysLog log = queue.take();
                    writeSafely(log);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "sys-log-consumer");
        consumer.setDaemon(true);
        consumer.start();
    }

    public void enqueue(SysLog log) {
        queue.offer(log);
    }

    /** 仅供测试使用：同步消费队列剩余项目 */
    public void flushForTest() {
        List<SysLog> pending = new ArrayList<>();
        queue.drainTo(pending);
        pending.forEach(this::writeSafely);
    }

    public IPage<SysLog> list(String operator, String module, String level,
                               LocalDateTime startTime, LocalDateTime endTime,
                               int page, int size) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator))  w.like(SysLog::getOperator, operator);
        if (StringUtils.hasText(module))    w.eq(SysLog::getModule, module);
        if (StringUtils.hasText(level))     w.eq(SysLog::getLevel, level);
        if (startTime != null)              w.ge(SysLog::getOpTime, startTime);
        if (endTime != null)                w.le(SysLog::getOpTime, endTime);
        w.orderByDesc(SysLog::getOpTime);
        return sysLogMapper.selectPage(new Page<>(page, size), w);
    }

    public IPage<SysLogHistory> listHistory(String operator, String module, String level,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int page, int size) {
        LambdaQueryWrapper<SysLogHistory> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator))  w.like(SysLogHistory::getOperator, operator);
        if (StringUtils.hasText(module))    w.eq(SysLogHistory::getModule, module);
        if (StringUtils.hasText(level))     w.eq(SysLogHistory::getLevel, level);
        if (startTime != null)              w.ge(SysLogHistory::getOpTime, startTime);
        if (endTime != null)                w.le(SysLogHistory::getOpTime, endTime);
        w.orderByDesc(SysLogHistory::getOpTime);
        return sysLogHistoryMapper.selectPage(new Page<>(page, size), w);
    }

    public IPage<SqlAuditLog> listAuditLogs(String opType, String result,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              int page, int size) {
        LambdaQueryWrapper<SqlAuditLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(opType))  w.eq(SqlAuditLog::getOpType, opType);
        if (StringUtils.hasText(result))  w.eq(SqlAuditLog::getResult, result);
        if (startTime != null)            w.ge(SqlAuditLog::getOpTime, startTime);
        if (endTime != null)              w.le(SqlAuditLog::getOpTime, endTime);
        w.orderByDesc(SqlAuditLog::getOpTime);
        return auditLogMapper.selectPage(new Page<>(page, size), w);
    }

    public byte[] exportExcel(String operator, String module, String level,
                               LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator))  w.like(SysLog::getOperator, operator);
        if (StringUtils.hasText(module))    w.eq(SysLog::getModule, module);
        if (StringUtils.hasText(level))     w.eq(SysLog::getLevel, level);
        if (startTime != null)              w.ge(SysLog::getOpTime, startTime);
        if (endTime != null)                w.le(SysLog::getOpTime, endTime);
        w.orderByDesc(SysLog::getOpTime);
        List<SysLog> logs = sysLogMapper.selectList(w);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("操作日志");
            String[] headers = {"时间", "模块", "动作", "操作人", "IP", "级别", "耗时(ms)", "错误信息"};
            XSSFRow header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            for (int i = 0; i < logs.size(); i++) {
                SysLog log = logs.get(i);
                XSSFRow row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(log.getOpTime() != null ? log.getOpTime().format(FMT) : "");
                row.createCell(1).setCellValue(log.getModule() != null ? log.getModule() : "");
                row.createCell(2).setCellValue(log.getAction() != null ? log.getAction() : "");
                row.createCell(3).setCellValue(log.getOperator() != null ? log.getOperator() : "");
                row.createCell(4).setCellValue(log.getOpIp() != null ? log.getOpIp() : "");
                row.createCell(5).setCellValue(log.getLevel() != null ? log.getLevel() : "");
                row.createCell(6).setCellValue(log.getCostMs() != null ? log.getCostMs() : 0);
                row.createCell(7).setCellValue(log.getErrorMsg() != null ? log.getErrorMsg() : "");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    private void writeSafely(SysLog log) {
        try {
            sysLogMapper.insert(log);
        } catch (Exception ignored) {
        }
    }
}
```

- [ ] **Step 5: 运行测试，确认通过（Green）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogServiceTest -q 2>&1 | tail -5
```

预期：BUILD SUCCESS，Tests run: 3, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/powergateway/aop/SysLogRecord.java \
        backend/src/main/java/com/powergateway/service/SysLogService.java \
        backend/src/test/java/com/powergateway/SYS1SysLogServiceTest.java
git commit -m "feat(SYS-1): add @SysLogRecord annotation and SysLogService with async queue"
```

---

### Task 4: SysLogAspect（Red → Green）

**Files:**
- Create: `backend/src/main/java/com/powergateway/aop/SysLogAspect.java`
- Create: `backend/src/test/java/com/powergateway/SYS1SysLogAopTest.java`
- Modify: `backend/src/main/java/com/powergateway/controller/AuthController.java`（仅用于此 Task 测试）

- [ ] **Step 1: 编写失败测试 `SYS1SysLogAopTest.java`（Red）**

该测试需要先在 `AuthController.login` 上加 `@SysLogRecord` 注解（下一步完成），此处先写测试，运行会因切面不存在而失败。

```java
package com.powergateway;

import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogAspect AOP 拦截测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogAopTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SysLogService sysLogService;
    @Autowired private SysLogMapper sysLogMapper;

    @Test
    @Order(1)
    void 登录成功_切面写入INFO日志() throws Exception {
        // 清理可能残留的日志
        sysLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getModule, "认证").eq(SysLog::getAction, "用户登录"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk());

        sysLogService.flushForTest();

        List<SysLog> logs = sysLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLog>()
                        .eq(SysLog::getModule, "认证")
                        .eq(SysLog::getAction, "用户登录")
                        .eq(SysLog::getLevel, "INFO"));
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getCostMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(2)
    void 登录失败_切面写入ERROR日志() throws Exception {
        sysLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getModule, "认证").eq(SysLog::getLevel, "ERROR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong_password\"}"))
                .andExpect(status().isOk()); // 全局异常处理器返回 200 + code:401

        sysLogService.flushForTest();

        List<SysLog> logs = sysLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysLog>()
                        .eq(SysLog::getModule, "认证")
                        .eq(SysLog::getLevel, "ERROR"));
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getErrorMsg()).isNotBlank();
    }
}
```

- [ ] **Step 2: 在 `AuthController.login` 和 `AuthController.logout` 上添加 `@SysLogRecord` 注解**

在 `AuthController.java` 中添加 import：
```java
import com.powergateway.aop.SysLogRecord;
```

在 `login` 方法上添加：
```java
@SysLogRecord(module = "认证", action = "用户登录")
@PostMapping("/login")
public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
    return Result.success(authService.login(req));
}
```

在 `logout` 方法上添加：
```java
@SysLogRecord(module = "认证", action = "用户登出")
@PostMapping("/logout")
public Result<Void> logout() {
    authService.logout();
    return Result.success();
}
```

- [ ] **Step 3: 运行测试，确认此时仍失败（AOP Bean 不存在）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogAopTest -q 2>&1 | tail -8
```

预期：Tests run: 2, Failures: 2（切面未实现，日志未写入）

- [ ] **Step 4: 创建 `SysLogAspect.java`（Green）**

```java
package com.powergateway.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志 AOP 切面（SYS-1）
 *
 * 拦截所有标注 @SysLogRecord 的方法：
 *   1. 记录开始时间
 *   2. 执行目标方法
 *   3. 成功 → level=INFO；抛异常 → level=ERROR，截取 error_msg
 *   4. 投入 SysLogService 的异步队列
 */
@Aspect
@Component
public class SysLogAspect {

    @Autowired
    private SysLogService sysLogService;

    @Around("@annotation(sysLogRecord)")
    public Object around(ProceedingJoinPoint pjp, SysLogRecord sysLogRecord) throws Throwable {
        long startMs = System.currentTimeMillis();
        LocalDateTime opTime = LocalDateTime.now();
        String operator = resolveOperator();
        String opIp = resolveOpIp();

        try {
            Object result = pjp.proceed();
            sysLogService.enqueue(buildLog(sysLogRecord, operator, opIp, opTime,
                    (int) (System.currentTimeMillis() - startMs), "INFO", null));
            return result;
        } catch (Throwable t) {
            sysLogService.enqueue(buildLog(sysLogRecord, operator, opIp, opTime,
                    (int) (System.currentTimeMillis() - startMs), "ERROR",
                    truncate(t.getMessage(), 1000)));
            throw t;
        }
    }

    private SysLog buildLog(SysLogRecord annotation, String operator, String opIp,
                             LocalDateTime opTime, int costMs, String level, String errorMsg) {
        SysLog log = new SysLog();
        log.setModule(annotation.module());
        log.setAction(annotation.action());
        log.setOperator(operator);
        log.setOpIp(opIp);
        log.setOpTime(opTime);
        log.setCostMs(costMs);
        log.setLevel(level);
        log.setErrorMsg(errorMsg);
        return log;
    }

    private String resolveOperator() {
        try {
            Object loginId = StpUtil.getLoginId(null);
            return loginId != null ? loginId.toString() : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    private String resolveOpIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) return attrs.getRequest().getRemoteAddr();
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
```

- [ ] **Step 5: 运行测试，确认通过（Green）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogAopTest -q 2>&1 | tail -5
```

预期：BUILD SUCCESS，Tests run: 2, Failures: 0

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/powergateway/aop/SysLogAspect.java \
        backend/src/main/java/com/powergateway/controller/AuthController.java \
        backend/src/test/java/com/powergateway/SYS1SysLogAopTest.java
git commit -m "feat(SYS-1): add SysLogAspect and @SysLogRecord on AuthController"
```

---

### Task 5: SysLogController（Red → Green）

**Files:**
- Create: `backend/src/main/java/com/powergateway/controller/SysLogController.java`
- Create: `backend/src/test/java/com/powergateway/SYS1SysLogControllerTest.java`

- [ ] **Step 1: 编写失败测试 `SYS1SysLogControllerTest.java`（Red）**

注意：项目的 Sa-Token 拦截器在 `@WebMvcTest` 下无法加载，必须使用 `@SpringBootTest @AutoConfigureMockMvc`，登录后用 token 访问接口。

```java
package com.powergateway;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogController 接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogControllerTest {

    @Autowired private MockMvc mockMvc;

    private String token;

    @BeforeAll
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @Test
    @Order(1)
    void list接口_返回200和Result包装() throws Exception {
        mockMvc.perform(get("/api/log/list")
                        .header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    void historyList接口_返回200和Result包装() throws Exception {
        mockMvc.perform(get("/api/log/history/list")
                        .header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    void auditList接口_返回200和Result包装() throws Exception {
        mockMvc.perform(get("/api/log/audit/list")
                        .header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    void export接口_返回Excel内容类型() throws Exception {
        mockMvc.perform(get("/api/log/export")
                        .header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }
}
```

- [ ] **Step 2: 运行测试，确认失败（Controller 不存在）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogControllerTest -q 2>&1 | tail -5
```

预期：BUILD FAILURE

- [ ] **Step 3: 创建 `SysLogController.java`（Green）**

```java
package com.powergateway.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.common.Result;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.model.SysLog;
import com.powergateway.model.SysLogHistory;
import com.powergateway.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/log")
@Tag(name = "日志管理", description = "操作日志查询/导出/归档历史查询（SYS-1）")
public class SysLogController {

    @Autowired
    private SysLogService sysLogService;

    @GetMapping("/list")
    @Operation(summary = "操作日志分页查询")
    public Result<IPage<SysLog>> list(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.list(operator, module, level, startTime, endTime, page, size));
    }

    @GetMapping("/history/list")
    @Operation(summary = "历史操作日志分页查询（CHG-006）")
    public Result<IPage<SysLogHistory>> historyList(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.listHistory(operator, module, level, startTime, endTime, page, size));
    }

    @GetMapping("/export")
    @Operation(summary = "导出操作日志 Excel")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        byte[] data = sysLogService.exportExcel(operator, module, level, startTime, endTime);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "sys_log.xlsx");
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @GetMapping("/audit/list")
    @Operation(summary = "SQL 审计日志分页查询")
    public Result<IPage<SqlAuditLog>> auditList(
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(sysLogService.listAuditLogs(opType, result, startTime, endTime, page, size));
    }
}
```

- [ ] **Step 4: 运行测试，确认通过（Green）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogControllerTest -q 2>&1 | tail -5
```

预期：BUILD SUCCESS，Tests run: 4, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/powergateway/controller/SysLogController.java \
        backend/src/test/java/com/powergateway/SYS1SysLogControllerTest.java
git commit -m "feat(SYS-1): add SysLogController (list/history/export/audit)"
```

---

### Task 6: SysLogArchiveJob（Red → Green）

**Files:**
- Create: `backend/src/main/java/com/powergateway/job/SysLogArchiveJob.java`
- Create: `backend/src/test/java/com/powergateway/SYS1SysLogArchiveJobTest.java`

- [ ] **Step 1: 编写失败测试 `SYS1SysLogArchiveJobTest.java`（Red）**

```java
package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysLogHistoryMapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.job.SysLogArchiveJob;
import com.powergateway.model.SysLog;
import com.powergateway.model.SysLogHistory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogArchiveJob 归档任务测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogArchiveJobTest {

    @Autowired private SysLogArchiveJob archiveJob;
    @Autowired private SysLogMapper sysLogMapper;
    @Autowired private SysLogHistoryMapper sysLogHistoryMapper;

    @AfterAll
    void cleanup() {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getAction, "archive_test"));
        sysLogHistoryMapper.delete(new LambdaQueryWrapper<SysLogHistory>()
                .eq(SysLogHistory::getAction, "archive_test"));
    }

    @Test
    void 超期记录_归档到history并从sys_log删除() {
        // 插入一条60天前的"超期"记录
        SysLog old = new SysLog();
        old.setModule("测试模块");
        old.setAction("archive_test");
        old.setOperator("test_archive");
        old.setOpIp("127.0.0.1");
        old.setOpTime(LocalDateTime.now().minusDays(60));
        old.setLevel("INFO");
        old.setCostMs(5);
        sysLogMapper.insert(old);

        // 插入一条当天的"未超期"记录
        SysLog recent = new SysLog();
        recent.setModule("测试模块");
        recent.setAction("archive_test");
        recent.setOperator("test_archive_recent");
        recent.setOpIp("127.0.0.1");
        recent.setOpTime(LocalDateTime.now());
        recent.setLevel("INFO");
        recent.setCostMs(5);
        sysLogMapper.insert(recent);

        // 手动执行归档（默认留存30天，60天的超期）
        archiveJob.archive();

        // 超期记录应已从 sys_log 删除
        List<SysLog> remaining = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getOperator, "test_archive"));
        assertThat(remaining).isEmpty();

        // 超期记录应在 sys_log_history 中
        List<SysLogHistory> archived = sysLogHistoryMapper.selectList(
                new LambdaQueryWrapper<SysLogHistory>().eq(SysLogHistory::getOperator, "test_archive"));
        assertThat(archived).hasSize(1);
        assertThat(archived.get(0).getArchivedTime()).isNotNull();

        // 未超期记录应仍在 sys_log
        List<SysLog> stillRecent = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getOperator, "test_archive_recent"));
        assertThat(stillRecent).hasSize(1);
    }
}
```

- [ ] **Step 2: 运行测试，确认失败（SysLogArchiveJob 不存在）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogArchiveJobTest -q 2>&1 | tail -5
```

预期：BUILD FAILURE

- [ ] **Step 3: 创建 `SysLogArchiveJob.java`（Green）**

```java
package com.powergateway.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SysConfig;
import com.powergateway.model.SysLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 操作日志归档定时任务（SYS-1，CHG-006）
 *
 * 每天凌晨 3 点执行（与 AuditLogCleanupJob 的 2 点错开），从 sys_config 读取
 * sys.log.retention.days（默认 30 天），将超期记录用单条 INSERT...SELECT 归档到
 * sys_log_history，再从 sys_log 删除。两步在同一事务中执行，失败时自动回滚。
 */
@Component
public class SysLogArchiveJob {

    private static final String CONFIG_KEY = "sys.log.retention.days";
    private static final int DEFAULT_RETENTION_DAYS = 30;

    @Autowired private SysLogMapper sysLogMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void archive() {
        int retentionDays = DEFAULT_RETENTION_DAYS;
        SysConfig config = sysConfigMapper.selectById(CONFIG_KEY);
        if (config != null && config.getConfigValue() != null) {
            try {
                retentionDays = Integer.parseInt(config.getConfigValue().trim());
            } catch (NumberFormatException ignored) {
            }
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        // 批量归档（INSERT...SELECT，比逐条循环高效）
        sysLogMapper.archiveTo(threshold);

        // 从当前表删除超期记录
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>().lt(SysLog::getOpTime, threshold));
    }
}
```

- [ ] **Step 4: 运行测试，确认通过（Green）**

```bash
cd backend && mvn test -Dtest=SYS1SysLogArchiveJobTest -q 2>&1 | tail -5
```

预期：BUILD SUCCESS，Tests run: 1, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/powergateway/job/SysLogArchiveJob.java \
        backend/src/test/java/com/powergateway/SYS1SysLogArchiveJobTest.java
git commit -m "feat(SYS-1): add SysLogArchiveJob (archive expired logs to history table)"
```

---

### Task 7: SQL 审计日志查询集成测试

**Files:**
- Create: `backend/src/test/java/com/powergateway/SYS1AuditLogQueryTest.java`

- [ ] **Step 1: 编写并运行 `SYS1AuditLogQueryTest.java`**

```java
package com.powergateway;

import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.service.SysLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-1 SQL 审计日志查询测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1AuditLogQueryTest {

    @Autowired private SysLogService sysLogService;
    @Autowired private SqlAuditLogMapper auditLogMapper;

    @BeforeAll
    void insertTestData() {
        SqlAuditLog log1 = new SqlAuditLog();
        log1.setInterfaceId(1L);
        log1.setSqlText("INSERT INTO orders VALUES(1)");
        log1.setOpType("INSERT");
        log1.setOperator(null);   // 故意为 null，模拟非登录场景
        log1.setOpIp(null);        // 故意为 null
        log1.setOpTime(LocalDateTime.now());
        log1.setTargetDb("order_db");
        log1.setTargetTable("orders");
        log1.setResult("SUCCESS");
        auditLogMapper.insert(log1);

        SqlAuditLog log2 = new SqlAuditLog();
        log2.setInterfaceId(2L);
        log2.setSqlText("DELETE FROM orders WHERE id=1");
        log2.setOpType("DELETE");
        log2.setOperator("admin");
        log2.setOpIp("192.168.1.1");
        log2.setOpTime(LocalDateTime.now());
        log2.setTargetDb("order_db");
        log2.setTargetTable("orders");
        log2.setResult("FAIL");
        log2.setErrorMsg("Connection timeout");
        auditLogMapper.insert(log2);
    }

    @AfterAll
    void cleanup() {
        auditLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SqlAuditLog>()
                .eq(SqlAuditLog::getTargetDb, "order_db"));
    }

    @Test
    void 按opType过滤_只返回INSERT记录() {
        var page = sysLogService.listAuditLogs("INSERT", null, null, null, 1, 10);
        assertThat(page.getRecords()).anyMatch(l -> "INSERT".equals(l.getOpType()));
        assertThat(page.getRecords()).noneMatch(l -> "DELETE".equals(l.getOpType()));
    }

    @Test
    void 按result过滤_只返回FAIL记录() {
        var page = sysLogService.listAuditLogs(null, "FAIL", null, null, 1, 10);
        assertThat(page.getRecords()).isNotEmpty();
        assertThat(page.getRecords()).allMatch(l -> "FAIL".equals(l.getResult()));
    }

    @Test
    void null的operator和ip字段_正常返回不报错() {
        var page = sysLogService.listAuditLogs("INSERT", null, null, null, 1, 10);
        // null operator/ip 不引发 NPE，正常返回
        assertThat(page.getRecords()).isNotEmpty();
        SqlAuditLog nullOperatorLog = page.getRecords().stream()
                .filter(l -> l.getOperator() == null).findFirst().orElse(null);
        assertThat(nullOperatorLog).isNotNull();
    }
}
```

- [ ] **Step 2: 运行测试，确认通过**

```bash
cd backend && mvn test -Dtest=SYS1AuditLogQueryTest -q 2>&1 | tail -5
```

预期：BUILD SUCCESS，Tests run: 3, Failures: 0

- [ ] **Step 3: 运行全量测试，确认无退化**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```

预期：BUILD SUCCESS，所有已有测试通过

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/com/powergateway/SYS1AuditLogQueryTest.java
git commit -m "test(SYS-1): add audit log query integration test"
```

---

### Task 8: 在全部 Controller 写方法上标注 @SysLogRecord（~18 处，AuthController 已在 Task 4 完成）

**Files:**
- Modify: `backend/src/main/java/com/powergateway/controller/TemplateController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/ChannelConfigController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/PortRouteController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/DbConnectionController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/InterfaceConfigController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/UserController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/ShardConfigController.java`
- Modify: `backend/src/main/java/com/powergateway/controller/CacheController.java`

- [ ] **Step 1: 在每个 Controller 文件顶部添加 import**

在所有以下文件中添加：
```java
import com.powergateway.aop.SysLogRecord;
```

- [ ] **Step 2: `TemplateController.java` — 3 处**

```java
@SysLogRecord(module = "模板管理", action = "保存模板")
@PostMapping("/save")
public Result<Long> save(...) { ... }

@SysLogRecord(module = "模板管理", action = "删除模板")
@DeleteMapping("/{id}")
public Result<Void> delete(...) { ... }

@SysLogRecord(module = "模板管理", action = "复制模板")
@PostMapping("/{id}/copy")
public Result<Long> copy(...) { ... }
```

- [ ] **Step 3: `ChannelConfigController.java` — 2 处**

```java
@SysLogRecord(module = "渠道配置", action = "保存渠道")
@PostMapping("/save")
public Result<Long> save(...) { ... }

@SysLogRecord(module = "渠道配置", action = "删除渠道")
@DeleteMapping("/{id}")
public Result<Void> delete(...) { ... }
```

- [ ] **Step 4: `PortRouteController.java` — 2 处**

```java
@SysLogRecord(module = "端口路由", action = "保存路由")
@PostMapping("/save")
public Result<Long> save(...) { ... }

@SysLogRecord(module = "端口路由", action = "删除路由")
@DeleteMapping("/{id}")
public Result<Void> delete(...) { ... }
```

- [ ] **Step 5: `DbConnectionController.java` — 2 处**

```java
@SysLogRecord(module = "数据库连接", action = "保存连接")
@PostMapping("/save")
public Result<Long> save(...) { ... }

@SysLogRecord(module = "数据库连接", action = "删除连接")
@DeleteMapping("/{id}")
public Result<Void> delete(...) { ... }
```

- [ ] **Step 6: `InterfaceConfigController.java` — 4 处**

```java
@SysLogRecord(module = "接口配置", action = "保存接口")
@PostMapping("/save")
public Result<Long> save(...) { ... }

@SysLogRecord(module = "接口配置", action = "删除接口")
@DeleteMapping("/{id}")
public Result<Void> delete(...) { ... }

@SysLogRecord(module = "接口配置", action = "发布接口")
@PostMapping("/{id}/publish")
public Result<Void> publish(...) { ... }

@SysLogRecord(module = "接口配置", action = "禁用接口")
@PostMapping("/{id}/disable")
public Result<Void> disable(...) { ... }
```

- [ ] **Step 7: `UserController.java` — 2 处**

```java
@SysLogRecord(module = "用户管理", action = "保存用户")
@PostMapping("/save")
public Result<Long> save(...) { ... }

@SysLogRecord(module = "用户管理", action = "删除用户")
@DeleteMapping("/{id}")
public Result<Void> delete(...) { ... }
```

- [ ] **Step 8: `ShardConfigController.java` — 2 处**

```java
// 在 @PostMapping("/save") 上方加：
@SysLogRecord(module = "分库分表", action = "保存分片配置")
@PostMapping("/save")
public Result<Long> save(@RequestBody ShardSaveRequest req) { ... }

// 在 @DeleteMapping("/{id}") 上方加：
@SysLogRecord(module = "分库分表", action = "删除分片配置")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id) { ... }
```

- [ ] **Step 9: `CacheController.java` — 2 处**

```java
// 在 @DeleteMapping("/{interfaceId}") evict 方法上方加：
@SysLogRecord(module = "缓存管理", action = "清除缓存")
@DeleteMapping("/{interfaceId}")
public Result<?> evict(@PathVariable Long interfaceId) { ... }

// 在 @DeleteMapping("/all") evictAll 方法上方加：
@SysLogRecord(module = "缓存管理", action = "清除全部缓存")
@DeleteMapping("/all")
public Result<?> evictAll() { ... }
```

- [ ] **Step 10: 运行全量测试，确认无退化**

```bash
cd backend && mvn test -q 2>&1 | tail -10
```

预期：BUILD SUCCESS，所有测试通过

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/powergateway/controller/
git commit -m "feat(SYS-1): add @SysLogRecord annotations to all write Controller methods"
```

---

### Task 9: 前端 — api/log.js + LogList.vue

**Files:**
- Create: `frontend/src/api/log.js`
- Create: `frontend/src/views/system/LogList.vue`

- [ ] **Step 1: 创建 `frontend/src/api/log.js`**

```js
import request from '@/api/request'

export function listLogs(params) {
  return request.get('/log/list', { params })
}

export function listHistoryLogs(params) {
  return request.get('/log/history/list', { params })
}

export function listAuditLogs(params) {
  return request.get('/log/audit/list', { params })
}

export function exportLogs(params) {
  return request.get('/log/export', {
    params,
    responseType: 'blob'
  }).then(blob => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'sys_log.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  })
}
```

- [ ] **Step 2: 创建 `frontend/src/views/system/LogList.vue`**

```vue
<template>
  <div class="log-list">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">

      <!-- ─── Tab 1: 操作日志 ─── -->
      <el-tab-pane label="操作日志" name="operation">
        <div class="toolbar">
          <el-input v-model="opForm.operator" placeholder="操作人" clearable style="width:160px" />
          <el-select v-model="opForm.module" placeholder="操作模块" clearable style="width:150px">
            <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="opForm.level" placeholder="日志级别" clearable style="width:120px">
            <el-option label="INFO"  value="INFO" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
          <el-date-picker
            v-model="opForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width:340px"
          />
          <el-button type="primary" @click="loadOpLogs">查询</el-button>
          <el-button @click="resetOpForm">重置</el-button>
          <div style="flex:1" />
          <span style="margin-right:8px;font-size:13px;color:#606266">查历史数据</span>
          <el-switch v-model="showHistory" @change="loadOpLogs" />
          <el-button type="success" style="margin-left:12px" @click="handleExport">导出 Excel</el-button>
        </div>

        <el-table :data="opList" stripe border v-loading="opLoading" style="margin-top:14px">
          <el-table-column prop="opTime"   label="时间"   width="175" />
          <el-table-column prop="module"   label="模块"   width="110" />
          <el-table-column prop="action"   label="动作"   width="120" />
          <el-table-column prop="operator" label="操作人" width="100" />
          <el-table-column prop="opIp"     label="IP"    width="130" />
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <el-tag :type="row.level === 'ERROR' ? 'danger' : 'success'" size="small">
                {{ row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="costMs" label="耗时(ms)" width="90" />
          <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
        </el-table>

        <el-pagination
          v-model:current-page="opPage"
          :page-size="opPageSize"
          :total="opTotal"
          layout="total, prev, pager, next"
          style="margin-top:14px;text-align:right"
          @current-change="loadOpLogs"
        />
      </el-tab-pane>

      <!-- ─── Tab 2: SQL 审计 ─── -->
      <el-tab-pane label="SQL 审计" name="audit">
        <div class="toolbar">
          <el-select v-model="auditForm.opType" placeholder="操作类型" clearable style="width:140px">
            <el-option label="INSERT" value="INSERT" />
            <el-option label="UPDATE" value="UPDATE" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
          <el-select v-model="auditForm.result" placeholder="执行结果" clearable style="width:120px">
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAIL"    value="FAIL" />
          </el-select>
          <el-date-picker
            v-model="auditForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width:340px"
          />
          <el-button type="primary" @click="loadAuditLogs">查询</el-button>
          <el-button @click="resetAuditForm">重置</el-button>
        </div>

        <el-table :data="auditList" stripe border v-loading="auditLoading" style="margin-top:14px">
          <el-table-column prop="opTime"     label="时间"     width="175" />
          <el-table-column prop="interfaceId" label="接口ID"  width="80" />
          <el-table-column prop="opType"     label="操作类型" width="90" />
          <el-table-column prop="operator"   label="操作人"   width="100">
            <template #default="{ row }">{{ row.operator || '—' }}</template>
          </el-table-column>
          <el-table-column prop="opIp" label="IP" width="130">
            <template #default="{ row }">{{ row.opIp || '—' }}</template>
          </el-table-column>
          <el-table-column prop="targetDb"    label="目标库"   width="110" />
          <el-table-column prop="targetTable" label="目标表"   width="110" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 'FAIL' ? 'danger' : 'success'" size="small">
                {{ row.result }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sqlText" label="SQL" min-width="200" show-overflow-tooltip />
        </el-table>

        <el-pagination
          v-model:current-page="auditPage"
          :page-size="auditPageSize"
          :total="auditTotal"
          layout="total, prev, pager, next"
          style="margin-top:14px;text-align:right"
          @current-change="loadAuditLogs"
        />
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listLogs, listHistoryLogs, listAuditLogs, exportLogs } from '@/api/log'

// ─── Tab 状态 ──────────────────────────────
const activeTab = ref('operation')

// ─── 操作日志 ──────────────────────────────
const moduleOptions = [
  '认证', '模板管理', '渠道配置', '端口路由', '数据库连接',
  '接口配置', '用户管理', '分库分表', '缓存管理'
]
const opForm = ref({ operator: '', module: '', level: '', timeRange: null })
const showHistory = ref(false)
const opList = ref([])
const opLoading = ref(false)
const opPage = ref(1)
const opPageSize = 20
const opTotal = ref(0)

async function loadOpLogs() {
  opLoading.value = true
  try {
    const params = buildOpParams()
    const fn = showHistory.value ? listHistoryLogs : listLogs
    const data = await fn(params)
    opList.value = data.records || []
    opTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载日志失败')
  } finally {
    opLoading.value = false
  }
}

function buildOpParams() {
  const p = {
    operator: opForm.value.operator || undefined,
    module:   opForm.value.module   || undefined,
    level:    opForm.value.level    || undefined,
    page:     opPage.value,
    size:     opPageSize
  }
  if (opForm.value.timeRange) {
    p.startTime = opForm.value.timeRange[0]?.toISOString()
    p.endTime   = opForm.value.timeRange[1]?.toISOString()
  }
  return p
}

function resetOpForm() {
  opForm.value = { operator: '', module: '', level: '', timeRange: null }
  opPage.value = 1
  showHistory.value = false
  loadOpLogs()
}

async function handleExport() {
  try {
    await exportLogs(buildOpParams())
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

// ─── SQL 审计 ──────────────────────────────
const auditForm = ref({ opType: '', result: '', timeRange: null })
const auditList = ref([])
const auditLoading = ref(false)
const auditPage = ref(1)
const auditPageSize = 20
const auditTotal = ref(0)

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const params = {
      opType: auditForm.value.opType || undefined,
      result: auditForm.value.result || undefined,
      page:   auditPage.value,
      size:   auditPageSize
    }
    if (auditForm.value.timeRange) {
      params.startTime = auditForm.value.timeRange[0]?.toISOString()
      params.endTime   = auditForm.value.timeRange[1]?.toISOString()
    }
    const data = await listAuditLogs(params)
    auditList.value = data.records || []
    auditTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载审计日志失败')
  } finally {
    auditLoading.value = false
  }
}

function resetAuditForm() {
  auditForm.value = { opType: '', result: '', timeRange: null }
  auditPage.value = 1
  loadAuditLogs()
}

function handleTabChange(tab) {
  if (tab === 'audit' && auditList.value.length === 0) loadAuditLogs()
}

onMounted(loadOpLogs)
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/log.js frontend/src/views/system/LogList.vue
git commit -m "feat(SYS-1): add log.js API and LogList.vue dual-tab frontend"
```

---

### Task 10: 前端路由接入 + 全量验证 + 推送

**Files:**
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 在 `router/index.js` 中将 `/system/log` 的占位组件替换为 `LogList`**

找到以下片段：
```js
{
  path: 'system/log',
  name: 'LogManage',
  component: () => import('@/views/placeholder/PlaceholderView.vue'),
  meta: { title: '日志管理' }
},
```

替换为：
```js
{
  path: 'system/log',
  name: 'LogManage',
  component: () => import('@/views/system/LogList.vue'),
  meta: { title: '日志管理' }
},
```

- [ ] **Step 2: 后端全量测试**

```bash
cd backend && mvn test -q 2>&1 | tail -15
```

预期：BUILD SUCCESS，所有测试通过（含本次新增的 5 个测试类）

- [ ] **Step 3: 更新 CLAUDE.md 已完成单元表**

在 `backend/CLAUDE.md` 的「已完成单元」表格末尾追加一行：

```
| SYS-1 | 操作日志管理：`@SysLogRecord` 注解+`SysLogAspect` AOP 异步写 `sys_log`，`SysLogService`（队列+分页查询+Excel导出），`SysLogArchiveJob`（归档到 `sys_log_history`），`SysLogController`（list/history/export/audit），前端 `LogList.vue`（双Tab：操作日志+SQL审计，含「查历史数据」开关） |
```

同时删除「下一阶段」表格中 SYS-1 这一行。

- [ ] **Step 4: Commit 路由 + 文档**

```bash
git add frontend/src/router/index.js backend/CLAUDE.md
git commit -m "feat(SYS-1): wire LogList.vue to /system/log route, update CLAUDE.md"
```

- [ ] **Step 5: git push**

```bash
git push origin master
```

---

## 验收检查清单

- [ ] 执行"保存模板"操作后，`sys_log` 有 `level=INFO, module=模板管理, action=保存模板` 记录
- [ ] 执行登录失败后，`sys_log` 有 `level=ERROR, module=认证` 记录
- [ ] `GET /api/log/list?module=认证` 只返回认证模块记录
- [ ] `GET /api/log/export` 下载 Excel 文件，包含过滤结果
- [ ] 手动调用归档任务后，30 天前记录从 `sys_log` 消失，出现在 `sys_log_history`
- [ ] `GET /api/log/history/list` 能查到历史数据
- [ ] SQL 审计 Tab 展示 `sql_audit_log` 数据，`operator=null` 时显示 `—`
- [ ] 前端操作日志 Tab 切换「查历史数据」开关后请求改为 `/api/log/history/list`
- [ ] `sys_config` 中 `log_menu_enabled=false` 后，`/api/auth/menu` 不含 `/system/log`
- [ ] 全量测试：`mvn test` BUILD SUCCESS
