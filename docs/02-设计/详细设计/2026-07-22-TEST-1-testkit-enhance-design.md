# TEST-1 · 测试工具改造 · 详细开发计划

> 文档日期：2026-07-22
> 作者：Claude Opus 4.7
> 状态：**详细开发计划，待用户批准后开工**
> 上游方案：`docs/04-测试/测试工具改造-用户自测方案.md`
> 用户回复决策点：
> 1. 样例业务库用 **MySQL**（简单）
> 2. 前端**嵌入 PG 前端**，专门做「测试用户角色」权限控制；相关前端资源放在**独立目录**，后续可整目录删除；后端支持"要不要都行"
> 3. Mock 规则 v1 手动配置
> 4. 样例接口覆盖全功能（QUERY/INSERT/UPDATE/DELETE/分库分表/缓存）
> 5. **保留数据生成方案** + **清库机制** + 为后续适配其他 DB 留扩展点
> 6. Mock 端口可配（`application.yml`）

---

## 一、目标与范围

**目标**：把 `pg-testkit` 从"AI 驱动测试的 API 骨架"升级为"用户可以在 PG 主界面里点点鼠标就跑起来的自测环境"，并支持样例业务库和可视化 Mock 后端两大能力。

**范围包含**：
- 前端「测试」菜单 + 「测试用户」角色白名单双控可见性
- 前端独立目录 `frontend/src/modules/testkit/`（后期删除整目录即可下线）
- 后端保留 `pg-testkit` 独立服务（8081 端口），主 backend **零耦合**
- 样例业务库 schema + Faker 数据生成器 + 幂等初始化 / 清库 API
- 10 个样例接口配置的自动灌注脚本
- 可视化 Mock 后端 GUI（规则管理 + 请求历史）
- 数据生成方案 + 清库机制 + 数据库类型扩展点

**范围不含**（v2 或永不做）：
- Mock 规则「录制回放」（v2）
- 样例业务库支持 Oracle / PostgreSQL（v1 只做 MySQL，扩展点留出即可）
- 报文比对断言可视化（v2）
- 主 PG 前端 iframe 嵌入 pg-testkit 前端（不做，直接用 API 通信）

---

## 二、决策落地：菜单和权限双控可见性

### 2.1 前端目录结构

```
frontend/src/
├── modules/
│   └── testkit/                         # ★ 独立模块，后期整目录删除即可下线
│       ├── views/
│       │   ├── DemoDbManage.vue         # 样例数据库管理
│       │   ├── MockServerRules.vue      # Mock 规则列表 + 编辑
│       │   └── MockServerHistory.vue    # 请求历史
│       ├── api/
│       │   └── testkit.js               # 调 pg-testkit 8081 (代理)
│       ├── router.js                    # 导出路由数组，供主 router 合并
│       └── menu.js                      # 导出菜单项，供 SideMenu 合并
├── router/index.js                       # 只加 3 行：条件加载 testkit/router.js
└── components/layout/SideMenu.vue        # 只加 3 行：条件合并 testkit/menu.js
```

**下线时**：删除 `frontend/src/modules/testkit/` 整个目录 + 移除 `router/index.js` 和 `SideMenu.vue` 里两处 3 行代码 → 上线包完全不含测试模块代码。

### 2.2 前端加载条件

`router/index.js` 里：
```js
// 只在开发环境或存在测试角色时加载
if (import.meta.env.MODE === 'development' || __TESTKIT_ENABLED__) {
  const testkitModule = await import('@/modules/testkit/router.js')
  routes.push(...testkitModule.default)
}
```
- `__TESTKIT_ENABLED__` 由 Vite `define` 从环境变量注入，`build` 时可裁剪（tree-shaking 干掉）
- 生产环境构建脚本默认 `TESTKIT_ENABLED=false`，构建产物不含测试模块 chunk

### 2.3 后端角色权限

- `sys_role` 新增 `TESTER` 角色（不属于 ADMIN/USER/READONLY，独立并列）
- `MenuPermission.java` 新增 `TESTER_MENUS`，含：`/testkit/demo-db`、`/testkit/mock-rules`、`/testkit/mock-history`
- 生产环境**不预置 tester 用户**，只有开发/试用时手动通过用户管理页面新建 tester 用户；不新建 → 无人能进入测试菜单
- **上线安全承诺**：pg-testkit 后端服务不启动 + 前端无 tester 用户 = 测试模块彻底不可达

---

## 三、后端 · `pg-testkit` 增强

### 3.1 保留 8081 独立进程

用户决策"要不要都行" → 我推荐**保留独立**，理由：
- 已有 `pg-testkit` 模块骨架和 README，代码路径清晰
- 8081 独立端口便于运维（关掉端口 = 关掉测试环境）
- 主 backend 零侵入，符合项目"三模块互不 import 内部类"的架构约束（`CLAUDE.md` §项目模块）

主 PG 前端通过 `vite.config.js` 增加 dev 代理：`/testkit/*` → `http://localhost:8081/testkit/*`；生产环境的部署方案由 G 组决定（便携版可直接把 pg-testkit.jar 一并起，标准版按需装）。

### 3.2 样例业务库

#### 3.2.1 数据库定位
- 数据库名：`pg_demo_biz` + `pg_demo_biz_shard_01/02/03`
- DB 类型：**MySQL**（v1 唯一），预留策略模式接口 `DemoDbInitializer`，v2 可扩展 `MySqlDemoDbInitializer` / `OracleDemoDbInitializer` / ...

#### 3.2.2 表结构（10 张，覆盖所有可视化接口能力）
按上游方案 §3.2 表格；SQL 文件位置：`pg-testkit/src/main/resources/demo-db/schema-mysql.sql`

#### 3.2.3 数据生成方案（**保留** · 用户决策点 5）
```
pg-testkit/src/main/java/com/powergateway/testkit/demo/
├── DemoDbInitializer.java               # 接口
├── mysql/
│   ├── MySqlDemoDbInitializer.java      # 实现
│   ├── DdlLoader.java                   # 读 schema-mysql.sql 执行
│   └── DataFaker.java                   # Faker + 语义规则
├── DemoDbController.java                # /testkit/demo-db/{init,reset,drop,stats}
└── resources/
    ├── schema-mysql.sql
    └── demo-interfaces-seed.json        # 10 个样例接口的配置 JSON
```

**幂等 + 可清库**：
- `POST /testkit/demo-db/init`：先检测 `demo_user` 表是否存在，不存在则建表 + 灌数据；存在则跳过（可加 `?force=true` 强制重灌）
- `POST /testkit/demo-db/reset`：`TRUNCATE` 所有 demo_* 表后重灌数据
- `POST /testkit/demo-db/drop`：`DROP DATABASE pg_demo_biz*`（**危险操作**，前端二次确认）
- `GET /testkit/demo-db/stats`：返回每张表行数

**Faker 数据规则**（`DataFaker.java`）：
- 姓名：`com.github.javafaker.Faker(Locale.CHINA)`
- 手机：13/15/17/18 开头 11 位随机
- 金额：正态分布 μ=5000 σ=2000，取正数
- 生日：18-70 岁均匀分布
- 交易时间：过去 90 天均匀分布

**分库分表 demo 数据**：`demo_txn.user_id % 3` 决定物理库；生成器按此规则写入正确 shard。

#### 3.2.4 样例接口灌注
`demo-interfaces-seed.json` 存 10 个 InterfaceConfig 的 JSON；`POST /testkit/demo-db/init` 同步调用 PG 的 `POST /api/interface/save` 灌入 PG 配置库（需鉴权，用启动脚本里配置的 admin token）。

**注意**：为避免污染用户配置，样例接口全部以 `DEMO_` 前缀命名，且 `MetaTag` 打上 `SAMPLE=true`；PG 「接口管理」页面加个"隐藏样例接口"筛选开关（默认不隐藏，方便测试用户）。

### 3.3 可视化 Mock 后端

#### 3.3.1 端口配置
`pg-testkit/src/main/resources/application.yml`：
```yaml
testkit:
  mock-server:
    port: ${MOCK_SERVER_PORT:9999}
    persist-history: true
    history-max: 10000
    data-dir: ${TESTKIT_DATA_DIR:./pg-testkit/data}
```

#### 3.3.2 后端引擎
沿用现有 `MockServerService` 骨架，补充：
- 规则持久化：`{data-dir}/mock-rules.json`（每次改动 flush）
- 请求历史：`{data-dir}/mock-history.db`（SQLite），字段：`id / recv_at / method / path / headers / body / matched_rule_id / response_status / response_body / cost_ms`
- 匹配算法：Method 精确 + Path 支持 `/api/**` 通配 + Body 支持 `contains`/`jsonPath`/`regex` 三选一

#### 3.3.3 API 端点（供前端调用）
- `GET/POST/PUT/DELETE /testkit/mock/rules` — 增删改查规则
- `GET /testkit/mock/rules/{id}` — 详情
- `POST /testkit/mock/rules/reorder` — 调整规则优先级
- `GET /testkit/mock/history?page&size&pathLike` — 分页查历史
- `GET /testkit/mock/history/{id}` — 单请求详情（含完整 body）
- `DELETE /testkit/mock/history` — 清空
- `POST /testkit/mock/history/{id}/replay` — 从历史回放（发起一次一模一样的请求到主 PG，方便验证 fix）

### 3.4 pg-testkit 独立启动脚本

`pg-testkit/scripts/start-testkit.bat`（Win）/ `start-testkit.sh`（Linux）：
```bash
java -jar pg-testkit.jar \
  --server.port=8081 \
  --testkit.mock-server.port=9999 \
  --testkit.data-dir=./data \
  --spring.datasource.url=jdbc:mysql://localhost:3306/pg_demo_biz?... \
  --pg.admin.token=... # 用于灌注样例接口时鉴权
```

---

## 四、前端 · 测试模块 3 个视图

### 4.1 `DemoDbManage.vue` — 样例数据库管理

- 卡片式布局：显示 10 张 demo 表的行数
- 顶部按钮：`初始化`、`重置（清空重灌）`、`删除（DROP）`
- 底部：显示 10 个样例接口清单 + 「打开接口详情」链接（跳到主 PG 的接口管理页）

### 4.2 `MockServerRules.vue` — Mock 规则管理

按上游方案 §4.2 GUI 草图：左规则列表 + 右详情编辑；规则条目支持拖拽调整优先级（用 vue-draggable-next，遵守项目铁律：只用 default slot）

### 4.3 `MockServerHistory.vue` — 请求历史

- 表格：时间 / Method / Path / 状态 / 耗时 / 详情按钮
- 筛选：Path 模糊搜索 + 状态码过滤 + 时间范围
- 详情弹窗：完整 Header + Body（用 Monaco Editor 高亮 JSON/XML）+「回放」按钮

---

## 五、验收标准（独立测试用户完整流程）

`pg-testkit` 启动 + PG 主项目启动 + 前端 dev 模式 + 手动创建 `tester1/Test@123` 测试用户，登录后应能完成：

1. 侧边栏看到「测试环境」菜单组（含 3 个子项）
2. `样例数据库管理` → 点「初始化」→ 30s 内完成，看到 `demo_user=1000` 等统计
3. 打开主 PG「接口管理」→ 看到 10 个 `DEMO_*` 样例接口
4. 直接调用 `DEMO_USER_QUERY` (userNo=U000001) → 返回预期结果
5. `Mock 规则管理` → 加一条 `POST /cbs/query` → 返回 `{"code":0,"balance":12345}`
6. 主 PG 配 port_route 指向 `http://localhost:9999/cbs/query` → 转接口调用 → 主 PG 收到 mock 响应
7. `Mock 请求历史` → 看到 PG 发过来的请求，点回放 → 验证 100% 复现
8. 用非 tester 账号登录（普通 admin） → **看不到**「测试环境」菜单
9. 停 pg-testkit 服务 → 测试用户登录 → 3 个子页面显示"服务未启动"提示（前端不崩）

---

## 六、工时估算

| 阶段 | 工时 |
|------|------|
| 后端 · 样例业务库 schema + Faker 生成器 + 幂等/reset/drop API | 1.5 天 |
| 后端 · 样例接口灌注脚本（JSON seed + 调 admin API） | 0.5 天 |
| 后端 · Mock 规则引擎（含 SQLite 历史 + 回放接口） | 2 天 |
| 前端 · `DemoDbManage.vue` | 0.5 天 |
| 前端 · `MockServerRules.vue`（编辑 + 拖拽排序） | 1.5 天 |
| 前端 · `MockServerHistory.vue`（含 Monaco + 详情弹窗） | 1 天 |
| 前端 · 独立目录 + 条件加载 + tester 角色 + 权限白名单 | 0.5 天 |
| 联调 + 端到端测试 + 启动脚本 + 文档 | 1 天 |
| **合计** | **~8.5 天** |

---

## 七、依赖与风险

| 项 | 说明 |
|----|------|
| 依赖 | 需 G 组打包方案里预留 `--with-testkit` 参数（用于内部测试包）；数据库需本地或客户端环境有 MySQL 8 |
| 风险 | `demo_txn` 10 万条数据生成耗时约 20~30s，前端要有 loading + WebSocket 进度上报（v1 用轮询 stats API 就够） |
| 风险 | `sys_role.TESTER` 角色引入后所有权限相关测试要复跑（`SYS3PermissionTest` 系列 ~15 个用例，估 1 小时验证） |

---

## 八、待用户明确的次要问题

| # | 问题 | 我的默认建议 |
|---|------|-------------|
| 1 | 是否希望"接口管理"页面加"隐藏 DEMO 接口"筛选开关？ | 加（默认不隐藏，勾选后隐藏） |
| 2 | Mock 规则历史保留多久？超过 max 是删旧的还是拒新的？ | 循环覆盖，默认 max=10000，删最旧的 |
| 3 | tester 用户能否看到系统管理菜单？ | 不能（只有测试三个子菜单 + 主 PG 的接口管理/接口调试；`SYS3PermissionTest` 里明确断言） |
| 4 | 样例业务库 MySQL 连接参数是从 pg-testkit 的 `application.yml` 里读还是 PG 配置库 `db_connection` 里读？ | 从 `application.yml` 读，避免 pg-testkit 依赖 PG 配置库 |
| 5 | 是否需要一键"造出一条完整调用链演示"（造用户+账户+交易一条链） ？ | v1 不做，用户可以自己 Insert 接口造 |

---

## 九、后续演进（v2 及以后）

- v2：Mock 规则支持"录制回放"（拦截真实调用生成规则）
- v2：样例库支持 Oracle 分支（实现 `OracleDemoDbInitializer`）
- v2：报文断言可视化（把 PG 返回 vs 预期做 diff 高亮）
- v3：pg-testkit 的 GUI 可以做成 Electron 独立桌面工具，脱离浏览器

---

## 十、批准清单

用户批准以下几点后即可开工：

- [ ] 前端独立目录 + 条件加载方案确认
- [ ] `TESTER` 角色权限模型确认（生产不预置）
- [ ] `pg-testkit` 保留 8081 独立进程（不合入主 backend）
- [ ] MySQL 唯一支持（策略模式留扩展点）
- [ ] Mock 引擎持久化到 SQLite
- [ ] 上表"待用户明确的次要问题"5 条决策
