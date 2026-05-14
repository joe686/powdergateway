# SYS-2 性能统计设计规格

**日期**：2026-05-14  
**单元**：SYS-2  
**前置依赖**：P0-2（前端框架）、M2-7（ExecController 执行入口）  
**验收标准**：执行10次查询后统计页图表有数据；修改告警阈值后下次检查生效

---

## 1. 数据库表

### 1.1 perf_stat（性能统计明细）

```sql
CREATE TABLE perf_stat (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  interface_id BIGINT       COMMENT '接口ID，关联 interface_config.id',
  op_type      VARCHAR(32)  COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  cost_ms      INT          COMMENT '耗时（毫秒）',
  success      TINYINT      COMMENT '1=成功 0=失败',
  stat_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_stat_time (stat_time),
  INDEX idx_interface (interface_id)
);
```

### 1.2 perf_alert（告警记录）

```sql
CREATE TABLE perf_alert (
  id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
  alert_type  VARCHAR(64)   COMMENT 'FAIL_RATE / AVG_RESPONSE',
  alert_value DECIMAL(10,2) COMMENT '实际值（失败率%或毫秒）',
  threshold   DECIMAL(10,2) COMMENT '触发时的阈值',
  message     VARCHAR(512),
  check_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  resolved    TINYINT       DEFAULT 0
);
```

> `sys_config` 表已有预置键：`alert_fail_rate=5`（失败率阈值%）、`alert_response_ms=1000`（响应时间阈值ms）。SYS-2 直接读写这两个键，不新增键。

---

## 2. 后端实现

### 2.1 AOP 注解

新建 `@PerfStat` 注解（`aop/PerfStat.java`），无属性。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PerfStat {}
```

标注位置：`ExecController.execute()` 方法。

### 2.2 PerfStatAspect（切面）

```
aop/PerfStatAspect.java
```

- `@Around("@annotation(com.powergateway.aop.PerfStat)")`
- 从切面参数取 `interfaceId`（PathVariable，方法第一个参数）
- 记录 `startMs = System.currentTimeMillis()`，执行目标方法
- `success = 1`（正常返回）或 `0`（抛异常）
- 将 `PerfStatRecord{interfaceId, opType, costMs, success}` 投入 `PerfStatService` 异步队列
- opType 从 `InterfaceConfig.type` 取（需要先查配置，但 ExecController 内已有 `config` 对象，通过 ThreadLocal 透传或在切面内二次查询，**选择切面内通过 interfaceId 查 `InterfaceConfigMapper`**）

### 2.3 PerfStatService（异步写入）

```
service/PerfStatService.java
```

模式与 `SysLogService`、`AuditLogService` 完全一致：
- `LinkedBlockingQueue<PerfStat>` 内部队列
- 构造函数启动守护线程，循环 `take()` 写库
- `enqueue(PerfStat record)` 公开方法

### 2.4 查询接口

**`GET /api/stats/summary`**

参数：`dimension`（today / week / month，默认 today）

返回结构：
```json
{
  "timeline": ["08:00", "09:00", ...],
  "successCounts": [12, 8, ...],
  "failCounts": [0, 1, ...],
  "avgCostMs": [120, 95, ...]
}
```

SQL 聚合逻辑：
- `today`：按小时分组，`GROUP BY DATE_FORMAT(stat_time, '%H:00')`
- `week`：按天分组，`GROUP BY DATE(stat_time)`，最近7天
- `month`：按天分组，最近30天

**`GET /api/stats/alerts`**

参数：`page`、`pageSize`（默认20）  
返回：`perf_alert` 分页列表，按 `check_time DESC`

**`PUT /api/stats/alert-config`**

请求体：`{ "failRate": 5, "responseMs": 1000 }`  
操作：批量更新 `sys_config` 的 `alert_fail_rate`、`alert_response_ms` 两条记录

### 2.5 PerfAlertJob（定时告警检查）

```
job/PerfAlertJob.java
```

```
@Scheduled(cron = "0 * * * * ?")   // 每分钟执行
```

逻辑：
1. 从 `sys_config` 读阈值（`alert_fail_rate`、`alert_response_ms`）
2. 查最近1分钟 `perf_stat`：计算 `失败率 = COUNT(success=0)/COUNT(*)*100`、`平均耗时 = AVG(cost_ms)`
3. 若失败率超阈值 → 写 `perf_alert(alert_type=FAIL_RATE, ...)`
4. 若平均耗时超阈值 → 写 `perf_alert(alert_type=AVG_RESPONSE, ...)`
5. 若最近1分钟无数据，跳过

### 2.6 Controller

```
controller/StatsController.java
```

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stats/summary` | 聚合图表数据 |
| GET | `/api/stats/alerts` | 告警列表（分页） |
| PUT | `/api/stats/alert-config` | 更新阈值 |

需要 Sa-Token 登录鉴权（在 `SaTokenConfig` 白名单外，自动鉴权）。

---

## 3. 前端实现

### 3.1 api/stats.js

```js
import request from '@/api/request'

export const getStatsSummary = (dimension) =>
  request.get('/stats/summary', { params: { dimension } })

export const getAlerts = (page, pageSize) =>
  request.get('/stats/alerts', { params: { page, pageSize } })

export const updateAlertConfig = (data) =>
  request.put('/stats/alert-config', data)
```

### 3.2 views/system/Stats.vue

**布局（三块）**：

**块1 — 图表区**
- 顶部：`el-radio-group`（今天 / 本周 / 本月），切换时重新请求 `/api/stats/summary`
- 左图：`vue-echarts` 折线图，双线（成功次数/失败次数），X轴=时间标签
- 右图：`vue-echarts` 柱状图，单色柱（平均响应时间ms），X轴=时间标签

**块2 — 告警列表**
- `el-table`：告警类型、实际值、阈值、消息、检查时间、是否已处理
- 未处理行（`resolved=0`）row-class-name 加红色样式
- `el-pagination`（pageSize=10）

**块3 — 告警配置**
- 右上角 `el-button type="warning"` 打开 `el-dialog`
- 表单：失败率阈值（`el-input-number`，min=1,max=100，步长1）+ 响应时间阈值（`el-input-number`，min=100，步长100）
- 「保存」调用 `updateAlertConfig`，成功后 `ElMessage.success('配置已更新')`

### 3.3 路由接入

`src/router/index.js` 已预留 `system/stats`，将 `PlaceholderView` 替换为：
```js
component: () => import('@/views/system/Stats.vue')
```

---

## 4. 测试策略（TDD）

| 测试类 | 类型 | 覆盖点 |
|--------|------|--------|
| `SYS2PerfStatAspectTest` | SpringBootTest + MockMvc | 调用 `/api/exec/{id}` 后 perf_stat 有记录；失败调用 success=0 |
| `SYS2PerfAlertJobTest` | SpringBootTest | 插入高失败率数据，触发 job，perf_alert 有记录 |
| `SYS2StatsControllerTest` | SpringBootTest | summary 接口返回正确聚合结构；alerts 分页正常 |

所有测试类加 `@ActiveProfiles("test")`，H2 内存库，`perf_stat` 和 `perf_alert` 需在 `init-h2.sql` 中建表。

---

## 5. 实现顺序（推荐）

1. `init.sql` + `init-h2.sql` 新增 `perf_stat`、`perf_alert` 两张表
2. 实体类 + Mapper（`PerfStat`、`PerfAlert`、`PerfStatMapper`、`PerfAlertMapper`）
3. `@PerfStat` 注解 + `PerfStatService`（异步队列）
4. `PerfStatAspect`（切面） + `ExecController` 加注解
5. **Red → Green**：`SYS2PerfStatAspectTest`
6. `PerfAlertJob` + `StatsController`（summary/alerts/alertConfig）
7. **Red → Green**：`SYS2PerfAlertJobTest`、`SYS2StatsControllerTest`
8. 前端 `api/stats.js` + `Stats.vue`
9. 路由接入，浏览器验证图表有数据、告警配置保存生效

---

## 6. 不含范围

- `perf_stat` 数据定期清理（可在后续 SYS-4 中统一配置，或作 CHG 追加）
- 告警通知推送（邮件/钉钉），当前仅写库
- 按接口维度的独立统计页（当前 summary 只支持全局聚合）
- 告警"标记已处理"API（`perf_alert.resolved` 字段预留，SYS-2 写入时始终为 0，后续版本可扩展）
