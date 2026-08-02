# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 父目录的 `../CLAUDE.md` 包含整体产品定位、技术栈和27单元交付计划，本文件聚焦后端实现细节。

## 常用命令

```bash
mvn spring-boot:run                                        # 启动（端口 8080）
mvn clean package -DskipTests                              # 打包
mvn test                                                   # 全量测试（H2，无需外部服务）
mvn test -Dtest=PowergatewayApplicationTests               # 单个测试类
mvn test -Dtest=PowergatewayApplicationTests#healthEndpoint # 单个测试方法
```

Swagger UI：`http://localhost:8080/swagger-ui.html`

## 包结构

```
com.powergateway/
├── PowergatewayApplication.java
├── common/
│   └── Result.java              # 统一响应包装，所有接口必须返回此类型
├── controller/                  # REST 层，@Tag/@Operation Swagger 注解
├── service/                     # 业务逻辑层
├── dao/                         # MyBatis-Plus Mapper 接口
├── model/                       # 实体类 & DTO
├── config/                      # Sa-Token、Redis、多数据源配置
├── exception/
│   ├── BusinessException.java   # 业务异常，可携带自定义 code
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice 统一处理
└── utils/                       # 格式转换、分库分表、缓存工具
```

## 核心约定

### 统一响应

所有 Controller 方法返回 `Result<T>`，使用静态工厂方法：

```java
Result.success(data)      // 200 + 数据
Result.success()          // 200 无数据
Result.fail("message")    // 500
Result.fail(400, "msg")   // 自定义 code
```

业务异常通过 `throw new BusinessException(code, message)` 抛出，由 `GlobalExceptionHandler` 统一捕获转为 `Result`。

### MyBatis-Plus 配置

- 下划线 ↔ 驼峰自动转换（无需手动 `@Column`）
- 软删除字段：`deleted`（0=正常，1=已删）— 所有用户表必须有此字段，MyBatis-Plus 自动过滤
- 生产环境 SQL 打印到 stdout；测试环境关闭日志

### 测试配置

测试类必须加 `@ActiveProfiles("test")`，自动切换到 `application-test.yml`：

- H2 内存库（MySQL 兼容模式），无需启动外部 MySQL
- Redis 自动配置已禁用，无需启动 Redis
- 测试数据库每次运行重新初始化

### TDD 测试模板

**工具类（无 Spring 上下文，最快）**：
```java
@ActiveProfiles("test")
class M11FormatConverterTest {
    private final FormatConverter converter = new FormatConverter();

    @Test void json转xml_正常路径() { ... }
    @Test void json转xml_空输入_抛异常() { ... }
    @Test void json转xml_嵌套结构() { ... }
}
```

**Service 层（H2 + 事务回滚）**：
```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // 每个测试后自动回滚，不污染数据
class M15TemplateServiceTest {
    @Autowired TemplateService templateService;

    @Test void 保存模板_正常() { ... }
    @Test void 保存模板_名称重复_抛BusinessException() { ... }
}
```

**Controller 层（MockMvc，隔离 Service）**：
```java
@WebMvcTest(TemplateController.class)
@ActiveProfiles("test")
class M15TemplateControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean TemplateService templateService;

    @Test void 查询列表_返回Result包装() throws Exception {
        mockMvc.perform(get("/api/template/list"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200));
    }
}
```

### 数据库 Schema

`src/main/resources/db/init.sql` 包含 8 张配置库核心表。关键设计：

| 字段约定 | 说明 |
|---------|------|
| `config_json` / `mapping_rule` 等 JSON 列 | 用 `TEXT` 存储，Java 侧 Jackson 序列化/反序列化 |
| `password`（`db_connection` 表） | AES 加密存储，不能明文 |
| `status`（`interface_config` 表） | 枚举：`draft` / `published` / `disabled` |
| `db_type`（`db_connection` 表） | 枚举：`MySQL` / `Oracle` / `PostgreSQL` |

`sys_config` 表存全局 KV 配置，已预置：
- `cache.query.ttl` = 300s
- `cache.template.ttl` = 600s
- `audit.log.retention.days` = 365

## 关键依赖版本

| 依赖 | 版本 |
|------|------|
| Spring Boot | 2.7.18 |
| MyBatis-Plus | 3.5.7 |
| Sa-Token | 1.37.0 |
| springdoc-openapi-ui | 1.7.0 |
| dom4j | 2.1.4 |
| opencsv | 5.9 |
| H2（test） | 2.x |

## P0-1 已完成的基础设施

- 全局异常处理（`BusinessException` + `MethodArgumentNotValidException` + 兜底）
- 统一响应包装 `Result<T>`
- 健康检查接口 `GET /api/health`
- H2 测试基础设施
- 数据库 Schema（`db/init.sql`）

## 已完成单元

| 单元 | 内容 |
|------|------|
| P0-1 | 全局异常处理、统一响应体 `Result<T>`、健康检查、H2 测试基础设施 |
| P0-3 | 配置库 8 张表 DDL + MyBatis-Plus 实体类 + Mapper |
| P0-4 | Sa-Token 认证、登录接口 `POST /api/auth/login`、`SaTokenConfig` |
| M1-1 | `FormatConverter`：JSON ↔ XML ↔ CSV 等 12 种互转 |
| M1-2 | 字段映射配置：`mapping_rule` JSON 存入 `convert_template`，`ConvertController` 保存/预览映射 |
| M1-3 | `FieldProcessor`：策略模式字段加工引擎（Trim/Pad/Substring/Case/TypeCast） |
| M1-4 | 渠道配置：`ChannelConfigController/Service`，运行时按渠道字段自动选模板 |
| M1-5 | 转换模板 CRUD：`TemplateController/Service`，含分页搜索、版本留存（`is_latest`） |
| M1-6 | 报文转换串联接口 `POST /api/convert`：格式转换 → 字段映射 → 字段加工全链路，Redis 缓存模板 |
| M1-7 | 端口分发路由：`port_route` 表 + `POST /api/dispatch`，双向转换（请求加工→转发→应答加工→返回），`HeaderConfigMerger`（两级报文头合并）、`CharsetConverter`（字节级转码） |
| M2-1 | 数据库连接管理：MySQL/Oracle/PG，密码 AES-128 加密，测试连通 |
| M2-2 | 表结构查询：`DatabaseMetaData` + Redis 缓存（TTL 24h）+ 手动刷新 + Excel 导出 |
| M2-9 | SQL 审计日志：`SqlAuditLog` 实体 + `@DS("audit")` 审计数据源 + `@AuditLog` 注解 + `SqlAuditAspect` AOP + `AuditLogService`（LinkedBlockingQueue 守护线程异步写入）+ `AuditLogCleanupJob`（每天凌晨2点按 sys_config 留存天数清理） |
| M2-3 | 查询接口配置：`QueryBuilder`（单表/多表 LEFT JOIN）、4步向导（选表→选字段→条件→预览） |
| M2-4 | 插入接口配置：`InsertBuilder`、`DataSourceResolver`（REQUEST/CONST/CALC）、`ColumnValidator`（基于表结构元数据校验）、多表 JDBC 手动事务（任意失败全部回滚），前端 `InsertConfig.vue` |
| M2-5 | 修改接口配置：`UpdateBuilder`，强制唯一条件校验（主键/唯一索引），修改前快照，复用 `DataSourceResolver`、`ColumnValidator` |
| M2-6 | 删除接口配置：`DeleteBuilder`，待删数据预览，批量删除保护开关，`allow_batch_delete` 字段 |
| M2-7 | 接口发布：状态流转 draft→published→disabled，统一执行入口 `/api/exec/{id}`，`OpenApiDynamicCustomizer` Swagger 动态注册 |
| M2-10 | 双层缓存：`CacheConfig`（Caffeine Bean + cacheRedisTemplate）、`QueryCacheManager`（Caffeine→Redis→DB，分布式锁防击穿，命中统计）、`CacheController`（list/config/evict/refresh/stats/evictAll），`interface_config` 新增 `cache_enabled`/`cache_ttl_seconds`/`cache_key_template`，前端 `CacheList.vue` |
| M2-8 | 分库分表配置：`ShardRuleJson`（MODULO/RANGE 路由规则 DTO）、`ShardRouter`（纯静态路由工具，支持取模/范围/补查/补零）、`ShardConfigService/Controller`（CRUD + 路由预览）；`InterfaceConfigService.resolveSharding()` 在4种 exec 方法中替换主表名和 dbConnectionId；前端 `ShardConfig.vue` + `api/shardConfig.js` |
| SYS-3 | 用户权限管理：`MenuPermission`（三角色菜单白名单常量 + `sys_config` 日志开关）、`GET /api/auth/menu`（登录后拉取可见路由列表）、`UserService/Controller`（用户 CRUD，BCrypt 密码，自删/末位 admin 保护）；前端 `useUserStore.allowedMenus`、`SideMenu.vue` 动态 `v-if`、路由守卫越权拦截、`UserList.vue` |
| SYS-1 | 操作日志管理：`@SysLogRecord` 注解+`SysLogAspect` AOP 异步写 `sys_log`，`SysLogService`（队列+分页查询+Excel导出），`SysLogArchiveJob`（归档到 `sys_log_history`），`SysLogController`（list/history/export/audit），前端 `LogList.vue`（双Tab：操作日志+SQL审计，含「查历史数据」开关） |
| SYS-2 | 性能统计：`@PerfStat` 注解 + `PerfStatAspect` AOP 异步写 `perf_stat`，`PerfStatService`（队列消费/summary/statBetween/groupByOpType/topSlowInterfaces），`PerfAlertJob`（定时告警检查），`StatsController`（summary/alerts/alert-config），前端 `Stats.vue`（折线图+柱状图+告警列表+阈值配置） |
| SYS-4 | 系统配置：`SysConfigService`（`@PostConstruct` 预热 ConcurrentHashMap + `batchUpdate` 持久化 + `ApplicationEvent` 热更新广播），`SysConfigController`（GET all / PUT batch），前端 `SystemConfig.vue`（分组表单，仅 admin 可保存） |
| SYS-5 | 接口配置九步向导：纯前端 `InterfaceWizard.vue`（867 行），Pinia + localStorage 保存中间状态，按接口类型动态裁剪步骤，复用 `ConditionBuilder.vue` |
| AUX-1 | 报文调试工具：`MessageDebug.vue`，格式转换调试 + 接口调用调试双模式，`/tools/debug` 路由 |
| AUX-2 | 首页系统概览：`HomeOverviewController/Service`，`GET /api/home/overview?dimension=today\|week\|month`，聚合 interfaceStats/callStats/callTrend/opTypeDistribution/topSlowInterfaces/activeAlerts；前端 `DashboardView.vue`（5卡片+3图表+TOP5表+告警列表+维度切换+30s 轮询） |
| FN-09 | 接口文档下载：`InterfaceDocumentService.buildVisualModel/buildTransformModel`,md/html/xlsx 三格式,zip 批量导出 · v0.2.0 CHG-032 加字典 key 列 + xlsx 4-sheet |
| FN-11 | 配置导入导出扩展：`ConfigImportService/ConfigExportService`,Excel/Markdown/Zip 三格式,菜单合并,循环报文路径,manifest.json 元数据 · CHG-022 |
| FN-12 | 字典映射管理全链路：`dict_mapping` 表 + `DictMappingController/Service` + Redis 缓存 · `DictMappingProcessor`（M1-3 集成 · `ProcessRuleType.DICT_MAP`）· POI Excel 导入导出 · 三级联动嵌入 M1-3/M1-6/M2-3/4/5/6 · FN-09 联动生成 xlsx 4-sheet · CHG-028/029/031/032 |
| REG-1 | 注册中心集成：`RegistryClient` 接口抽象 + `RegistryFacade` 门面 + `NacosRegistryClient`/`EurekaRegistryClient` 双实现 + `service://` 协议解析 + `RegistryHeartbeatScheduler` 心跳骨架 · **注意**:`EurekaRegistryClient.selfRegister` 目前仅日志占位(L55-60),Q8=A **延到 v0.3.2 SOCK-5-B 补齐** · CHG-023 |
| TEST-1 | pg-testkit 测试工具增强 + PG 前端嵌入 + TESTER 角色 + DemoDbController 骨架 · v1.1:Faker 10 万条数据 + 完整 10 表 DDL + Mock 规则持久化 · CHG-024 |
| REL-1 | 打包发布形态：Maven profile + Caffeine 降级 + `scripts/build/build-portable.sh`/`build-standard.sh` + `jlink-jre.sh` + `verify-artifacts.sh` 本地冒烟 · 便携版 + 标准版 · 去 CI 化 · git tag 手动版本管理 · CHG-025 + CHG-027 |
| SOCK-1 | v0.3.0 · TCP Socket + XML 报文出站接入:Netty TCP Client + 三分帧(XML_BOUNDARY / LENGTH_PREFIX_BE4/BE8)+ 双编码(UTF-8/GBK)+ SocketClient(全局共享 EventLoopGroup · 短连接)+ SocketExecutor 编排(解析 socket 段 → renderTemplate → send → parseXml + flattenMap)+ ExecController.dispatchByType 加 SOCKET 分支 + MessageDebug 扁平化 tab + pg-testkit SocketMockServer · CHG-037 + CHG-038 · **connectionMode 仅 short 实装 · long/pooled 预留 · Eureka selfRegister 延到 v0.3.2 SOCK-5-B** |
| CR-007 | v0.3.1 · 双层功能号路由:`interface_config.function_id UNIQUE` + `ChannelFunctionIdMapper`(走 FN-12 字典 scope=3 · systemCode=ROUTE) + `FunctionIdRouteService`(Redis 缓存 · negative caching) + `RouteController POST /api/route`(免登 · **_originalFunctionId 透传下游联机**) + `ExecDispatchService`(抽 ExecController.dispatchByType 供 exec + route 共享) · CHG-039 · **前端 wizard 元数据 step 加 functionId 输入延到 v0.3.7** |
| CR-003 | v0.3.1 · 版本显示:`sys_app_info` H2 表(防篡改) + `SysAppInfoInitializer @PostConstruct` 启动 upsert + `AppInfoController GET /api/app-info`(免登) + 前端 `AppInfoBadge` 组件(SideMenu 底部 + LoginView footer · 光斓 + 中文日期 + 测试版本注) · CHG-040 |
| trace_id | v0.3.1 · 跨表追溯:sys_log/sql_audit_log/perf_stat 三表加 trace_id + 索引 · `TraceIdFilter @Order HIGHEST`(生成 UUID 或沿用上游 X-Trace-Id · MDC + 响应头 · finally 清理) · 三 AOP(SqlAuditAspect/SysLogAspect/PerfStatAspect)从 MDC 读写实体 · **v0.5.0 补 business_op_log 第四表** · CHG-041 |

## 关键代码地标（跨单元复用组件 · 禁重复实现）

| 组件 | 路径 | 首实现单元 | 被复用方 |
|---|---|:-:|---|
| `FieldProcessor` 字段加工引擎（策略模式）| `service/FieldProcessor.java` | M1-3 | M2-3/4/5,FN-12 DictMappingProcessor |
| `FormatConverter`(JSON/XML/CSV 互转 + `flattenMap`) | `utils/FormatConverter.java` (L218-230 flattenMap · v0.3.0 起 public static) | M1-1 | M1-6/7,AUX-1,SOCK-1 SocketExecutor,MessageToolsController /api/tools/xml-flatten |
| `DataSourceResolver`（REQUEST/CONST/CALC 解析）| `utils/DataSourceResolver.java` | M2-4 | M2-5 |
| `ColumnValidator`（元数据字段校验）| `utils/ColumnValidator.java` | M2-4 | M2-5 |
| `TableMetaService` 表结构 + Redis 缓存 | `service/TableMetaService.java` | M2-2 | M2-3/4/5/6 |
| `SysConfigService` KV 配置 + 热更新广播 | `service/SysConfigService.java` | SYS-4 | M2-9,M2-10,SYS-1,SYS-3 |
| `ShardRouter` 分片路由（取模/范围/补查/补零）| `utils/ShardRouter.java` | M2-8 | M2-8 exec 集成 |
| `MenuPermission` 三角色菜单白名单 | `config/MenuPermission.java` | SYS-3 | `AuthService.getMenuForCurrentUser()` |
| `RegistryFacade` 注册中心门面 | `service/registry/RegistryFacade.java` | REG-1 | v0.3.2 SOCK-5-B(Eureka selfRegister 补齐) |
| `SqlAuditAspect` / `SysLogAspect` / `PerfStatAspect` AOP 异步审计 | `aop/` | M2-9 / SYS-1 / SYS-2 | AOP 拦截全站 |
| `DictMappingProcessor` 字典映射策略 | `service/processor/DictMappingProcessor.java` | FN-12 | M1-3 集成 |
| `ExecController.dispatchByType` 接口执行分发 | `controller/ExecController.java` (L82-108) | M2-7 | v0.3.0 SOCK-1 已加 SOCKET 分支 · v0.3.2 SOCK-5 加 INBOUND_SOCKET 分支(入站编排) |
| `SocketClient` Netty TCP Client 门面 | `socket/SocketClient.java` | SOCK-1(v0.3.0) | SocketExecutor · v0.3.2 SOCK-5-A 入站服务端可复用 codec |
| `SocketExecutor` 出站编排 | `socket/SocketExecutor.java` | SOCK-1(v0.3.0) | ExecController case SOCKET |
| `socket.codec.*`(XmlBoundaryCodec + LengthPrefixCodec + FramingType + CharsetSupport) | `socket/codec/` | SOCK-1(v0.3.0) | v0.3.2 SOCK-5-A 入站服务端复用同一 codec 集 |

## 单元变更后同步文档规约（2026-07-28 复盘落实）

新单元交付 / 已有单元行为变更 / 新增关键复用组件 → **必须**同步更新以下位置:

1. **本文件** "已完成单元"表 + "关键代码地标"表
2. [`../CLAUDE.md`](../CLAUDE.md) 项目根 · "跨单元复用规约"表 · "已完成阶段"标注
3. [`../docs/01-需求/需求拆分与最小实现方案.md`](../docs/01-需求/需求拆分与最小实现方案.md) · 状态列
4. [`../docs/03-开发/开发计划.md`](../docs/03-开发/开发计划.md) · 交付状态列
5. [`../docs/03-开发/变更记录.md`](../docs/03-开发/变更记录.md) · 新增 CHG-XXX

若涉及跨模块 · 同步 [`../frontend/CLAUDE.md`](../frontend/CLAUDE.md)。
