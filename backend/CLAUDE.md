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

## 下一阶段：SYS-2、SYS-4

| 单元 | 内容 |
|------|------|
| SYS-2 | 性能统计：AOP 统计耗时，ECharts 折线图+柱状图，定时告警检查 |
| SYS-4 | 系统配置：全局 KV 编辑，`ApplicationEvent` 通知各服务刷新 |
