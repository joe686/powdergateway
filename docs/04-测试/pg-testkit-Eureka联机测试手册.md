# pg-testkit ↔ lcpt-register(Eureka)联机测试手册

> **版本**:v0.3.11 · **状态**:实测通过(2026-08-03) · **CHG**:CHG-054
>
> 本手册对 v0.3.11 CHG-054 "pg-testkit Eureka 多应用自注册" 提供完整可操作的验收步骤。已用**恒生 lcpt-register 7.1.0**(基于 Netflix Eureka Server · 端口 8091)实测通过。

## 环境依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK 17 | Oracle/Eclipse 17.x | **lcpt-register 必需** · pg-testkit 用 JDK 8 · 两个 JDK 共存即可(不同 JAVA_HOME) |
| lcpt-register-bootstrap.jar | 7.1.0 | 项目路径:`非项目主干内容/register/lcpt-register/lcpt-register-bootstrap.jar` |
| pg-testkit | v0.3.11 | `pg-testkit/` 已 mvn compile 通过 |

## Step 1 · 起 lcpt-register(Eureka Server · 8091)

```bash
cd "D:/Project/powergateway/非项目主干内容/register/lcpt-register"
export JAVA_HOME="/d/jdk-17.0.1"     # 换成你实际 JDK 17 路径
export PATH="$JAVA_HOME/bin:$PATH"

export CLASSPATH="lcpt-register-bootstrap.jar;../lib/otherlib/base/*;../lib/otherlib/spring-cloud/*"
export JVM_OPTS="-Xms256m -Xmx256m \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED"

java -cp "$CLASSPATH" $JVM_OPTS -Dname=lcpt-register-bootstrap \
  com.hundsun.lcpt.register.SpringBootRegisterApplication
```

**Windows CMD 或 startup-jdk17.bat 也可**(见 `sbin/启动步骤说明.txt`)。

**就绪检查**:

```bash
curl -sf http://localhost:8091/eureka/apps -H "Accept: application/json"
# 应返回 200 · 无 application 时 body 为 <applications>...</applications> 或 {"applications":{"versions__delta":"1"}}
```

## Step 2 · 起 pg-testkit(8081)· 自动 register 3 应用

修改 `pg-testkit/src/main/resources/application.yml`:

```yaml
pg-testkit.eureka:
  enabled: true
  server-url: http://127.0.0.1:8091/eureka
```

启动:

```bash
cd D:/Project/powergateway/pg-testkit
mvn spring-boot:run
```

**启动 log 应显示**:

```
[pg-testkit] Eureka 自注册启动 · server=http://127.0.0.1:8091/eureka · applications 数=3
[pg-testkit] Eureka register OK app=PG-INTERNAL inst=127.0.0.1:pg-internal:8080
[pg-testkit] Eureka register OK app=BANK-SVC     inst=127.0.0.1:bank-svc:9999
[pg-testkit] Eureka register OK app=HOST-SVC     inst=127.0.0.1:host-svc:9998
Started PgTestKitApplication in X.XXX seconds
```

## Step 3 · 三面确认

### 面 1:pg-testkit 侧

```bash
curl -s http://localhost:8081/test/eureka/list | jq
# 期望
# {
#   "enabled": true,
#   "serverUrl": "http://127.0.0.1:8091/eureka",
#   "configuredCount": 3,
#   "registeredCount": 3,
#   "registered": [{name:pg-internal,port:8080}, {name:bank-svc,port:9999}, {name:host-svc,port:9998}]
# }
```

### 面 2:Eureka Server 侧

```bash
curl -s http://localhost:8091/eureka/apps -H "Accept: application/json" | jq
# 期望 3 个 application(PG-INTERNAL/BANK-SVC/HOST-SVC)· 每个 instances 有 1 · status=UP
```

或浏览器访问 `http://localhost:8091` · lcpt-register 有内置 Eureka Server dashboard。

### 面 3:backend discover 联动(v0.3.12 CHG-055 · 已 E2E 验证通)

前提:backend 已起(v0.3.12+ · Registrar 自动装配 case eureka)· 建 Eureka 类型 registry_config:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 建 registry_config(注意 enabled 是 Integer 用 1 · 不是 true)
curl -s -X POST http://localhost:8080/api/registry/save \
  -H "Content-Type: application/json" -H "satoken: $TOKEN" \
  -d '{"name":"lcpt-register-8091","type":"eureka","serverAddr":"http://127.0.0.1:8091/eureka/","enabled":1}'

# discover(RegistryFacade 聚合所有已启用 client)
curl -s -H "satoken: $TOKEN" "http://localhost:8080/api/registry/discover-preview?serviceName=pg-internal"
# 期望:[{"serviceName":"pg-internal","ip":"127.0.0.1","port":8080,"scheme":"http","metadata":{...}}]

# backend 自己也注册到 lcpt-register
curl -s -X POST -H "satoken: $TOKEN" http://localhost:8080/api/registry/reregister-self
# 期望:{"code":200,"data":true}

# 验证 backend 已上 lcpt-register
curl -s http://localhost:8091/eureka/apps/POWERGATEWAY -H "Accept: application/json" | jq
# 期望:1 实例 UP · ipAddr 是 backend 网卡 IP(172.18.0.1 或 127.0.0.1 · 由 sys_config registry.self.ip.override 决定)
```

## Step 4 · 手工 API 冒烟

```bash
# 全量注销
curl -s -X DELETE http://localhost:8081/test/eureka/deregister-all
# {"action":"deregister-all","successCount":3}

# 单个注册
curl -s -X POST http://localhost:8081/test/eureka/register/pg-internal
# {"action":"register","name":"pg-internal","success":true}

# 幂等重复全量注册
curl -s -X POST http://localhost:8081/test/eureka/register-all
# {"action":"register-all","successCount":3,"total":3}
```

**Eureka Server eviction**:deregister 后 · lcpt-register 侧不是立即消失 · 由 `eviction-interval-timer-in-ms=3000` 触发(3s 后)。等 6s 再 curl `/eureka/apps` 才能看到实际清除。

## Step 5 · 优雅停机 · @PreDestroy 触发 deregister-all

**优雅停机方式**(会触发 `@PreDestroy` · 自动 deregister-all):

- **IDE Stop 按钮**(IntelliJ / Eclipse):红色 Stop · 会发 SIGTERM
- **Linux / Mac**:`kill -TERM <pid>` 或 `Ctrl-C`(mvn 前台)
- **Windows CMD 前台 mvn**:`Ctrl-C`(要点 Y 确认终止批处理)
- **Actuator shutdown**(需 pg-testkit 开启 · 默认关闭):`POST /actuator/shutdown`

**强杀方式**(不触发 `@PreDestroy` · 应用残留在 Eureka Server):

- Windows `taskkill /F /PID xxx`
- Linux/Mac `kill -9 <pid>`

强杀后需手工清理:

```bash
# 方式 A:先手工 deregister 再 kill(推荐)
curl -X DELETE http://localhost:8081/test/eureka/deregister-all
taskkill //F //PID <pid>

# 方式 B:直接调 Eureka Server DELETE
for APP in PG-INTERNAL BANK-SVC HOST-SVC; do
  INST_PORT=$(case $APP in PG-INTERNAL) echo 8080;; BANK-SVC) echo 9999;; HOST-SVC) echo 9998;; esac)
  curl -X DELETE "http://127.0.0.1:8091/eureka/apps/$APP/127.0.0.1:${APP,,}:$INST_PORT"
done
```

## 实测记录(2026-08-03)

| 用例 | 期望 | 实际 | 结论 |
|------|------|------|------|
| pg-testkit 启动 @PostConstruct 批量注册 | 3 应用 log OK | ✅ log 显示 3 register OK | 通过 |
| GET /test/eureka/list | registeredCount=3 | ✅ | 通过 |
| lcpt-register 8091 应用列表 | 3 UP | ✅ 3 应用 UP | 通过 |
| DELETE /test/eureka/deregister-all | successCount=3 | ✅ 6s 后 Eureka Server evict | 通过 |
| POST /test/eureka/register/{name} 单个 | success:true | ✅ | 通过 |
| POST /test/eureka/register-all 幂等 | successCount=3 | ✅ | 通过 |
| 强杀 pg-testkit(taskkill /F) | @PreDestroy 触发 | ❌ Windows /F 是 SIGKILL · Java 通用行为 | 需手工 deregister |
| 软关闭(Ctrl-C / IDE Stop) | @PreDestroy 触发 | ⚠️ 未实测(Windows Git Bash 不好触发) · Linux/Mac/IDE 应该 OK |  |

## 常见问题

**Q1:pg-testkit 启动 log 显示 "Eureka register 失败"**
- 检查 lcpt-register 是否起了(`curl http://localhost:8091/eureka/apps`)
- 检查 `pg-testkit.eureka.server-url` 是否与实际端口一致
- 检查是否有防火墙阻挡 8091

**Q2:Eureka Server 侧看不到应用**
- register 是异步 lease · 首次可能延迟 500ms · 等一下再 curl
- 检查 `lcpt-register/config/application.properties` 是否 `enableSelfPreservation=false`(此项开启后 evict 会推迟)

**Q3:JDK 17 找不到**
- lcpt-register 必须 JDK 17 · pg-testkit 可用 JDK 8
- 两个 JDK 可通过不同 shell 会话 export JAVA_HOME 隔离共存

**Q4:pg-testkit 端口 8081 被占**
- `netstat -ano | grep 8081` 找到 PID
- `taskkill /F /PID xxx`(Windows)或 `kill -9 <pid>`(Linux)

## 附:与 backend v0.3.9 CHG-051 联动

pg-testkit 注册的 3 应用可供 backend discover:
- backend `RegistryFacade.discover("pg-internal")` → 返 `[{ip:"127.0.0.1", port:8080}]`
- SOCK-5 INBOUND_SOCKET 接口 `outbound.applicationName=pg-internal` · 入站报文命中后 · Orchestrator 走 Eureka discover 找 backend 内部 exec 接口

即完整 lcpt-bank 场景:
```
渠道方 XML → pg-testkit Socket Mock(bank-svc:9999)
                 ↓ 报文透传
             backend INBOUND_SOCKET(自建端口)接收
                 ↓ dom4j 提 //FunctionId → 双层路由(FN-12 scope=3)→ PG 内部功能号
             backend discover("pg-internal") → 通过 lcpt-register(8091) 找到 backend 8080
                 ↓ HTTP POST /api/exec/{id}
             backend 执行 SQL → 返 JSON
                 ↓
             回 XML 按原分帧写回 Channel
```
