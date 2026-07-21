# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 语言与环境

- **对话语言**：所有回复、注释、文档一律使用**中文**
- **操作系统**：Windows 10
- **Shell**：Git Bash（使用 Unix 语法，路径用正斜杠；避免依赖 Windows 原生命令）
- **注意**：awk、sed 等命令在 Git Bash 下路径解析可能有兼容性问题，优先用 Python 或 Java 工具链替代

## 项目定位

PowerGateway 是一个低代码/零代码可视化接口开发平台，核心价值：减少接口开发工时 50% 以上，单个接口转换配置 ~10 分钟内完成。

**当前状态（555 个测试全绿）：**
- ✅ 全部 28 个交付单元已完成（前五个阶段全部交付）
- ✅ 阶段一（P0-1 ～ P0-4）、阶段二（M1-1 ～ M1-7）、阶段三（M2-1/2/3/4/5/6/7/9）
- ✅ 阶段四（M2-10、M2-8、SYS-1、SYS-2、SYS-3、SYS-4）
- ✅ 阶段五（SYS-5 九步向导、AUX-1 报文调试、AUX-2 首页概览）
- ✅ 阶段六（UX-A/B/C/D/E/F 全站体验重塑，2026-07-20 交付，CHG-016 ～ CHG-020）
- 🔵 阶段七（平台支撑能力）**2026-07-22 全部交付（部分 v1.1 增量）**：
  - ✅ **FN-11 扩展**（导入导出 Excel/MD/PathExpression）· CHG-022
  - ✅ **REG-1**（注册中心 Nacos+Eureka + service:// 协议 + 心跳告警）· CHG-023
  - ✅ **TEST-1**（TESTER 角色 + PG 前端嵌入 modules/testkit + pg-testkit DemoDbController 骨架）· CHG-024
        · v1.1 增量：Faker 10 万条数据 + 完整 10 表 DDL + Mock 规则持久化
  - ✅ **REL-1**（Maven profile + Caffeine 降级 + build-portable/standard.sh + jlink + release.yml + verify-artifacts.sh）· CHG-025
        · v1.1 增量：SQLite CI job + Oracle 驱动预置 + Docker
  - **测试规模**：完整回归 555 用例全绿（后端全阶段）+ 前端 build 通过 + pg-testkit compile 通过

> `backend/CLAUDE.md` 包含后端实现细节（测试配置、Schema 约定、依赖版本）；`frontend/CLAUDE.md` 包含前端路由约定、请求链路和新增页面步骤。

## 项目模块

仓库包含三个并列模块，三者均不互相 import 内部类，依靠 HTTP/JDBC 集成：

| 模块 | 说明 | 端口 |
|------|------|------|
| `backend/` | PG 主后端（Spring Boot） | 8080 |
| `frontend/` | PG 前端（Vue 3 + Vite） | 5173 |
| `pg-testkit/` | 独立测试工具，含内嵌 Mock 服务器与 `/test/*` API，供端到端链路测试和 AI 控制测试环境，详见 `pg-testkit/README.md` | 8081 / 9999 |

**参考文档**（文档按需求/设计/开发/测试归到 `docs/` 子目录）：
- 详细产品需求：`docs/01-需求/可视化接口程序产品需求说明书.md`
- 需求最小化拆分（28个交付单元）：`docs/01-需求/需求拆分与最小实现方案.md`
- 分阶段开发计划：`docs/03-开发/开发计划.md`
- **范围变更记录**：`docs/03-开发/变更记录.md`（所有正式交付后的范围增减必须在此存档）
- 架构说明（目录结构/模块/菜单/数据库表）：`docs/02-设计/架构说明.md`
- 核心实现范式（后端/前端模式）：`docs/02-设计/实现范式.md`
- TDD 详细规范：`docs/04-测试/TDD规范.md`
- 各单元详细设计：`docs/02-设计/详细设计/`；各单元任务计划：`docs/03-开发/任务计划/`

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7.x（JDK 1.8+） |
| ORM | MyBatis-Plus |
| 权限认证 | Sa-Token + JWT |
| API文档 | springdoc-openapi（Swagger 3.0） |
| 缓存 | Redis 6.2.x（分布式）+ Caffeine（本地） |
| 消息格式处理 | Jackson（JSON）、Dom4j（XML）、OpenCSV（CSV） |
| 前端框架 | Vue 3 + Vite |
| UI组件库 | Element Plus |
| 状态管理 | Pinia |
| 拖拽组件 | vue-draggable-next |
| 图表 | ECharts |

**支持的目标数据库**：MySQL 8.0+、Oracle 11g+、PostgreSQL 12+

## 本地开发环境

| 服务 | 地址 | 账号 | 密码 |
|------|------|------|------|
| 后端 API | http://localhost:8080 | — | — |
| 前端页面 | http://localhost:5173 | — | — |
| Swagger UI | http://localhost:8080/swagger-ui.html | — | — |
| MySQL | localhost:3306 | root | qwe12345 |
| Redis | localhost:6379 | — | — |

> **端口固定，不得变更**：后端始终使用 8080，前端始终使用 5173。若端口被占用，应终止占用进程而非改端口。管理员账号：`admin` / `Admin@123`。

## 构建与运行命令

```bash
# 后端（backend/）
mvn spring-boot:run                # 本地启动（端口 8080）
mvn clean package -DskipTests     # 打包
mvn test                           # 运行全部测试（H2内存库，无需外部 MySQL/Redis）
mvn test -Dtest=M17PortRouteTest              # 运行单个测试类
mvn test -Dtest=M17PortRouteTest#methodName   # 运行单个测试方法
# 注意：所有测试类必须加 @ActiveProfiles("test")，否则会连接生产 MySQL/Redis

# 前端（frontend/）
npm install && npm run dev         # 本地开发（端口 5173，代理到 8080）
npm run build                      # 生产打包

# 测试工具（pg-testkit/，端口 8081，需 backend 已启动）
mvn spring-boot:run
```

## 关键架构决策

- **三库分离**：配置库（MySQL，存模板/接口配置）、业务库（`dynamic-datasource-spring-boot-starter` 按 dbId 动态切换）、审计库（独立 MySQL，保留1年）
  - 配置库：无注解，默认 `master` 数据源
  - 审计库：`@DS("audit")` 注解，对应独立 MySQL
  - 业务库：`DynamicDataSourceContextHolder.push(dbId)` 在 Service 中动态切换，exec 方法结束后 `pop`
- **双层缓存**：Caffeine → Redis → DB；分布式锁（SET NX PX 3000）防缓存击穿；查询目标 ≤ 300ms，增删改 ≤ 500ms
- **配置驱动 SQL**：接口配置以 JSON 持久化（`interface_config.config_json`），运行时 QueryBuilder/InsertBuilder/UpdateBuilder/DeleteBuilder 动态构造，不存硬编码 SQL
- **统一执行入口**：所有已发布接口通过 `POST /api/exec/{interfaceId}` 调用，状态流转 `draft → published → disabled`
- **异步审计**：AOP @Around 拦截 Executor → LinkedBlockingQueue + @Async 写审计库，不阻塞主业务链路

### 密码安全规约

| 场景 | 算法 | 位置 |
|------|------|------|
| 用户登录密码 | BCrypt | `sys_user.password` |
| 数据源连接密码 | AES-128 | `db_connection.password` |

### AOP 注解速查

| 注解 | 用途 | 切面类 |
|------|------|------|
| `@AuditLog` | SQL 增删改操作审计（写审计库） | `SqlAuditAspect` |
| `@SysLogRecord` | 后台操作日志（写 `sys_log`） | `SysLogAspect` |
| `@PerfStat` | 接口执行性能统计（写 `perf_stat`） | `PerfStatAspect` |

三者均通过 `LinkedBlockingQueue + @Async` 异步写入，不阻塞主链路。

详细模块说明、目录结构、菜单树、数据库表设计见 [`docs/02-设计/架构说明.md`](docs/02-设计/架构说明.md)。

## 跨单元复用规约

开发时**必须**复用以下组件，**不得重复实现**：

| 组件/类 | 实现单元 | 被复用方 |
|---------|---------|---------|
| `FieldProcessor`（字段加工引擎，策略模式） | M1-3 | M2-3/4/5 |
| `DataSourceResolver`（请求字段/固定值/计算值解析） | M2-4 | M2-5 |
| `ColumnValidator`（基于表结构元数据的字段校验） | M2-4 | M2-5 |
| `DatabaseMetaData` 表结构查询（Redis缓存） | M2-2 | M2-3/4/5/6 |
| `sys_config` KV 配置读取 | SYS-4¹ | M2-9、M2-10、SYS-1、SYS-3（日志菜单开关） |
| 条件配置前端组件 `ConditionBuilder.vue` | M2-3 | M2-5、M2-6 |
| `ShardRouter`（分片路由，取模/范围/补查） | M2-8 | M2-8 exec 集成 |
| `MenuPermission`（角色菜单白名单） | SYS-3 | `AuthService.getMenuForCurrentUser()` |
| `InterfaceWizard.vue`（9步接口配置向导） | SYS-5 | 各接口配置页（M2-3/4/5/6） |

> ¹ `sys_config` 表由 P0-3 建立并预置默认 KV（cache ttl、audit 保留天数等），M2-9/M2-10 已直接读取；SYS-4 补充管理 UI 和 `ApplicationEvent` 热更新传播，不影响现有读取逻辑。

## TDD 规范（强制）

开发任何新功能，**必须** Red → Green → Refactor，不得跳步：

1. **Red**：先写测试，运行确认失败
2. **Green**：写最小实现代码，使测试通过
3. **Refactor**：测试全绿后重构，重新运行确认不退化

测试命名：`{单元编号}{功能}Test.java`，**必须加** `@ActiveProfiles("test")`。

详细测试约定、分层策略、验收门槛见 [`docs/04-测试/TDD规范.md`](docs/04-测试/TDD规范.md)。
核心实现范式（后端/前端代码模式）见 [`docs/02-设计/实现范式.md`](docs/02-设计/实现范式.md)。

## 范围变更规约

**凡是对已交付单元的功能范围做任何增减或行为修改，必须同步执行以下三步，缺一不可：**

| 步骤 | 操作 | 目标文件 |
|------|------|---------|
| 1 | 在 `变更记录.md` 新增一条 `CHG-XXX` 记录（含日期、影响单元、变更前后、原因） | `docs/03-开发/变更记录.md` |
| 2 | 更新对应单元的范围/不含/实现方案/验收标准 | `docs/01-需求/需求拆分与最小实现方案.md` |
| 3 | 更新开发计划表格中对应行的描述 | `docs/03-开发/开发计划.md` |

> **什么算"范围变更"**：新增功能点、删除功能点、接口行为改变（入参/出参/调用链变化）、前端页面新增交互流程。Bug 修复若不改变对外行为，**不需要**记录变更。
