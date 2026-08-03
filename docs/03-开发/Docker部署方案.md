# PowerGateway Docker 部署方案(v0.3.7 · REL-1 补齐)

**版本**:v0.3.7 · **交付**:CHG-047 · **状态**:骨架就绪 · 生产验证待用户环境

## 定位

Docker 部署是 REL-1 的第三种交付形态,与便携版(内嵌 JRE 免安装)和标准版(依赖预装 JRE)并列。目标场景:

- **快速起 demo**:一条 `docker compose up -d` 起 MySQL + Redis + Backend 三件套
- **CI 冒烟**:配合 pg-testkit 做端到端联调(免装本地 MySQL/Redis)
- **生产候选**:配合 k8s / swarm 编排(需自行加 nginx / cert)

## 制品

| 文件 | 用途 |
|------|------|
| [backend/Dockerfile](../../backend/Dockerfile) | 后端镜像 · openjdk-8 jre alpine + jar copy · ~200MB |
| [docker-compose.yml](../../docker-compose.yml) | 三服务编排 · mysql:8.0.35 + redis:6.2.14-alpine + backend:0.3.7 |

## 启动

```bash
# 1. 预打包 jar(不进容器构建 · 依赖本地 mvn)
cd backend
mvn clean package -DskipTests

# 2. 起容器(项目根)
cd ..
docker compose up -d

# 3. 观察启动
docker compose logs -f backend

# 4. 访问
#    - Backend:      http://localhost:8080
#    - Swagger:      http://localhost:8080/swagger-ui.html
#    - MySQL:        localhost:3306 · root/qwe12345
#    - Redis:        localhost:6379
```

**v0.3.9 CHG-050 起 · frontend nginx 镜像已集成** · compose 一键起四服务(mysql/redis/backend/frontend)· 访问 http://localhost:5173 即可,`/api` `/swagger-ui` 已配置代理转 backend:8080。

若不想用容器 · 本地 dev 仍可 `cd frontend && npm run dev`(vite 5173 直接代理 8080)。

## Oracle 场景

Oracle 驱动 `ojdbc8:21.9.0.0` 已 pom 内置(scope=runtime),启用只需:

1. **配置目标业务库**:登录后台 `/interface/db` 新建连接 · 类型选 Oracle · 填 URL/用户/密码
2. **不需要**改 docker-compose.yml(Oracle 一般外置 · 不进 compose)· 如要内置可参考 [gvenzl/oracle-xe](https://hub.docker.com/r/gvenzl/oracle-xe) 加一个 service 段

配置库(存 PG 元数据)推荐仍用 MySQL(compose 内置)· 业务库(动态 dbId 切换)才用 Oracle。

## 与便携/标准版对比

| 维度 | 便携版 | 标准版 | Docker |
|---|---|---|---|
| 依赖 | 无(内含 JRE) | 预装 JRE 8+ | Docker Engine |
| 体积 | ~120MB(含 jlink JRE) | ~50MB(jar 单文件) | ~200MB(镜像) |
| 一键起 | 单机 bat / sh | 单机 bat / sh | docker compose up |
| 中间件 | 需外置 MySQL/Redis | 需外置 MySQL/Redis | compose 内置 |
| 场景 | 客户离线现场 | 有运维基础 | 快速 demo / CI |

## 已知限制

- 前端 nginx 镜像未提供:CI 端到端需手动加(下一版本 v0.3.8+ 视需求补)
- Windows 下 `host.docker.internal` 需 Docker Desktop 4.x+
- MySQL 数据卷 down -v 清空,生产需外置 volume driver

## 后续可选

- v0.3.8+:frontend nginx 镜像 + 端口反代
- v0.4.0+:k8s helm chart(需求确定后)
- Oracle 内置 compose 段(用户明确需要时)
