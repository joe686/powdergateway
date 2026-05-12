# M2-8 分库分表配置 设计文档

**日期**：2026-05-12  
**交付单元**：M2-8  
**前置依赖**：M2-1（数据库连接管理）、M2-7（接口发布 + 执行入口）

---

## 一、背景与目标

大型工程项目中，同一张逻辑表往往按某个路由字段（如 `userId`、`tradeId`）拆分到多个物理库/表，称为分库分表。PowerGateway 需要让用户通过可视化界面配置分片规则，在接口执行时自动路由到正确的物理库和表，无需硬编码。

**核心需求**：
- 支持**取模路由**（主要）和**范围路由**（兼容）
- 支持**单库多表**和**多库多表**两种映射
- 路由字段可能不在请求参数中，需配置**补查**（从已有数据源中 SELECT 一条记录取出路由字段值）
- 取模路由表名按前缀 + 索引自动生成，支持配置**是否补零及位数**

---

## 二、shard_rule JSON 结构

每条 `shard_config` 记录的 `shard_rule` 字段存储以下 JSON：

### 2.1 取模路由（MODULO）

```json
{
  "routingField": "userId",
  "fieldLookup": {
    "dbConnectionId": 1,
    "table": "orders",
    "conditionColumn": "order_id",
    "conditionParamKey": "orderId",
    "targetColumn": "user_id"
  },
  "algorithm": {
    "type": "MODULO",
    "divisor": 16
  },
  "dbSegments": [
    { "dbConnectionId": 1, "tablePrefix": "orders_", "indexStart": 0, "indexEnd": 7,  "indexPadding": 0 },
    { "dbConnectionId": 2, "tablePrefix": "orders_", "indexStart": 8, "indexEnd": 15, "indexPadding": 0 }
  ]
}
```

字段说明：

| 字段 | 说明 |
|------|------|
| `routingField` | 用于取模/范围计算的字段名 |
| `fieldLookup` | 可选。不为 null 时先补查该字段，再路由 |
| `fieldLookup.dbConnectionId` | 补查使用的数据源（M2-1 中配置的连接） |
| `fieldLookup.conditionParamKey` | 从请求参数取出的条件值对应的 key |
| `fieldLookup.conditionColumn` | WHERE 条件列名 |
| `fieldLookup.table` | 补查表名 |
| `fieldLookup.targetColumn` | 取出的列，值作为 routingField |
| `algorithm.type` | `MODULO` 或 `RANGE` |
| `algorithm.divisor` | 取模的除数（MODULO 时必填） |
| `dbSegments` | 分段映射列表，每段描述连续的分片索引范围 |
| `dbSegments.tablePrefix` | 表名前缀，最终表名 = prefix + pad(index, padding) |
| `dbSegments.indexPadding` | 0 = 不补零（`orders_3`）；2 = 补两位（`orders_03`） |

**单库多表**：所有 `dbSegments` 的 `dbConnectionId` 相同。

### 2.2 范围路由（RANGE）

```json
{
  "routingField": "tradeId",
  "algorithm": { "type": "RANGE" },
  "shards": [
    { "rangeStart": 1,    "rangeEnd": 999,  "dbConnectionId": 1, "tableName": "trade_001" },
    { "rangeStart": 1000, "rangeEnd": 1999, "dbConnectionId": 2, "tableName": "trade_001" }
  ]
}
```

`shards` 中每条记录显式指定库和表，范围端点均为闭区间。

---

## 三、后端设计

### 3.1 新增 DTO 类

**`ShardRuleJson.java`**（`model/dto/`）：

```java
// 顶层
String routingField;
FieldLookupConfig fieldLookup;   // 可为 null
AlgorithmConfig algorithm;
List<DbSegment> dbSegments;      // MODULO 使用
List<ShardItem> shards;          // RANGE 使用

// 内部类
class FieldLookupConfig {
    Long dbConnectionId;
    String table, conditionColumn, conditionParamKey, targetColumn;
}
class AlgorithmConfig {
    String type;     // MODULO / RANGE
    Integer divisor; // MODULO 时填
}
class DbSegment {
    Long dbConnectionId;
    String tablePrefix;
    int indexStart, indexEnd, indexPadding;
}
class ShardItem {
    Long rangeStart, rangeEnd, dbConnectionId;
    String tableName;
}
```

**`ShardRouteResult.java`**（`model/dto/`）：

```java
Long dbConnectionId;
String dbName;
String tableName;
```

**`ShardSaveRequest.java`**（`model/dto/`）：

```java
Long id;           // null = 新增
String name;
String shardRule;  // JSON 字符串
```

---

### 3.2 ShardRouter（`utils/ShardRouter.java`）

纯静态工具类，无 Spring 依赖，方便单元测试。

**路由入口**：

```java
public static ShardRouteResult route(ShardRuleJson rule, String fieldValue)
```

**取模路由流程**：
```
long val = Long.parseLong(fieldValue);
int idx  = (int)(val % rule.algorithm.divisor);
找 dbSegments 中 indexStart <= idx <= indexEnd 的段:
    tableName = segment.tablePrefix + pad(idx, segment.indexPadding)
    return ShardRouteResult{dbConnectionId, tableName}
找不到 → BusinessException("取模索引 idx 无匹配分段")
```

**范围路由流程**：
```
long val = Long.parseLong(fieldValue);
找 shards 中 rangeStart <= val <= rangeEnd:
    return ShardRouteResult{dbConnectionId, tableName}
找不到 → BusinessException("值 val 无匹配分片范围")
```

**补零工具**：
```
pad(int idx, int padding):
    padding == 0 → String.valueOf(idx)
    else         → String.format("%0{padding}d", idx)
```

---

### 3.3 ShardConfigService（`service/ShardConfigService.java`）

```java
// CRUD
List<ShardConfig> list(String name, int page, int size)
Long save(ShardSaveRequest req)
void delete(Long id)

// 路由预览（含补查）
ShardRouteResult preview(Long shardConfigId, Map<String, Object> params)
```

**preview 内部流程**：
1. 加载 `ShardConfig`，解析 `shardRule` JSON → `ShardRuleJson`
2. 若 `fieldLookup != null`：
   - 用 `fieldLookup.dbConnectionId` 建立 JDBC 连接（AES 解密密码）
   - 执行 `SELECT {targetColumn} FROM {table} WHERE {conditionColumn} = ?`（参数从 `params.get(conditionParamKey)` 取）
   - 将结果写入 `params[routingField]`
3. 从 `params` 取 `routingField` 的值，调用 `ShardRouter.route()`
4. 用路由结果的 `dbConnectionId` 查 `db_connection` 表，补充 `dbName`
5. 返回 `ShardRouteResult`

---

### 3.4 ShardConfigController（`controller/ShardConfigController.java`）

```
GET  /api/shard/list              → 分页列表
POST /api/shard/save              → 新增/更新
DELETE /api/shard/{id}            → 逻辑删除
POST /api/shard/{id}/preview      → Body: { "params": {"orderId": "12345"} }
                                     返回: { dbConnectionId, dbName, tableName }
```

---

### 3.5 ExecController 集成

在 `InterfaceConfigService` 新增私有方法：

```java
private ShardRouteResult resolveSharding(InterfaceConfig config, Map<String, Object> params)
```

逻辑：若 `config.shardConfigId == null` 返回 null；否则调用 `ShardConfigService.preview(shardConfigId, params)`。

**生效范围**：分片路由对以下执行路径生效：

| 执行路径 | 是否走分片路由 | 说明 |
|---------|-------------|------|
| SELECT（`cache_enabled=0`，`doExecuteQuery`） | ✅ | 直接执行路径，路由后再查库 |
| SELECT（`cache_enabled=1`，走 `cacheManager.executeWithCache`） | ❌ | 缓存优先，命中时无需路由；本期不支持缓存+分片组合 |
| INSERT（`executeInsert`） | ✅ | |
| UPDATE（`executeUpdate`） | ✅ | |
| DELETE（`executeDelete`） | ✅ | |

实现位置：在 `doExecuteQuery`、`executeInsert`、`executeUpdate`、`executeDelete` 四个方法内部，在取 `DbConnection` 之前执行分片路由：

```java
ShardRouteResult shard = resolveSharding(config, params);
Long dbConnId = shard != null ? shard.getDbConnectionId() : config.getDbConnectionId();
// 解析 config_json 后，若 shard != null，将 configObj 的第一张表名替换为 shard.tableName
```

> 表名替换只覆盖 config_json 里的**第一张主表**，JOIN 副表表名不变（分库分表场景通常只有主表分片）。
>
> cache_enabled=1 的 SELECT 接口若同时配置了 `shard_config_id`，执行时记录 WARN 日志并忽略分片路由，以缓存结果为准。

---

### 3.6 InterfaceSaveRequest 新增字段

```java
Long shardConfigId;  // 可为 null
```

`save()` 方法中同步写入 `interface_config.shard_config_id`。

### 3.7 InterfaceList.vue 联动（对已有页面的最小改动）

在 `InterfaceList.vue` 的新建/编辑弹窗中新增一个**可选**的「分库分表配置」下拉框（`el-select`），数据源为 `GET /api/shard/list` 返回列表，可清空（不绑定则为 null）。保存时将选中的 `shardConfigId` 带入请求体。

---

## 四、前端设计（`views/interface/ShardConfig.vue`）

### 页面结构

**列表区**：`el-table`，列：名称、路由算法（el-tag）、创建时间、操作（编辑/删除）

**新建/编辑弹窗**（`el-dialog`）：

1. **基础信息**：配置名称（`el-input`）
2. **路由算法**：`el-radio-group`（取模 / 范围）
   - 取模：显示「除数」输入框 + `dbSegments` 动态表格（添加分段：dbConnectionId 下拉、表前缀、起始索引、结束索引、补零位数）
   - 范围：显示 `shards` 动态表格（添加分片：起始值、结束值、dbConnectionId 下拉、表名）
3. **路由字段**：输入框（填写字段名，如 `userId`）
4. **补查配置**（`el-collapse` 折叠，默认收起）：
   - 数据源下拉（M2-1 连接列表）、查询表名、条件列名、请求参数 key、目标列名
5. **路由预览**：参数输入区（`el-input` key-value 动态列表）+ 「预览」按钮 → 显示命中的库名和表名

**删除**：`el-popconfirm` 二次确认。

### 路由注册

```js
// router/index.js
{ path: '/interface/shard', component: () => import('@/views/interface/ShardConfig.vue'),
  meta: { title: '分库分表配置' } }
```

菜单在"可视化接口开发"下新增「分库分表配置」项。

---

## 五、测试设计

### M28ShardRouterTest（纯单元，无 Spring）

| 用例 | 验证点 |
|------|--------|
| 取模路由_正常命中_单库 | 返回正确 dbConnectionId 和表名 |
| 取模路由_边界值_indexStart | index=0 路由到第一段 |
| 取模路由_边界值_indexEnd | index=divisor-1 路由到最后一段 |
| 取模路由_补零两位 | indexPadding=2，表名为 `orders_03` |
| 取模路由_多库_高位索引命中第二库 | shard 8~15 路由到 dbConnectionId=2 |
| 取模路由_无匹配分段_抛异常 | dbSegments 不覆盖某索引时 BusinessException |
| 范围路由_正常命中 | 返回正确库表 |
| 范围路由_边界值 | rangeStart 和 rangeEnd 本身均命中 |
| 范围路由_无匹配_抛异常 | 超出所有范围时 BusinessException |

### M28ShardConfigTest（@SpringBootTest + @Transactional）

| 用例 | 验证点 |
|------|--------|
| 保存_新增_返回ID | save() 返回非 null id，list() 能查到 |
| 保存_更新_覆盖规则 | 同 id 再次 save() 更新 shardRule |
| 删除_逻辑删除 | delete() 后 list() 不再返回该记录 |
| preview_取模路由_无补查 | 直接从 params 取值，返回正确 tableName |
| preview_取模路由_补查字段 | fieldLookup 配置时先补查再路由（用 H2 内置表模拟） |

---

## 六、验收标准

1. 配置 2 个数据库各 8 张表的取模分片规则（divisor=16），预览时输入不同 userId 分别路由到正确库表
2. 配置补查（用 orderId 查 userId）后，预览接口自动完成补查再路由
3. 范围路由配置后，预览显示正确的库表匹配
4. 已发布的 SELECT/INSERT/UPDATE/DELETE 接口配置了 `shard_config_id` 后，执行时自动切换到路由所得的数据库连接和表名
5. 所有测试通过（新增测试 + 原有 241 个测试不退化）

---

## 七、不在本次范围内

- 多级联查（补查后再补查）
- 哈希路由（非数值字段）
- 日期分表
- 分片配置与接口执行的完整压测
