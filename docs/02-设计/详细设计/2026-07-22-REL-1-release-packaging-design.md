# REL-1 · 打包发布 · 详细开发计划

> **⚠️ CHG-027 更新（2026-07-22）**：本文档 §六 "CI/CD（GitHub Actions）"、§三 "分支策略"里的多 SKU tag CI 触发、§十 "路线图"中的"v0.1.1 CI 上线"均已**作废**。项目改为单人开发模式，不使用 GitHub Actions；发布走本地打包脚本 + 本地 `verify-artifacts.sh` 冒烟 + `git tag` 手动版本管理。其他章节（Maven profile、打包脚本、启动模板、SKU 矩阵）依然生效。首版 tag 定为 `v0.1.0`。

> 文档日期：2026-07-22
> 作者：Claude Opus 4.7
> 状态：**详细开发计划，待用户批准后开工**
> 上游方案：`docs/03-开发/打包发布方案.md`
> 用户回复决策点：
> 1. 组合 A（便携 Win + 便携 Linux + 标准包）；便携零环境自带 JRE 不依赖中间件；标准包**前后端分离**，系统数据库支持 **MySQL/Oracle/OceanBase**（客户可以在业务库上开一个 schema 直接用）
> 2. 不带 pg-testkit；另出 `-with-testkit` 版并标注"**不对外发行**"
> 3. **H2 主分支 + SQLite 独立分支**
> 4. h2-console：便携版开，标准版关
> 5. 不做 exe 安装包
> 6. 便携版自带 **JRE 17**（客户环境 JDK 8 也不影响）
> 7. 便携版 fat-jar；正式包**前后端分离**
> 8. Docker v2
> 补充：
> - 验证后**尽快**做自动发布流水线（GitHub Actions）
> - 改造 GitHub 分支：主分支 + tag 触发多 SKU CI（决策点确认）

---

## 一、SKU 全景

第一版（v0.1.0）交付 **5 个正式包 + 2 个内部包**：

| # | SKU 命名 | 类型 | 平台 | JDK | 系统 DB | 业务 DB 驱动 | 缓存 | pg-testkit | h2-console | 对外发行 |
|---|---------|------|------|-----|--------|-------------|------|-----------|-----------|--------|
| 1 | `powergateway-portable-win-x64-{ver}.zip` | 便携 | Win x64 | 内嵌 JRE17 | H2 | H2 + MySQL 驱动 | Caffeine | ❌ | ✅ | ✅ |
| 2 | `powergateway-portable-linux-x64-{ver}.zip` | 便携 | Linux x64 | 内嵌 JRE17 | H2 | H2 + MySQL 驱动 | Caffeine | ❌ | ✅ | ✅ |
| 3 | `powergateway-portable-win-x64-sqlite-{ver}.zip` | 便携 SQLite 分支 | Win x64 | 内嵌 JRE17 | SQLite | SQLite + MySQL 驱动 | Caffeine | ❌ | ❌（SQLite 无 console） | ✅ |
| 4 | `powergateway-portable-linux-x64-sqlite-{ver}.zip` | 便携 SQLite 分支 | Linux x64 | 内嵌 JRE17 | SQLite | SQLite + MySQL 驱动 | Caffeine | ❌ | ❌ | ✅ |
| 5 | `powergateway-standard-{ver}.zip` | 标准部署 · 前后端分离 | Win/Linux 通用 | 客户自带 JDK 8+ | MySQL / Oracle / OceanBase | MySQL / Oracle / OceanBase 驱动 | Redis / Caffeine（可选） | ❌ | ❌ | ✅ |
| 6 | `powergateway-portable-*-with-testkit-{ver}.zip` | 便携测试版 | Win/Linux | 内嵌 JRE17 | H2 | H2 + MySQL 驱动 | Caffeine | ✅（8081+9999） | ✅ | ❌ **仅内部** |
| 7 | `powergateway-standard-with-testkit-{ver}.zip` | 标准测试版 | Win/Linux | 客户自带 JDK | MySQL/Oracle/OceanBase | 同标准包 | 同标准包 | ✅ | ❌ | ❌ **仅内部** |

**JDK 说明**（决策点 6）：
- 便携版**始终内嵌 JRE 17**（用 `jlink` 裁剪 ~40MB），客户环境有没有 Java 都能跑，环境 Java 版本冲突无关
- 标准版**依赖客户自备 JDK**，Spring Boot 2.7.18 官方最低支持 JDK 8；实际代码里已用了 Optional / Stream / lambda 等 8+ 特性，不使用 JDK 9+ 语法（保持兼容），因此标准包客户可以 JDK 8/11/17 任选

---

## 二、分支策略（决策点确认）

**主分支 + tag 触发多 SKU CI**（不为每个 SKU 单独维护长期分支）：

```
main                    ← 唯一开发主干
 ├─ tag v0.1.0          ← 触发 CI，产出 SKU 1/2/5（默认套餐）
 ├─ tag v0.1.0-sqlite   ← 触发 CI，产出 SKU 3/4（SQLite 分支）
 ├─ tag v0.1.0-testkit  ← 触发 CI，产出 SKU 6/7（内部测试版）

release/*               ← 仅在需要维护旧版本 patch 时才创建（如 release/0.1.x）
                        ← 平时不用
```

**SQLite 的"分支"含义**：不是 git 分支，而是 **Maven profile**：
- `pom.xml` 内定义 `<profile id="db-h2">` / `<profile id="db-sqlite">`
- CI 通过 `-Pdb-h2` 或 `-Pdb-sqlite` 触发不同构建
- 代码里通过 `@Profile("h2") / @Profile("sqlite")` 装载不同的 Dialect Bean 和 migration 脚本

**注册中心裁剪**（对齐 REG-1 方案）：Maven profile `registry-none` / `registry-nacos` / `registry-full`，与 db profile 正交。

---

## 三、便携版技术方案

### 3.1 目录结构（Win 示例）

```
powergateway-portable-win-x64-0.1.0/
├── backend/
│   ├── powergateway.jar               # fat-jar，含前端 dist 静态资源
│   ├── lib/                           # 独立驱动包目录（客户可放 Oracle/OceanBase 驱动进来）
│   │   ├── mysql-connector-j-8.0.33.jar   # 便携版预置 MySQL 驱动（用户不改配置也能连 MySQL 业务库）
│   │   └── README-drivers.txt         # 说明如何添加 Oracle/OceanBase 驱动
│   └── config/
│       └── application-standalone.yml
├── data/
│   ├── h2/                            # H2 数据文件（首次启动创建 pg_config + pg_audit）
│   └── logs/
├── jre/                               # jlink 裁剪的 JRE 17（~40MB）
│   ├── bin/java.exe
│   └── ...
├── start.bat                          # 一键启动
├── stop.bat
├── open-console.bat                   # 打开 h2-console 浏览器（默认 http://localhost:8080/h2-console）
├── README.txt                         # 端口、账号密码、常见问题
└── LICENSE.txt
```

Linux 版本同结构，`.bat` 换 `.sh`，`java.exe` 换 `java`。

SQLite 分支：`data/h2/` 换成 `data/sqlite/pg.db`，`config/application-standalone.yml` 走 SQLite dialect。

### 3.2 内嵌 JRE (`jlink`)

平台专属构建，脚本 `scripts/jlink-jre.sh`：
```bash
$JAVA_HOME/bin/jlink \
  --module-path $JAVA_HOME/jmods \
  --add-modules java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.management.rmi,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.compiler,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.crypto.mscapi,jdk.httpserver,jdk.jdwp.agent,jdk.jfr,jdk.jshell,jdk.localedata,jdk.management,jdk.management.agent,jdk.naming.dns,jdk.naming.rmi,jdk.net,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.zipfs \
  --output dist/jre \
  --strip-debug \
  --compress=2 \
  --no-header-files \
  --no-man-pages
```

Win 和 Linux 分别用对应 JDK 17 jlink（**必须在目标平台的 JDK 上执行**，跨平台 jlink Oracle 不支持）。

### 3.3 无 Redis 降级开关

新增 `CacheConfig.java` 里的分层：
```java
@Configuration
public class CacheConfig {
  @Bean
  @ConditionalOnProperty(name = "pg.cache.redis.enabled", havingValue = "true", matchIfMissing = true)
  public RedisTemplate<String, Object> redisTemplate(...) { ... }

  @Bean
  @ConditionalOnProperty(name = "pg.cache.redis.enabled", havingValue = "false")
  public NoopRedisTemplate noopRedisTemplate() { ... }
}
```
- `application-standalone.yml` 设 `pg.cache.redis.enabled: false`
- `CachingService` 逻辑不变，遇 `NoopRedisTemplate` 时 Redis 层直接返回 null，退化为 Caffeine-only（现有双层缓存架构里 Redis miss 就查 DB，行为一致）

### 3.4 H2 兼容层

`application-standalone.yml`（H2 版）：
```yaml
spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:h2:file:./data/h2/pg_config;MODE=MySQL;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE;INIT=RUNSCRIPT FROM 'classpath:db/init-h2.sql'
          driver-class-name: org.h2.Driver
          username: sa
          password:
        audit:
          url: jdbc:h2:file:./data/h2/pg_audit;MODE=MySQL;DB_CLOSE_DELAY=-1
  h2:
    console:
      enabled: true                     # 便携版开
      path: /h2-console
      settings:
        web-allow-others: false         # 只允许本机
pg:
  cache:
    redis:
      enabled: false
```

**已知兼容点**（继承现有测试基础设施，backend 现已支持 H2 测试模式）：
- `DATE_FORMAT`：已有 `H2DateFormatAlias.java`
- `IFNULL`：H2 用 `NVL`，需加 alias 或改用 `COALESCE`（跨库通用）
- 分库分表：H2 版**不支持**（demo 场景），业务库分库分表由用户自己 MySQL 承载

### 3.5 SQLite 兼容层（独立分支产物）

`application-standalone-sqlite.yml`：
```yaml
spring:
  datasource:
    dynamic:
      primary: master
      datasource:
        master:
          url: jdbc:sqlite:./data/sqlite/pg_config.db
          driver-class-name: org.sqlite.JDBC
        audit:
          url: jdbc:sqlite:./data/sqlite/pg_audit.db
          driver-class-name: org.sqlite.JDBC
```

- 依赖：`org.xerial:sqlite-jdbc:3.44.0.0`
- DDL 单独一份：`db/init-sqlite.sql`（SQLite 无 `AUTO_INCREMENT`，用 `INTEGER PRIMARY KEY AUTOINCREMENT`；无 `DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE`，改用触发器）
- MyBatis-Plus dialect：需要 SQLite dialect（内置支持）
- **代价**：h2-console 不能用；分库分表不支持；性能略低于 H2

### 3.6 前端合体

`backend/pom.xml` 加 `frontend-maven-plugin`：
```xml
<plugin>
  <groupId>com.github.eirslett</groupId>
  <artifactId>frontend-maven-plugin</artifactId>
  <version>1.15.0</version>
  <executions>
    <execution>
      <id>install-node</id>
      <goals><goal>install-node-and-npm</goal></goals>
      <configuration><nodeVersion>v18.19.0</nodeVersion></configuration>
    </execution>
    <execution>
      <id>npm-install</id>
      <goals><goal>npm</goal></goals>
      <phase>generate-resources</phase>
      <configuration>
        <workingDirectory>${project.basedir}/../frontend</workingDirectory>
        <arguments>ci</arguments>
      </configuration>
    </execution>
    <execution>
      <id>npm-build</id>
      <goals><goal>npm</goal></goals>
      <phase>generate-resources</phase>
      <configuration>
        <workingDirectory>${project.basedir}/../frontend</workingDirectory>
        <arguments>run build</arguments>
      </configuration>
    </execution>
  </executions>
</plugin>
```

`maven-resources-plugin` 把 `frontend/dist/**` 复制到 `backend/target/classes/static/`；`WebMvcConfig` 添加 SPA fallback：
```java
@Override
public void addViewControllers(ViewControllerRegistry registry) {
  registry.addViewController("/{spring:[a-zA-Z0-9-_]+}").setViewName("forward:/index.html");
  // 二级、三级路由同理
}
```

### 3.7 启动脚本（Win 示例）

`start.bat`：
```bat
@echo off
chcp 65001 > nul
title PowerGateway

set BASE=%~dp0
set JAVA=%BASE%jre\bin\java.exe

if not exist "%JAVA%" (
  echo [WARN] 内嵌 JRE 未找到，尝试使用系统 Java
  set JAVA=java
)

echo ================================================
echo  PowerGateway 便携版启动中...
echo  数据目录: %BASE%data
echo  访问地址: http://localhost:8080
echo  默认账号: admin / Admin@123
echo  H2 控制台: http://localhost:8080/h2-console
echo ================================================
echo.
echo  关闭本窗口即停止服务。
echo.

"%JAVA%" -Xms256m -Xmx1g -Dfile.encoding=UTF-8 ^
  -Dspring.profiles.active=standalone ^
  -Dloader.path="%BASE%backend\lib" ^
  -jar "%BASE%backend\powergateway.jar"

pause
```

**注意**：
- `chcp 65001` 处理中文乱码（[[feedback-windows-bat-pitfalls]]）
- `-Dloader.path="%BASE%backend\lib"` 让 fat-jar 能加载 `lib/` 里额外的驱动 jar（Oracle/OceanBase 由客户手工放入）
- `%BASE%` 尾带 `\`，路径拼接不能重复加

Linux 版 `start.sh` 同理，注意 `#!/usr/bin/env bash` + `set -e` + `chmod +x` 三件套。

---

## 四、标准版技术方案（前后端分离）

### 4.1 目录结构

```
powergateway-standard-0.1.0/
├── backend/
│   ├── powergateway-backend.jar       # 后端 jar，不含前端资源
│   ├── config/
│   │   ├── application.yml
│   │   ├── application-prod.yml.example
│   │   └── logback-spring.xml
│   ├── lib/
│   │   ├── mysql-connector-j-8.0.33.jar
│   │   ├── ojdbc11-21.9.0.0.jar       # Oracle 驱动
│   │   ├── oceanbase-client-2.4.9.jar # OceanBase 驱动
│   │   └── README-drivers.txt         # 说明信创国产库的扩展方式
│   ├── scripts/
│   │   ├── install-schema-mysql.sh    # 建库建表
│   │   ├── install-schema-oracle.sh
│   │   ├── install-schema-oceanbase.sh
│   │   ├── start.sh / stop.sh
│   │   └── start.bat / stop.bat
│   └── init-sql/
│       ├── init-mysql.sql
│       ├── init-oracle.sql
│       └── init-oceanbase.sql
├── frontend/
│   ├── dist/                          # 静态资源，客户部署到 Nginx
│   ├── nginx.conf.example
│   └── README.md
├── docs/
│   ├── 部署手册.md
│   ├── 数据库准备.md
│   └── 常见问题.md
└── README.md
```

### 4.2 前后端分离的 nginx 配置示例

`frontend/nginx.conf.example`：
```nginx
server {
  listen 80;
  server_name powergateway.example.com;

  root /opt/powergateway/frontend/dist;
  index index.html;

  # SPA fallback
  location / {
    try_files $uri $uri/ /index.html;
  }

  # API 反代到后端
  location /api {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }
}
```

### 4.3 系统 DB 多语法支持

**方案**：DDL 三份（`init-mysql.sql` / `init-oracle.sql` / `init-oceanbase.sql`），部署前客户根据数据库类型选一份执行；MyBatis 层通过 `<databaseIdProvider>` 分发方言（现有代码若有 SQL 不兼容需要重构）。

**风险评估**：
- 现有 backend 代码里的 SQL 都是 MyBatis-Plus 生成，语法基本通用，但要普查以下场景：
  - `LIMIT ? OFFSET ?` → Oracle 用 `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`（Oracle 12+ 支持）
  - `AUTO_INCREMENT` → Oracle 用 `SEQUENCE + TRIGGER`
  - `DATETIME` → Oracle 用 `TIMESTAMP`
  - `JSON` 字段 → Oracle 用 `CLOB`（M2-3 等的 `configJson` 存 JSON 字符串没问题）
  - `TINYINT(1)` → Oracle 用 `NUMBER(1)`
  - `ON DUPLICATE KEY UPDATE` → Oracle 用 `MERGE INTO`（sys_config batchUpdate 需重构）
- **工时评估**：DDL 转写 1 天；MyBatis SQL 兼容性排查 + fix 2 天；每种数据库跑一遍完整回归 1 天 = 4 天

**建议**：Oracle/OceanBase 支持作为**独立子任务** REL-1B，先跟 REL-1（H2/MySQL）解耦，REL-1 出 v0.1.0 后再启动 REL-1B。

### 4.4 Redis 可选

`application-prod.yml.example` 里默认 `pg.cache.redis.enabled: true`；无 Redis 环境的客户可以关掉。

---

## 五、构建脚本

### 5.1 目录

```
scripts/
├── build-portable.sh              # 便携版组装
├── build-portable-sqlite.sh       # SQLite 分支便携版组装
├── build-standard.sh              # 标准版组装
├── jlink-jre.sh                   # JRE 裁剪（Win/Linux 都用同一份，通过 --platform 传参）
├── package-templates/             # 模板
│   ├── start.bat / start.sh / stop.bat / stop.sh / open-console.bat
│   ├── README-portable.txt
│   ├── README-standard.md
│   └── application-standalone.yml
└── ci/
    ├── github-actions-release.yml # GitHub Actions workflow
    └── verify-artifacts.sh        # 产物验证：解压 → 启动 → 冒烟测试
```

### 5.2 手动打包命令（v1 阶段）

```bash
# 便携 Win H2 版
scripts/build-portable.sh --platform win-x64 --db h2 --version 0.1.0

# 便携 Linux SQLite 版
scripts/build-portable.sh --platform linux-x64 --db sqlite --version 0.1.0

# 标准版
scripts/build-standard.sh --version 0.1.0

# 内部测试版（带 pg-testkit）
scripts/build-portable.sh --platform win-x64 --db h2 --with-testkit --version 0.1.0
```

产物输出：`dist/{sku-name}.zip` + `dist/{sku-name}.zip.sha256`

---

## 六、CI/CD（GitHub Actions）

### 6.1 触发时机

```yaml
on:
  push:
    tags:
      - 'v*.*.*'               # v0.1.0 → 触发默认 SKU（1/2/5）
      - 'v*.*.*-sqlite'        # → SKU 3/4
      - 'v*.*.*-testkit'       # → SKU 6/7（**内部包，不上传到 Release，只归档到 artifact**）
```

### 6.2 Job 矩阵

`.github/workflows/release.yml` 主要 Job：
- `build-portable-win-x64`（runs-on: `windows-latest`，独立 jlink Win JRE + 打 zip）
- `build-portable-linux-x64`（runs-on: `ubuntu-latest`，独立 jlink Linux JRE）
- `build-standard`（runs-on: `ubuntu-latest`，出通用 zip）
- `publish-release`（依赖前 3 个 job，创建 GitHub Release，上传 zip + sha256 + CHANGELOG）

### 6.3 冒烟测试

每个 Job 打完包后，`scripts/ci/verify-artifacts.sh`：
1. 解压 zip
2. 启动服务（后台）
3. `curl http://localhost:8080/actuator/health` → 200 OK
4. `curl -X POST /api/auth/login` 用默认 admin 登录 → 拿到 token
5. `curl /api/interface/list` → 200
6. 关闭服务，删除数据目录

任一步失败则 Job 失败，Release 不发布。

### 6.4 版本号来源

- `git tag v0.1.0` → CI 从 tag 提取 `0.1.0` 注入 `pom.xml` 的 `${revision}` + 前端 `package.json.version`
- Maven 用 `revision` property 支持动态版本：`<version>${revision}</version>`

---

## 七、发布节奏建议（v1 → v1.1 → v2）

| 里程碑 | 内容 | 预估 |
|-------|------|------|
| **v0.1.0 首发** | SKU 1/2/5（Win/Linux 便携 H2 + 标准 MySQL）+ 手动脚本 | 1.5 周 |
| **v0.1.1 CI 上线** | GitHub Actions 自动发布 + 冒烟测试 | 3 天 |
| **v0.2.0 SQLite 分支** | SKU 3/4 + 分支的 Maven profile 打通 | 1 周 |
| **v0.3.0 Oracle/OceanBase** | 标准包支持 Oracle/OceanBase（REL-1B 子任务） | 1 周 |
| **v0.4.0 内部测试版** | SKU 6/7 内部专用 pg-testkit 版（依赖 TEST-1 交付） | 3 天 |
| **v1.0.0 Docker** | 补 Docker Compose 方案（v2 承诺兑现） | 1 周 |

---

## 八、工时估算

| 阶段 | 工时 |
|------|------|
| Maven profile 三档（db-h2 / db-sqlite） + Cache 降级开关 | 1.5 天 |
| 前端合体 fat-jar 打包（frontend-maven-plugin + SPA fallback） | 1 天 |
| `jlink-jre.sh` 跨平台 JRE 裁剪 + 联调 | 1 天 |
| 启动/停止脚本 Win + Linux + open-console + 中文乱码兼容 | 1 天 |
| `build-portable.sh` 组装脚本 | 0.5 天 |
| `build-standard.sh` 组装脚本 | 0.5 天 |
| 标准包 nginx.conf.example + 部署手册文档 | 1 天 |
| H2 数据库 MODE=MySQL 兼容性 fix（IFNULL/DATE_FORMAT 等） | 1 天 |
| SQLite 分支 DDL + dialect + 兼容性 | 2 天 |
| 冒烟测试脚本 `verify-artifacts.sh` | 0.5 天 |
| GitHub Actions workflow 编写 + 联调（含 Win runner） | 1.5 天 |
| 5 个正式 SKU + 2 个内部 SKU 端到端联调 | 1.5 天 |
| Oracle/OceanBase 兼容（**REL-1B 子任务，独立算**） | +4 天 |
| **合计（REL-1）** | **~13 天** |
| **合计（+ REL-1B）** | **~17 天** |

---

## 九、待用户明确的次要问题

| # | 问题 | 我的默认建议 |
|---|------|-------------|
| 1 | 是否要为便携版做 ARM 版本（Apple Silicon Mac / ARM Linux）？ | 不做，第一版只 x64 |
| 2 | 便携版首次启动如果端口 8080 被占用，是自动改端口还是报错退出？ | 报错退出，README 提示用户改 `application-standalone.yml` 里的 `server.port` |
| 3 | 便携版是否自带"卸载脚本"清理数据？ | 不做。整目录删除即卸载，`data/` 用户自决 |
| 4 | 标准版是否要提供 `systemd unit` 模板？ | 提供 `powergateway.service.example`，客户可选装 |
| 5 | 内部测试版怎么标"不对外发行"？ | (1) 文件名前缀 `INTERNAL-`；(2) 启动 banner 打红字警告；(3) 只归档到 Actions Artifact，不进 GitHub Release |
| 6 | 是否要在 Release 里贴 CHANGELOG.md？ | 是，从 `docs/03-开发/变更记录.md` 提取本版本涉及的 CHG 编号自动生成 |
| 7 | 客户拿到便携版后想升级到新版本，怎么迁移数据？ | v1 手动：停旧版 → 复制 `data/` 到新版目录 → 启新版；README 说明；v2 加内置升级检查 |
| 8 | 是否要签名（Windows Authenticode）？ | 暂不签名（需商业证书 ~$300/年），README 里提示 SmartScreen 警告如何绕过 |
| 9 | 便携版 fat-jar 首次启动预估耗时？ | JRE17 + Spring Boot 2.7 + H2 冷启动约 15-25s（内嵌 JRE 无 JIT 预热），可接受 |

---

## 十、依赖与风险

| 项 | 说明 |
|----|------|
| 依赖 | REG-1 注册中心的 Maven profile 需先合入（无也可发布，只是缺 nacos 变体） |
| 依赖 | TEST-1 pg-testkit 增强完成后才能出 SKU 6/7 |
| 风险 | Oracle 驱动 ojdbc11 有 license 限制（Oracle Technology Network License），二次分发合规性需法务确认，可能改为"客户自行下载放入 lib/"方案 |
| 风险 | `frontend-maven-plugin` 首次 CI 跑要下载 Node 18，需要国内镜像加速（配 `.npmrc registry=https://registry.npmmirror.com/`），否则 timeout |
| 风险 | 内部测试版流出风险，除文件名标注外，可考虑代码里加"启动限制"（如 30 天后无法启动），但会影响长期回归测试，取舍看用户 |

---

## 十一、批准清单

用户批准以下几点后即可开工：

- [ ] 7 个 SKU 命名和范围确认
- [ ] 分支策略：主分支 + tag 触发（含 SQLite/testkit 变体 tag 命名）
- [ ] Oracle/OceanBase 拆成 REL-1B 独立子任务的分期方案
- [ ] `frontend-maven-plugin` 合体 fat-jar 方案（工程结构改动，需先在 backend/pom.xml 加插件配置）
- [ ] 标准版前后端分离 + nginx 反代方案
- [ ] 内部测试版"仅内部"标识方案（文件名前缀 + banner + Artifact-only）
- [ ] "待用户明确的次要问题"9 条决策
