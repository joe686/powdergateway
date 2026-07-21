# PowerGateway 标准版 · 部署手册

前后端分离部署：Nginx 承前端静态资源，Spring Boot 后端跑 8080 端口。

## 前置

- JDK 8+（推荐 11 或 17）
- MySQL 8.0+ / Oracle 11g+ / OceanBase（选一）
- Redis 6.2+（可选，无则自动降级 Caffeine-only）
- Nginx（推荐 1.20+）

## 目录结构

```
powergateway-standard-{version}/
├── backend/
│   ├── powergateway-backend.jar     # 后端 fat-jar
│   ├── config/
│   │   ├── application-prod.yml.example  # 复制为 application-prod.yml 后编辑
│   │   └── application-prod.yml          # ← 你需要创建这个
│   ├── lib/                          # Oracle/OceanBase 驱动放这里
│   ├── init-sql/                     # 建库建表 DDL（MySQL 版）
│   └── scripts/
│       ├── install-schema-mysql.sh   # 建库脚本
│       ├── start.sh / stop.sh
│       └── start.bat
├── frontend/
│   ├── dist/                         # 前端静态资源，部署到 Nginx
│   └── nginx.conf.example
└── docs/
```

## 一步一步部署

### 1. 建库

```bash
cd backend/scripts
./install-schema-mysql.sh localhost 3306 root <mysql_root_password>
```

或手工在 MySQL 里执行：
```sql
CREATE DATABASE powergateway_config CHARACTER SET utf8mb4;
CREATE DATABASE powergateway_audit  CHARACTER SET utf8mb4;
```
然后：`mysql -u root -p powergateway_config < backend/init-sql/init-mysql.sql`

### 2. 修改配置

```bash
cd backend/config
cp application-prod.yml.example application-prod.yml
vim application-prod.yml   # 改数据库 URL / 账号 / 密码 / Redis / AES key
```

### 3. 启动后端

```bash
cd backend/scripts
./start.sh
tail -f ../logs/powergateway.log
```

浏览器打开 http://localhost:8080/actuator/health 应返回 200。

### 4. 部署前端

```bash
sudo cp -r frontend/dist /opt/powergateway/frontend/
sudo cp frontend/nginx.conf.example /etc/nginx/sites-available/powergateway
sudo ln -s /etc/nginx/sites-available/powergateway /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

浏览器打开 http://<你的域名>/ 即可，默认账号 admin / Admin@123。

### 5. 添加 Oracle / OceanBase 驱动（可选）

```bash
cp ojdbc11-*.jar backend/lib/
# 修改 config/application-prod.yml 里的 url / driver-class-name
./scripts/stop.sh && ./scripts/start.sh
```

## Redis 降级

无 Redis 环境时，把 `application-prod.yml` 里的 `pg.cache.redis.enabled: false`，
后端启动时会跳过 Redis Bean 装配，缓存降级为 Caffeine 单层，业务链路不受影响。

## 端口占用

默认后端 8080，如与其他服务冲突，改 `application-prod.yml` 里的 `server.port` 即可。
