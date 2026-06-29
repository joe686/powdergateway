# PowerGateway

低代码/零代码可视化接口开发平台。通过界面配置替代手工编写接口代码，将单个接口的开发工时压缩到 **10 分钟以内**。

> 全部 28 个核心交付单元已完成，326 个测试全绿。详见 [docs/03-开发/开发计划.md](docs/03-开发/开发计划.md)。

## 仓库结构

```
powergateway/
├── backend/                 Spring Boot 主后端（端口 8080）
├── frontend/                Vue 3 + Vite 前端（端口 5173）
├── pg-testkit/              独立测试工具，内嵌 Mock 服务器（端口 8081 / 9999）
├── scripts/                 一键启停脚本（start/stop × bat/sh/ps1）
├── powergateway-analysis/   竞品分析报告（独立产物，与主代码无依赖）
├── docs/                    项目文档（按需求/设计/开发/测试分目录）
│   ├── 01-需求/
│   ├── 02-设计/
│   ├── 03-开发/
│   └── 04-测试/
├── CLAUDE.md                Claude Code / Anthropic 工具的项目指令
└── README.md                你正在看的文件
```

三个模块（`backend/`、`frontend/`、`pg-testkit/`）互不 import 内部类，仅通过 HTTP/JDBC 集成。

## 文档导航

| 目录 | 主题 | 关键入口 |
|------|------|---------|
| [01-需求/](docs/01-需求/) | 需求 | [产品需求说明书](docs/01-需求/可视化接口程序产品需求说明书.md)、[需求拆分](docs/01-需求/需求拆分与最小实现方案.md) |
| [02-设计/](docs/02-设计/) | 设计 | [架构说明](docs/02-设计/架构说明.md)、[实现范式](docs/02-设计/实现范式.md)、[详细设计](docs/02-设计/详细设计/) |
| [03-开发/](docs/03-开发/) | 开发 | [开发计划](docs/03-开发/开发计划.md)、[变更记录](docs/03-开发/变更记录.md)、[问题清单](docs/03-开发/问题清单.md) |
| [04-测试/](docs/04-测试/) | 测试 | [TDD 规范](docs/04-测试/TDD规范.md)、[测试案例](docs/04-测试/测试案例.md)、[E2E 验证报告](docs/04-测试/E2E验证报告-2026-06-29.md) |

## 功能概览

### 模块一：报文格式转换

| 功能 | 说明 |
|------|------|
| 格式互转 | JSON ↔ XML ↔ CSV ↔ FormData，12 种组合 |
| 字段映射 | 拖拽配置源字段 → 目标字段映射关系 |
| 字段加工 | 截取、补位、大小写、类型转换、四则运算，多规则叠加 |
| 渠道模板 | 按报文中的渠道标识字段自动匹配转换模板 |
| 端口分发 | 双向转发：请求加工 → 转发目标端口 → 应答加工返回，支持 GBK/UTF-8 编码转换 |

### 模块二：可视化接口开发

| 功能 | 说明 |
|------|------|
| 查询接口 | 多表 LEFT JOIN，可视化 WHERE 条件，字段加工 |
| 插入接口 | 多表（最多 3 张），三种字段来源：请求参数/固定值/运算表达式，事务回滚 |
| 修改接口 | 强制唯一条件校验，修改前快照写入审计日志 |
| 删除接口 | 多表（最多 3 张），批量删除保护开关，删除前预览待删数据 |
| 接口发布 | `draft → published → disabled` 状态机，统一执行入口 `POST /api/exec/{id}` |
| SQL 审计 | 所有增删改异步写入独立审计库，含操作人、IP、执行结果、修改前数据 |
| 双层缓存 | Caffeine（本地）→ Redis（分布式）→ DB，分布式锁防击穿 |
| 分库分表 | 取模 / 范围 / 补查三种路由策略 |

### 模块三：系统管理

| 功能 | 说明 |
|------|------|
| 用户与权限 | Sa-Token + JWT，角色菜单白名单 |
| 系统配置 | `sys_config` KV，`ApplicationEvent` 热更新传播 |
| 日志管理 | 后台操作日志（`sys_log`），异步写入 |
| 性能统计 | 接口调用次数/耗时（`perf_stat`），ECharts 可视化 |
| 接口配置向导 | 九步图形化向导（SYS-5），覆盖查询/插入/修改/删除全流程 |
| 报文调试 | AUX-1 在线调试工具 |
| 首页概览 | AUX-2 仪表盘 |

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 2.7 / JDK 1.8 |
| ORM | MyBatis-Plus 3.5 |
| 认证 | Sa-Token + JWT |
| 缓存 | Redis 6.2（分布式）+ Caffeine（本地双层） |
| 报文解析 | Jackson（JSON）、Dom4j（XML）、OpenCSV（CSV） |
| 前端框架 | Vue 3 + Vite |
| UI | Element Plus |
| 状态管理 | Pinia |
| 拖拽 | vue-draggable-next |
| 图表 | ECharts |
| 支持数据库 | MySQL 8.0+、Oracle 11g+、PostgreSQL 12+ |

完整版本清单见 [docs/02-设计/技术栈总结.md](docs/02-设计/技术栈总结.md)。

## 本地开发

**前置条件**：JDK 1.8+、Maven 3.6+、Node 18+、MySQL 8.0、Redis 6.2

### 一键启停（推荐）

```bash
# Windows
scripts\start.bat       # 启动 backend + frontend
scripts\stop.bat        # 停止全部

# macOS / Linux
./scripts/start.sh
./scripts/stop.sh
```

详见 [scripts/README.md](scripts/README.md)。

### 手动启动

```bash
# 1. 初始化配置库（约 9 张表）
mysql -u root -p your_db < backend/src/main/resources/db/init.sql

# 2. 启动后端（端口 8080）
cd backend
# 修改 src/main/resources/application.yml 中的 MySQL/Redis 连接信息
mvn spring-boot:run
# Swagger: http://localhost:8080/swagger-ui.html

# 3. 启动前端（端口 5173，代理到 8080）
cd frontend
npm install
npm run dev

# 4. （可选）启动测试工具（端口 8081，需 backend 已起）
cd pg-testkit
mvn spring-boot:run
```

默认管理员账号：`admin` / `Admin@123`

> **端口固定，不得变更**：backend `8080`、frontend `5173`、pg-testkit `8081 / 9999`。若端口被占用，应终止占用进程而非改端口。

### 运行测试

```bash
cd backend
mvn test                                       # 全量测试（H2 内存库，无需外部 MySQL/Redis）
mvn test -Dtest=M17PortRouteTest               # 单个测试类
mvn test -Dtest=M17PortRouteTest#methodName    # 单个测试方法
```

> 所有测试类必须加 `@ActiveProfiles("test")`，否则会连接生产 MySQL/Redis。详见 [docs/04-测试/TDD规范.md](docs/04-测试/TDD规范.md)。

## 贡献流程

1. 阅读 [docs/04-测试/TDD规范.md](docs/04-测试/TDD规范.md) — TDD 强制
2. 阅读 [docs/02-设计/实现范式.md](docs/02-设计/实现范式.md) — 复用现有组件，禁止重复实现
3. 范围变更走 [docs/03-开发/变更记录.md](docs/03-开发/变更记录.md) 三步同步规约
