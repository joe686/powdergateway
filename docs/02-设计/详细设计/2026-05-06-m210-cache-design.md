# M2-10 缓存查询管理设计文档

**日期**：2026-05-06  
**单元**：M2-10 缓存查询管理（双层缓存）  
**前置依赖**：M2-3（查询接口配置）、M2-7（接口发布与执行入口）

---

## 1. 目标与范围

为 SELECT 类型接口配置 Caffeine + Redis 双层缓存，降低重复查询的数据库压力，并提供命中统计与缓存管理界面。

**范围内：**
- `interface_config` 表新增缓存配置字段
- `QueryCacheManager` Bean 封装双层缓存逻辑（含分布式锁防击穿）
- Redis 命中统计计数器
- `CacheController` REST API
- 前端 `CacheList.vue` 管理页面
- 接口配置保存请求支持缓存字段写入

**范围外：**
- 分页查询不走缓存（key 空间爆炸，本期不支持）
- INSERT/UPDATE/DELETE 接口不走缓存

---

## 2. 数据模型

### 2.1 interface_config 表变更

```sql
ALTER TABLE interface_config
  ADD COLUMN cache_enabled      TINYINT      DEFAULT 0    COMMENT '是否开启缓存：0=否，1=是',
  ADD COLUMN cache_ttl_seconds  INT          DEFAULT 300  COMMENT '缓存 TTL（秒），0=永不过期',
  ADD COLUMN cache_key_template VARCHAR(512) DEFAULT ''   COMMENT 'key 模板，支持 {参数名} 占位符';
```

H2 测试 schema（`init.sql`）同步新增这 3 列。

### 2.2 Java 实体

`InterfaceConfig.java` 新增：

```java
private Integer cacheEnabled;       // 0/1
private Integer cacheTtlSeconds;    // 单位秒
private String  cacheKeyTemplate;   // 如 "query:{userId}:{status}"
```

### 2.3 缓存统计存储

纯 Redis 计数器，不新建数据库表：

| Redis Key | 说明 |
|-----------|------|
| `cache_hit:{interfaceId}` | 命中次数（Caffeine 或 Redis 层均算） |
| `cache_miss:{interfaceId}` | 未命中次数（查 DB） |

---

## 3. QueryCacheManager 核心逻辑

### 3.1 Cache Key 生成规则

```
cacheKey = "query_cache:{interfaceId}:{模板替换后的参数串}"
```

- 有模板时：将 `{参数名}` 替换为实际参数值，例如模板 `"query:{userId}:{status}"` + 参数 `{userId=1, status=active}` → `"query_cache:42:query:1:active"`
- 无模板时：按参数 Map 的 key 排序后拼接（`k1=v1&k2=v2`），保证同参数组合生成同 key

### 3.2 双层缓存执行流程

```
executeWithCache(interfaceId, config, params, dbQueryFn):
  1. 生成 cacheKey
  2. 查 Caffeine（本地缓存）→ 命中 → INCR cache_hit → 返回
  3. 查 Redis → 命中 → 写回 Caffeine → INCR cache_hit → 返回
  4. 获取分布式锁（SET NX PX 3000，lockKey = "lock:{cacheKey}"）
     4a. 获取锁失败 → 等待 50ms → 再次查 Redis → 命中则返回，否则直接查 DB（降级，不写缓存）
     4b. 获取锁成功 → 调 dbQueryFn 查 DB → 写 Redis（SETEX ttl）→ 写 Caffeine → 释放锁
  5. INCR cache_miss（步骤 4 路径）→ 返回结果
```

### 3.3 Caffeine 配置

- 最大容量：1000 条
- 本地 TTL：`min(接口配置 TTL, 60s)`（本地缓存比 Redis 短，保证一致性）

声明在 `CacheConfig.java`：

```java
@Bean
public Cache<String, Object> localCache() {
    return Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .build();
}
```

### 3.4 缓存失效与刷新

```java
// 清除指定接口所有缓存 key
void evict(Long interfaceId)
  // Redis: SCAN "query_cache:{interfaceId}:*" → DEL
  // Caffeine: 遍历 key 清除前缀匹配的条目

// 清除后立即预热
void refresh(Long interfaceId, Map<String, Object> params)
  // 1. evict(interfaceId)
  // 2. 用传入 params 调一次 DB 查询 → 写回缓存

// 清除所有接口缓存
void evictAll()
  // Redis: SCAN "query_cache:*" → DEL（批量）
  // Caffeine: invalidateAll()
```

### 3.5 测试环境降级

`RedisTemplate` 以 `@Autowired(required = false)` 注入，为 null 时（测试环境 Redis 禁用）跳过 Redis 层，直接查 DB，不抛异常。

---

## 4. API 接口设计

新增 `CacheController`，路由前缀 `/api/cache`：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/cache/list` | 查询所有 SELECT 接口缓存配置 + 命中统计 |
| `PUT` | `/api/cache/{interfaceId}/config` | 更新缓存配置（enabled/ttl/keyTemplate），自动 evict |
| `DELETE` | `/api/cache/{interfaceId}` | 清除指定接口的 Caffeine + Redis 缓存 |
| `POST` | `/api/cache/{interfaceId}/refresh` | 清除后用请求体参数预热 |
| `DELETE` | `/api/cache/all` | 一键清除所有接口缓存 |
| `GET` | `/api/cache/{interfaceId}/stats` | 查询单个接口命中/未命中统计 |

**`GET /api/cache/list` 返回结构：**

```json
[{
  "interfaceId": 42,
  "interfaceName": "订单查询",
  "cacheEnabled": 1,
  "cacheTtlSeconds": 300,
  "cacheKeyTemplate": "query:{userId}",
  "hitCount": 120,
  "missCount": 5
}]
```

**`PUT /api/cache/{interfaceId}/config` 请求体：**

```json
{
  "cacheEnabled": 1,
  "cacheTtlSeconds": 600,
  "cacheKeyTemplate": "query:{userId}:{status}"
}
```

---

## 5. 执行层集成

### 5.1 InterfaceConfigService.executeQuery() 改造

原有查询逻辑提取为私有方法 `doExecuteQuery()`，在 `executeQuery()` 开头加缓存判断：

```java
// 仅全量查询（page==null）且 cache_enabled=1 时走缓存
if (Integer.valueOf(1).equals(config.getCacheEnabled()) && page == null) {
    return cacheManager.executeWithCache(id, config, params,
        () -> doExecuteQuery(config, params, null, null));
}
return doExecuteQuery(config, params, page, pageSize);
```

对外方法签名不变，分页查询绕过缓存直接查 DB。

### 5.2 InterfaceSaveRequest 新增字段

```java
private Integer cacheEnabled;     // null 表示不修改
private Integer cacheTtlSeconds;
private String  cacheKeyTemplate;
```

`save()` 逻辑：
- 字段非 null 时写入 entity
- 若 cacheEnabled 从 1 改为 0，自动触发 `cacheManager.evict(id)`

---

## 6. 前端设计

### 6.1 CacheList.vue（路径：`views/cache/CacheList.vue`）

路由：`interface/cache`（由 PlaceholderView 替换为 CacheList.vue）

页面布局：

```
┌─ 顶栏 ──────────────────────────────────────────────────────┐
│  [一键清除全部缓存]                               [刷新统计]  │
└──────────────────────────────────────────────────────────────┘
┌─ 表格（所有 SELECT 类型接口）──────────────────────────────────┐
│ 接口名 │ 缓存状态     │ Key 模板 │ TTL(s) │ 命中 │ 未命中 │ 操作│
│ 订单查询│ [el-switch] │ q:{uid} │  300   │  120 │   5   │ …  │
└──────────────────────────────────────────────────────────────┘
```

- **缓存状态列**：`el-switch` 直接切换 cacheEnabled，切换后调 `PUT /api/cache/{id}/config` 并自动 evict
- **操作列**：
  - **编辑**：`el-popover` 内联表单修改 TTL 和 Key 模板，保存后自动 evict
  - **清除缓存**：`el-popconfirm` 二次确认 → `DELETE /api/cache/{id}`
  - **刷新缓存**：弹出参数输入对话框 → `POST /api/cache/{id}/refresh`
- **一键清除全部**：`el-popconfirm` 二次确认 → `DELETE /api/cache/all`

### 6.2 InterfaceList.vue 入口

SELECT 类型接口操作列新增「缓存」按钮，点击跳转 `/interface/cache`。

### 6.3 API 封装

新建 `src/api/cache.js`，封装上述 6 个接口。

---

## 7. 测试策略

测试文件：`M210CacheTest.java`，加 `@ActiveProfiles("test")`。

| 测试方法 | 验证点 |
|---------|--------|
| `cacheKey_noTemplate_sortedParams` | 无模板时参数按 key 排序生成一致 key |
| `cacheKey_withTemplate_replaced` | 模板 `{userId}` 被正确替换 |
| `executeQuery_cacheDisabled_alwaysHitDb` | cache_enabled=0 时每次调 DB，不写缓存 |
| `executeQuery_cacheEnabled_secondCallHitLocal` | 第二次命中 Caffeine，missCount=1，hitCount=1 |
| `evict_clearsCaffeineAndReturnsFromDb` | evict 后下次查询重新走 DB |
| `saveRequest_enableCache_persistsToDb` | 保存接口时缓存配置写入 interface_config |
| `cacheConfig_update_triggersEvict` | 修改 TTL 后旧缓存被清除 |
| `cacheList_api_returnsAllSelectInterfaces` | GET /api/cache/list 返回含 hitCount/missCount |
| `clearAll_removesAllLocalCacheEntries` | DELETE /api/cache/all 清空本地缓存 |

---

## 8. 验收标准

1. 第一次查询走 DB（missCount +1），第二次命中 Caffeine（hitCount +1），响应时间明显更短
2. 命中统计数字与 Redis 计数器一致
3. 清除缓存后下次查询重新走 DB
4. 刷新缓存后命中统计重置，缓存数据为最新
5. 一键清除全部后所有接口缓存清空
6. 缓存配置修改后旧缓存自动失效
7. 分页查询不走缓存，每次直接查 DB
