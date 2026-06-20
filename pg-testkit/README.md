# PG-TestKit

PowerGateway 独立测试工具，与项目本体（`backend/`、`frontend/`）物理分离。

## 定位

- **独立 Spring Boot 应用**，默认端口 `8081`，不与 PG 后端 `8080` 冲突
- **不依赖 backend jar**，通过 HTTP REST 调用 PG 后端、通过 JDBC 直连配置库/审计库完成校验
- 提供 Mock 上下游系统能力，支持端到端链路测试
- 暴露 `/test/*` 系列 API，供 AI 调用控制测试环境

## 启动顺序

```bash
# 1. 确保 MySQL（powergateway_config + powergateway_audit）和 Redis 已启动
# 2. 启动 PG 后端
cd backend && mvn spring-boot:run          # 端口 8080

# 3. 启动测试工具
cd pg-testkit && mvn spring-boot:run        # 端口 8081
```

## 核心能力

| 模块 | 说明 |
|------|------|
| `mock/MockServerService` | 内嵌 HTTP Mock 服务器（默认 9999 端口），模拟上下游 B 系统 |
| `data/TestDataFactory` | 测试数据生成工厂，构造模板/渠道/接口等配置 JSON |
| `assert/PgAssertions` | PG 专用断言工具，校验响应格式、审计记录、性能统计 |
| `base/IntegrationTestBase` | 全链路测试基类，封装登录→配置→发布→调用→验证流程 |
| `api/TestApiController` | `/test/*` 系列 API，供 AI 启动 Mock、配置场景、查询结果 |

## /test/* API 一览

| API | 方法 | 说明 |
|-----|------|------|
| `/test/mock-server/start` | POST | 启动 Mock 服务器 |
| `/test/mock-server/stop` | POST | 停止 Mock 服务器 |
| `/test/mock-server/configure` | POST | 配置 Mock 响应规则 |
| `/test/mock-server/requests` | GET | 查询收到的请求记录 |
| `/test/mock-server/verify` | POST | 验证请求是否符合预期 |
| `/test/db/query` | POST | 执行 SQL 查询配置库/审计库 |
| `/test/run-scenario` | POST | 执行预设测试场景 |
| `/test/results` | GET | 获取测试结果 |
| `/test/health` | GET | 健康检查 |

## 配置

见 `src/main/resources/application.yml`，关键配置项：

```yaml
pg-testkit:
  server:
    port: 8081                    # 测试工具自身端口
  mock:
    port: 9999                    # Mock 服务器端口
  pg-backend:
    base-url: http://localhost:8080   # PG 后端地址
    username: admin
    password: Admin@123
  db:
    config-url: jdbc:mysql://localhost:3306/powergateway_config
    audit-url: jdbc:mysql://localhost:3306/powergateway_audit
    username: root
    password: qwe12345
```

## 与项目本体的关系

```
powergateway/
├── backend/          # PG 后端（项目本体）
├── frontend/         # PG 前端（项目本体）
└── pg-testkit/       # 独立测试工具（本项目，不引用 backend 内部类）
```

详细设计见 `README/测试工具计划.md`。
