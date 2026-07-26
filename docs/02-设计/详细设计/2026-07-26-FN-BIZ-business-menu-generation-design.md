# FN-BIZ · 业务菜单生成能力 · 设计文档

## 元信息

| 项 | 值 |
|---|---|
| 代号 | FN-BIZ（Business Menu Generation） |
| 类型 | **新增单元**（远期战略功能，独立子模块） |
| 目标版本 | **v0.5.0**（单一大版本，仅做此功能 + SYS-3 升级） |
| 依赖前置 | ① [SYS-3 升级](./2026-07-26-SYS-3-upgrade-design.md)（必须先完成） · ② FN-12 字典功能（v0.2.0 已排期） |
| 触发反馈 | FB-041 · CR-002（详见 [反馈簿](../../06-项目管理/反馈簿.md#fb-041) / [待办与缺陷池](../../06-项目管理/待办与缺陷池.md#cr-002-fn-biz-业务菜单生成能力--sys-3-升级)） |
| 状态 | 待评审 · 待排期 |

---

## 一、战略定位与目标

### 1.1 战略定位

从**"接口开发平台" → "业务系统生成平台"**的关键跨越。已发布 CRUD 接口不仅是给外部调用的 API，同时是**业务用户可直接操作的功能界面** —— 零编码即可交付 MIS 系统。

### 1.2 目标（G1-G10）

| 序 | 目标 |
|---|---|
| G1 | **一键生成菜单**：已发布 4 类 CRUD 接口（M2-3/4/5/6 的 SELECT/INSERT/UPDATE/DELETE），一接口一菜单，支持批量/单条 |
| G2 | **独立生成中心**：新菜单"业务菜单生成中心"，含接口清单（多选）+ 双状态列（生成/发布）+ 统计饼图 + 生成/重新生成按钮 |
| G3 | **通用页面模板**：一个 Vue 组件覆盖所有业务菜单，运行时按接口元数据渲染。信息区 + 动态表单区（含字典联动）+ 响应区（SELECT 出表格；其他出响应回显） |
| G4 | **多业务应用组织**：支持多业务应用（CRM/订单/财务…），应用下最多 2 级菜单（分组+菜单项），支持一接口挂多应用 |
| G5 | **变更同步四态**：接口字段变→"已过期"；接口 disabled→"已停用"隐藏；接口删除→"已孤立"；重新生成保留手改的展示名/图标/排序 |
| G6 | **字典自动联动**：字段有 dict_key（FN-12）→ 表单下拉、响应表格反向映射中文 |
| G7 | **权限模型统一**：复用 SYS-3 升级后的 sys_menu 表，一表通吃 |
| G8 | **业务操作监控**：新表 business_op_log 存请求/响应报文 + 全局 trace_id；trace_id 打入 sys_log/sql_audit_log/perf_stat 三张现有表；首页统计 + 独立查询菜单 |
| G9 | **首页统计接入**：DashboardView 新增 4 卡片（业务菜单总数/覆盖率/24h 业务操作数/应用分布饼图） |
| G10 | **模块化可裁剪**：独立 Maven 子模块 `pg-biz-menu` + `sys_config.biz.menu.enabled` 运行时开关双控 |

### 1.3 非目标

- ❌ 不生成独立前端工程（不生成 .vue 文件，改运行时渲染）
- ❌ 不搞独立业务门户 / 独立域名 / 独立部署（一进程一前端）
- ❌ 不建独立业务用户体系（复用 sys_user + 升级后 SYS-3 权限）
- ❌ 不做可视化拖拽的页面编辑器（页面模板固定）
- ❌ 不做多租户 / SaaS 化
- ❌ 不与外部 UI 框架集成（微前端 / iframe 挂 OA）
- ❌ 不做工作流 / BPM / 审批编排（单接口 = 单表单）
- ❌ 不覆盖 M1-x 转换接口（仅 4 类 CRUD）
- ❌ 不做"字段级" / "按钮级"权限（仅菜单粒度）

---

## 二、数据模型

### 2.1 复用 sys_menu 表

SYS-3 升级后 sys_menu 已包含全部结构，本单元不新建菜单表。业务应用 = origin=BIZ_APP 一行，业务菜单 = origin=BIZ 一行。参见 [SYS-3 升级设计 § 3.1](./2026-07-26-SYS-3-upgrade-design.md#31-sys_menu--统一菜单表)。

### 2.2 business_op_log · 业务操作日志（新表）

```sql
CREATE TABLE business_op_log (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  trace_id      VARCHAR(64)  NOT NULL       COMMENT '全局流水号（snowflake）',
  user_id       BIGINT       NOT NULL       COMMENT '操作用户',
  user_name     VARCHAR(128)                COMMENT '冗余存名字',
  menu_id       BIGINT       NOT NULL       COMMENT 'sys_menu.id',
  menu_code     VARCHAR(128)                COMMENT '冗余',
  app_menu_id   BIGINT                      COMMENT '所属业务应用节点',
  interface_id  BIGINT       NOT NULL       COMMENT '底层接口 id',
  op_type       VARCHAR(16)  NOT NULL       COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  req_body      MEDIUMTEXT                  COMMENT '完整请求报文（JSON）',
  resp_body     MEDIUMTEXT                  COMMENT '完整响应报文（JSON，含错误）',
  resp_code     INT                         COMMENT '业务响应码',
  elapsed_ms    INT                         COMMENT '总耗时',
  client_ip     VARCHAR(45),
  user_agent    VARCHAR(255),
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uk_trace (trace_id),
  KEY idx_user_time (user_id, create_time),
  KEY idx_menu_time (menu_id, create_time),
  KEY idx_interface_time (interface_id, create_time),
  KEY idx_time (create_time)
);
```

**位置**：**配置库**（默认数据源，**不**走 `@DS("audit")`）—— 用户 2026-07-26 明确要求"本质上放一个库就行"。

**留存**：`biz.menu.oplog.retention.days`（默认 90 天），独立定时任务清理。

### 2.3 现有 3 表加 trace_id 列

```sql
ALTER TABLE sys_log         ADD COLUMN trace_id VARCHAR(64), ADD KEY idx_trace (trace_id);
ALTER TABLE sql_audit_log   ADD COLUMN trace_id VARCHAR(64), ADD KEY idx_trace (trace_id);
ALTER TABLE perf_stat       ADD COLUMN trace_id VARCHAR(64), ADD KEY idx_trace (trace_id);
```

**串联机制**：`BusinessInterfaceInvokerService` 生成 trace_id → 塞 `TraceIdContext` (ThreadLocal) → 各 Aspect (SqlAuditAspect / SysLogAspect / PerfStatAspect) 读取并写入自身表 → 反查时按 trace_id 四表 join。

### 2.4 interface_config 扩展

```sql
ALTER TABLE interface_config ADD COLUMN default_app_menu_id BIGINT
  COMMENT '默认归属业务应用节点(sys_menu.id where origin=BIZ_APP)';
```

发布接口时可下拉选（可空）；生成中心默认按此分组，允许手动覆盖到其他应用。

---

## 三、独立子模块架构

### 3.1 目录结构

```
powergateway/
├─ backend/                          主后端（不 import pg-biz-menu 任何类）
├─ pg-testkit/                       现有独立模块
└─ pg-biz-menu/                      🆕 新增独立模块
    ├─ pom.xml                       依赖 backend-core（反向不依赖）
    └─ src/main/java/com/powergateway/biz/
        ├─ BizMenuAutoConfiguration.java
        │       @ConditionalOnProperty(prefix="biz.menu", name="enabled", havingValue="true")
        ├─ controller/
        │   ├─ BusinessMenuGeneratorController.java
        │   ├─ BusinessAppController.java
        │   ├─ BusinessInterfaceExecController.java
        │   └─ BusinessOpLogController.java
        ├─ service/
        │   ├─ BusinessMenuGeneratorService.java
        │   ├─ InterfaceMetadataExtractor.java
        │   ├─ BusinessInterfaceInvokerService.java
        │   ├─ BusinessOpLogService.java
        │   └─ MenuStatusSyncListener.java  (@EventListener)
        └─ context/
            └─ TraceIdContext.java           (ThreadLocal)
```

### 3.2 前端目录

```
frontend/src/features/biz-menu/
├─ index.js                       入口，按 window.__PG_FEATURES__.bizMenu 决定 register 路由
├─ router.js                      同 modules/testkit 模式
├─ views/                         BusinessMenuGenerator / BusinessAppList / BusinessMenuTree /
│                                 BusinessOpLog / BusinessOpLogDetail / BusinessInterfacePage
├─ components/
│   ├─ InterfaceStatusTag.vue     生成+发布双徽章
│   ├─ StatusPieChart.vue         统计饼图
│   ├─ TraceLinkView.vue          trace 全链路时间线
│   └─ DynamicFormRenderer.vue    核心：按接口字段元数据渲染表单
└─ api/bizMenu.js
```

### 3.3 双层裁剪

| 层 | 实现 | 效果 |
|---|---|---|
| **编译期** | Maven profile `-Pbiz-menu`，未启用则模块不打进 jar | 产物包体积不涨；主 jar 无 biz 相关类 |
| **运行期** | `sys_config.biz.menu.enabled` + `@ConditionalOnProperty` | 已打包但运行时可禁用；重启生效 |

### 3.4 关闭时的完整关闭面（`biz.menu.enabled=false`）

**核心原则**：一开关关掉，用户看不到任何 FN-BIZ 相关 UI/API/数据，主系统零残影。

**后端**：
- 所有 pg-biz-menu Bean 不实例化
- `/api/biz/**` 全部 404
- `MenuStatusSyncListener` 不注册，主模块发的事件被忽略
- `sys_menu` / `business_op_log` 表数据保留（不物理清理），后端 `GET /api/menu/tree` 过滤 `origin IN (BIZ, BIZ_APP)` 不返回
- 三张日志表 trace_id 列保留（不 drop），关闭时不写值

**前端**（所有元素统一由 `$features.bizMenu` 控制）：
- 侧栏"业务应用" / "业务菜单生成中心" / "业务操作监控"三级菜单不渲染
- 首页 DashboardView 业务菜单 4 卡片不渲染，不发请求
- 首页应用分布饼图不渲染
- 接口配置页"生成业务菜单"按钮 v-if 隐藏
- 接口发布页 `default_app_menu_id` 下拉字段 v-if 隐藏
- 前端路由 `/biz/**` 由 vite build 按 env tree-shake 完全剔除
- 业务操作监控相关全部报表隐藏

**开关传递**：

```
sys_config.biz.menu.enabled=true/false
    │
    ├─ 后端：@ConditionalOnProperty 控制 Bean 装配
    │
    └─ 前端：登录后 GET /api/config/features → {bizMenu: true/false}
             ├─ Pinia store: features.bizMenu
             ├─ 全局注入 $features
             └─ 所有相关组件 v-if="$features.bizMenu"
```

**运维验证**：关闭开关 → 重启后端 → 全站扫查看不到 FN-BIZ 痕迹。发版前必测项。

---

## 四、后端 API

| 方法 & 路径 | 用途 |
|---|---|
| `GET /api/biz/generator/candidates` | 拉已发布 4 类 CRUD 接口清单，含生成状态 + 发布状态 |
| `GET /api/biz/generator/stats` | 首页/生成中心统计（总接口/已生成/覆盖率/按 app 分布饼图） |
| `POST /api/biz/generator/generate` | 批量生成 body: `{interfaceIds:[], appMenuId, groupMenuId?}` |
| `POST /api/biz/generator/regenerate` | 重新生成（保留 display_props） body: `{menuIds:[]}` |
| `POST /api/biz/generator/purge-orphans` | 一键清理已孤立菜单 |
| `GET/POST/PUT/DELETE /api/biz/apps` | 业务应用 CRUD（origin=BIZ_APP 的 sys_menu） |
| `POST /api/biz/exec/{menuId}` | **通用业务接口调用入口** —— 内部转发 `/api/exec/{interfaceId}` + trace_id + 报文录制 |
| `GET /api/biz/oplog` | 分页查 business_op_log（用户/菜单/时间筛选） |
| `GET /api/biz/oplog/{traceId}/full` | 按 trace_id 反查四表拼完整链路 |
| `GET /api/biz/oplog/stats` | 24h 业务操作数、Top10 高频菜单、异常率 |

---

## 五、前端页面

| 路径 | 组件 | 内容 |
|---|---|---|
| `/biz/generator` | `BusinessMenuGenerator.vue` | 生成中心（接口清单左 + 状态列中 + 统计卡右 + 底部批量操作） |
| `/biz/apps` | `BusinessAppList.vue` | 业务应用管理 |
| `/biz/apps/:appId/menus` | `BusinessMenuTree.vue` | 业务应用内菜单树编辑（拖拽/建分组/改名/图标） |
| `/biz/oplog` | `BusinessOpLog.vue` | 业务操作日志列表 |
| `/biz/oplog/:traceId` | `BusinessOpLogDetail.vue` | 单条 trace 全链路详情 |
| `/biz/menu/:menuId` | `BusinessInterfacePage.vue` | **通用业务接口页**（所有 origin=BIZ 菜单路由到此） |

### 5.1 BusinessInterfacePage.vue · 通用页面模板

```
┌─────────────────────────────────────────────┐
│ [接口信息区]                                 │
│  名称 / 类型 / 地址 / 字段规格 / 调用统计    │
├─────────────────────────────────────────────┤
│ [请求参数区 · 动态表单]                      │
│  根据接口的请求字段元数据自动渲染输入框      │
│  含字典字段的下拉框（FN-12 联动）           │
├─────────────────────────────────────────────┤
│  [发起请求]   [清空]                         │
├─────────────────────────────────────────────┤
│ [响应区]                                    │
│  SELECT → 表格 / 列表                        │
│  INSERT/UPDATE/DELETE → 响应信息回显框       │
└─────────────────────────────────────────────┘
```

**元数据来源**：从 `sys_menu.interface_id` 反查 `interface_config.config_json`，`InterfaceMetadataExtractor` 提取字段列表 + 每字段的 dict_key（若有 DICT_MAP 加工规则）。

### 5.2 首页 DashboardView 新卡片

现有 AUX-2 布局基础上追加：

```
既有: 5 卡片 + 3 图表 + Top5 + 告警
新增: 业务菜单 4 项 (v-if="$features.bizMenu")
  ├─ 业务菜单总数
  ├─ 已生成覆盖率
  ├─ 24h 业务操作数
  └─ 应用分布饼图
```

---

## 六、关键流程

### 6.1 菜单生成 / 重新生成

```
admin 生成中心多选接口 → POST /api/biz/generator/generate
    │
    ▼
BusinessMenuGeneratorService.generate()
    │
    ├─ 事务开始
    │  for each interfaceId:
    │    ├─ 查 interface_config
    │    ├─ InterfaceMetadataExtractor 提字段列表 + 计算 metadata_hash (SHA-256)
    │    ├─ 查 sys_menu WHERE interface_id=? AND app_menu_id=?
    │    │    ┌─ 存在 → UPDATE metadata_hash + status=NORMAL，保留 display_props
    │    │    └─ 不存在 → INSERT，name 按规则默认（select_customer→查询客户），
    │    │                icon 按 op_type，badge=[业务]/success
    │    └─ 打 sys_log + business_op_log (op_type=MENU_GENERATE)
    │
    ├─ 事务提交
    └─ 广播 MenuGeneratedEvent → SSE 推送侧栏刷新
```

**边界**：同一 interface_id + 不同 app_menu_id 可存多行（G4 一对多）；批次内单条失败不影响整批。

### 6.2 接口变更 → 菜单状态自动同步

**事件驱动，不轮询**：

```
InterfaceConfigService.save/updateStatus/delete
    │
    └─ 发布 InterfaceConfigChangedEvent(interfaceId, changeType)
         │
         ▼
    MenuStatusSyncListener (在 pg-biz-menu)
    @TransactionalEventListener(AFTER_COMMIT)
    @Async
        │
        ├─ FIELD_MODIFIED    → 重算 hash，diff 则 UPDATE status='EXPIRED'
        ├─ STATUS_DISABLED   → UPDATE status='DISABLED'
        ├─ STATUS_PUBLISHED  → 重算 hash 决定 NORMAL 或 EXPIRED
        └─ DELETED (软删)    → UPDATE status='ORPHAN'
```

**跨模块解耦**：backend 主模块发事件，pg-biz-menu 作为可选监听方。开关关闭时监听器 Bean 不注册，事件被吞掉，主模块无感知。

### 6.3 trace_id 全链路串联

```
浏览器点击 [新增客户] → POST /api/biz/exec/501 (menuId=501)
    │
    ▼
BusinessInterfaceExecController.exec
    │
    ├─ TraceIdContext.generate() → 1743891234000001 塞 ThreadLocal
    │
    ├─ BusinessInterfaceInvokerService.invoke(501, body)
    │    ├─ 查 sys_menu 501 → 拿 interface_id=42
    │    │  若 status != NORMAL → 400 "菜单不可用"
    │    ├─ 字典反向映射（"男" → M，若启用 FN-12）
    │    ├─ 转发 /api/exec/42（M2-7 现有入口，不改动）
    │    │    │
    │    │    ├─ SqlAuditAspect  → 读 trace_id → 异步入 sql_audit_log
    │    │    ├─ PerfStatAspect  → 读 trace_id → 异步入 perf_stat
    │    │    └─ SysLogAspect    → 读 trace_id → 异步入 sys_log
    │    │
    │    └─ 拿到 response
    │
    ├─ BusinessOpLogService.record → 异步入 business_op_log
    │  (含 req_body, resp_body, elapsed)
    │
    ├─ TraceIdContext.clear() (关键，防泄漏；Filter 层兜底)
    │
    └─ 返回响应 + Header X-Trace-Id: 1743891234000001
```

**反查全链路**（`GET /api/biz/oplog/{traceId}/full`）：4 张表按 trace_id join，前端 `TraceLinkView.vue` 时间线渲染。

### 6.4 全链路容错

| 环节 | 失败处理 |
|---|---|
| 生成批次单条失败 | fail-fast=false，返回 `[{id, ok, err}]` |
| 事件监听器异常 | `AFTER_COMMIT + @Async`，异常打 sys_log 不 rollback 主流程 |
| ThreadLocal 泄漏 | Controller try-finally + Filter 兜底 clear |
| 报文超阈值 | `biz.menu.oplog.body.max.kb=256` 截断存储 |
| trace_id 冲突 | snowflake 全局唯一，冲突即系统 bug 告警 |
| 未启用 FN-BIZ 但事件仍发 | 主模块无条件发事件，pg-biz-menu 关闭 = 无监听 = 零开销 |
| 异步写队列满 | LinkedBlockingQueue 满时降级：只保留 trace_id 元数据、丢报文体 |

---

## 七、复用与依赖

### 7.1 已交付单元复用

| 组件 | 单元 | 用途 |
|---|---|---|
| `interface_config.config_json` | M2-3/4/5/6 | 字段元数据来源 |
| `POST /api/exec/{interfaceId}` | M2-7 | 底层执行入口，不改动 |
| `DictMappingProcessor` | FN-12 | 字典下拉 + 反向映射 |
| `QueryCacheManager` | M2-10 | SELECT 类可选走缓存 |
| `SqlAuditAspect / SysLogAspect / PerfStatAspect` | M2-9 / SYS-1 / SYS-2 | 透过 ThreadLocal 读 trace_id |
| `ConditionBuilder.vue` | M2-3 | DynamicFormRenderer 内部渲染筛选组件 |
| `DashboardView.vue` | AUX-2 | 首页卡片挂载点 |
| `sys_menu / sys_role / sys_role_menu` | SYS-3 升级 | 权限模型统一（本单元不新建） |

### 7.2 sys_config 新增键

| key | 默认 | 说明 |
|---|---|---|
| `biz.menu.enabled` | `false` | 总开关 |
| `biz.menu.oplog.body.max.kb` | `256` | 报文截断阈值 |
| `biz.menu.oplog.retention.days` | `90` | business_op_log 保留天数 |
| `biz.menu.grant.audit.enabled` | `true` | 授权动作强制记入 sys_log |

---

## 八、测试策略

### 8.1 后端测试增量（预估 +45~65 用例）

| 类 | 用例数 | 覆盖 |
|---|---|---|
| `BusinessMenuGeneratorServiceTest` | ~15 | 新生成/重新生成 hash 检测/display_props 保护/多应用一对多/失败降级 |
| `InterfaceMetadataExtractorTest` | ~8 | 4 类接口字段提取/字典关联/hash 计算稳定性 |
| `BusinessInterfaceInvokerServiceTest` | ~10 | trace_id 上下文/字典反向映射/status 校验/字段验证 |
| `BusinessOpLogServiceTest` | ~6 | 异步落库/报文截断/队列满降级 |
| `MenuStatusSyncListenerTest` | ~8 | 四种事件类型的状态转移/AFTER_COMMIT 保证 |
| `TraceIdContextTest` | ~6 | ThreadLocal 生成/传递/clear/Filter 兜底 |
| `BizMenuAutoConfigurationTest` | ~5 | 开关 on/off 下 Bean 装配差异 |

### 8.2 前端测试增量（预估 +15 用例）

- `BusinessMenuGenerator.spec.js`：多选生成 / 状态列徽章 / 统计卡片
- `DynamicFormRenderer.spec.js`：按字段类型渲染 / 字典下拉
- `TraceLinkView.spec.js`：时间线渲染 / 空数据兜底
- `Dashboard.spec.js`：`$features.bizMenu=false` 时业务卡片不渲染

### 8.3 手工验证

1. 开关 off 下全站扫查，验证零残影
2. admin 生成一批业务菜单 → USER 登录看到并操作
3. 修改接口字段 → 菜单标"已过期" + 页面运行时元数据自动同步
4. disable 接口 → 菜单从 USER 侧栏隐藏
5. 一次业务操作后走 `GET oplog/{traceId}/full` 拉全链路
6. 生成中心统计饼图与首页饼图数据一致

---

## 九、风险与开放问题

### 9.1 风险清单

| 序 | 风险 | 缓解 |
|---|---|---|
| R1 | ThreadLocal trace_id 泄漏跨请求串号 | Filter 兜底 clear + 单测断言 |
| R2 | business_op_log 报文太大撑爆表 | `body.max.kb=256` 截断 + 定期归档 |
| R3 | 事件监听器异步失败菜单状态漂移 | AFTER_COMMIT 保证一致性 + 每天定时全量 hash 对账 |
| R4 | 字典字段被绕过下拉直接改 URL 提交系统码 | 后端 Invoker 二次校验字典合法性 |
| R5 | pg-biz-menu 独立模块测试回归成本上升 | Maven aggregator `mvn test -P all-modules` 一命令跑全模块 |
| R6 | 开关 off 下前端 v-if 判断成本 | 生产 vite build 按 env tree-shake，运行时零成本 |
| R7 | 生成规则不满足复杂场景（如需两步操作） | 明确非目标 —— 一接口=一菜单=一按钮，复杂业务用手写页面 |
| R8 | snowflake 单实例够，集群化需 workerId 配置 | 部署文档标注；v0.5.0 不支持集群 |

### 9.2 开放问题

| Q | 建议 |
|---|---|
| SELECT 类是否默认走 M2-10 缓存？ | 是；UPDATE 类默认不走；`sys_config` 提供每接口开关 |
| 是否要"我的收藏菜单"？ | 非目标 |
| 业务操作日志是否要导出 Excel？ | 是，沿用 SYS-1 模式，同一版本内实现 |
| 业务应用是否需要独立品牌化（logo/主题色）？ | 非目标，走 PG 全局主题 |
| 支持业务菜单挂 M1-x 报文转换接口吗？ | 非目标（明确只 4 类 CRUD） |

---

## 十、v0.5.0 交付物清单

### 代码

- `pg-biz-menu/` 独立子模块（后端）
- `frontend/src/features/biz-menu/` 独立目录（前端）
- 首页 DashboardView 卡片挂载改造
- 接口发布页 `default_app_menu_id` 下拉字段（v-if 保护）
- Maven aggregator profile `biz-menu`
- `db/migration/V2026072603__business_op_log.sql`
- `db/migration/V2026072604__add_trace_id_columns.sql`
- `db/migration/V2026072605__interface_config_default_app.sql`

### 文档

- 本设计文档
- [SYS-3 升级设计](./2026-07-26-SYS-3-upgrade-design.md)（伴生前置）
- `docs/03-开发/变更记录.md` 新增 CHG-XXX 记录 v0.5.0 全部范围变更
- `docs/01-需求/需求拆分与最小实现方案.md` 新增 FN-BIZ 单元段
- `docs/03-开发/开发计划.md` 新增 FN-BIZ 行
- `docs/06-项目管理/路线图.md` 新增 v0.5.0 段（单一大版本仅做 FN-BIZ + SYS-3 升级）
- [反馈簿 FB-041](../../06-项目管理/反馈簿.md#fb-041) 状态回写 ✅
- [待办与缺陷池 CR-002](../../06-项目管理/待办与缺陷池.md#cr-002) 状态回写 ✅

### 测试

- 后端 +45~65 用例
- 前端 +15 用例
- 完整回归 555 + 新增 + FN-12 增量 = 预计 ~660 用例全绿
- 手工验证 checklist

### 发版

- `git tag -a v0.5.0 -m "PowerGateway v0.5.0 · FN-BIZ 业务菜单生成能力 + SYS-3 权限升级"`
- `git push origin master v0.5.0`
- 更新路线图"已发布"表
- v0.5.0-基线.md 新建
