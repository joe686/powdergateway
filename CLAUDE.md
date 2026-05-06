# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 语言与环境

- **对话语言**：所有回复、注释、文档一律使用**中文**
- **操作系统**：Windows 10
- **Shell**：Git Bash（使用 Unix 语法，路径用正斜杠；避免依赖 Windows 原生命令）
- **注意**：awk、sed 等命令在 Git Bash 下路径解析可能有兼容性问题，优先用 Python 或 Java 工具链替代

## 项目定位

PowerGateway 是一个低代码/零代码可视化接口开发平台，核心价值：减少接口开发工时 50% 以上，单个接口转换配置 ~10 分钟内完成。

**当前状态：阶段一全部完成（P0-1 ～ P0-4），阶段二全部完成（M1-1 ～ M1-7），阶段三全部完成（M2-1、M2-2、M2-9、M2-3、M2-4、M2-5、M2-6、M2-7），阶段四 M2-10 完成，共 241 个测试全绿。下一阶段：阶段四剩余（M2-8、SYS-1 ～ SYS-4）。**

> `backend/CLAUDE.md` 包含后端实现细节（测试配置、Schema 约定、依赖版本）；`frontend/CLAUDE.md` 包含前端路由约定、请求链路和新增页面步骤。

**参考文档**：
- 详细产品需求：`README/可视化接口程序产品需求说明书.md`
- 需求最小化拆分（28个交付单元）：`README/需求拆分与最小实现方案.md`
- 分阶段开发计划：`README/开发计划.md`
- **范围变更记录**：`README/变更记录.md`（所有正式交付后的范围增减必须在此存档）
- 架构说明（目录结构/模块/菜单/数据库表）：`README/架构说明.md`
- 核心实现范式（后端/前端模式）：`README/实现范式.md`
- TDD 详细规范：`README/TDD规范.md`

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
```

## 关键架构决策

- **三库分离**：配置库（MySQL，存模板/接口配置）、业务库（动态连接）、审计库（独立 MySQL，保留1年）
- **双层缓存**：Caffeine → Redis → DB；查询目标 ≤ 300ms，增删改 ≤ 500ms
- **配置驱动 SQL**：接口配置以 JSON 持久化，运行时动态构造 SQL，不存硬编码 SQL
- **异步审计**：LinkedBlockingQueue + @Async，不阻塞主业务链路

详细模块说明、目录结构、菜单树、数据库表设计见 [`README/架构说明.md`](README/架构说明.md)。

## 跨单元复用规约

开发时**必须**复用以下组件，**不得重复实现**：

| 组件/类 | 实现单元 | 被复用方 |
|---------|---------|---------|
| `FieldProcessor`（字段加工引擎，策略模式） | M1-3 | M2-3/4/5 |
| `DataSourceResolver`（请求字段/固定值/计算值解析） | M2-4 | M2-5 |
| `ColumnValidator`（基于表结构元数据的字段校验） | M2-4 | M2-5 |
| `DatabaseMetaData` 表结构查询（Redis缓存） | M2-2 | M2-3/4/5/6 |
| `sys_config` KV 配置读取 | SYS-4 | M2-9、M2-10、SYS-1 |
| 条件配置前端组件 `ConditionBuilder.vue` | M2-3 | M2-5、M2-6 |

## TDD 规范（强制）

开发任何新功能，**必须** Red → Green → Refactor，不得跳步：

1. **Red**：先写测试，运行确认失败
2. **Green**：写最小实现代码，使测试通过
3. **Refactor**：测试全绿后重构，重新运行确认不退化

测试命名：`{单元编号}{功能}Test.java`，**必须加** `@ActiveProfiles("test")`。

详细测试约定、分层策略、验收门槛见 [`README/TDD规范.md`](README/TDD规范.md)。
核心实现范式（后端/前端代码模式）见 [`README/实现范式.md`](README/实现范式.md)。

## 范围变更规约

**凡是对已交付单元的功能范围做任何增减或行为修改，必须同步执行以下三步，缺一不可：**

| 步骤 | 操作 | 目标文件 |
|------|------|---------|
| 1 | 在 `变更记录.md` 新增一条 `CHG-XXX` 记录（含日期、影响单元、变更前后、原因） | `README/变更记录.md` |
| 2 | 更新对应单元的范围/不含/实现方案/验收标准 | `README/需求拆分与最小实现方案.md` |
| 3 | 更新开发计划表格中对应行的描述 | `README/开发计划.md` |

> **什么算"范围变更"**：新增功能点、删除功能点、接口行为改变（入参/出参/调用链变化）、前端页面新增交互流程。Bug 修复若不改变对外行为，**不需要**记录变更。
