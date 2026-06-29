# M2-5 修改接口配置 设计文档

**日期**：2026-05-04  
**单元**：M2-5  
**前置依赖**：M2-1、M2-2、M2-4（DataSourceResolver、ColumnValidator）、M2-9（SqlAuditAspect）

---

## 一、范围

可视化配置 UPDATE 接口，支持多表（最多3张），强制唯一条件校验，执行前自动抓取修改前快照写入审计日志。不含接口发布（M2-7）。

---

## 二、Config JSON 结构

```json
{
  "tables": [
    {
      "tableName": "orders",
      "fields": [
        {"column": "status",      "sourceType": "REQUEST", "paramKey": "status"},
        {"column": "update_time", "sourceType": "CONST",   "constValue": "NOW()"}
      ]
    },
    {
      "tableName": "order_items",
      "fields": [
        {"column": "quantity", "sourceType": "CALC", "expression": "price * num"}
      ]
    }
  ],
  "conditions": [
    {"tableName": "orders",      "field": "id",       "op": "EQ", "paramKey": "orderId"},
    {"tableName": "order_items", "field": "order_id",  "op": "EQ", "paramKey": "orderId"}
  ]
}
```

- `fields` 复用 `FieldInsertConfig`（M2-4），支持 REQUEST / CONST / CALC 三种数据来源
- `conditions` 为统一列表，每条含 `tableName`，映射到对应表的 WHERE 子句

### 升级点（已预留，当前不实现）

当前所有条件在同一 UI 区统一配置（"共享条件集"方案）。`ConditionConfig.tableName` 字段已预留，未来如需"每表独立条件分组"，只需改前端渲染方式，JSON Schema 无需变更。

---

## 三、后端设计

### 新建文件

| 文件 | 说明 |
|------|------|
| `model/dto/UpdateConfigJson.java` | DTO，含 `TableUpdateConfig`（tableName + fields）和 `ConditionConfig`（tableName / field / op / paramKey）内部类 |
| `utils/UpdateBuilder.java` | 静态工具类，`build(String tableName, Map<String,Object> fieldValues, List<ConditionConfig> tableConditions, Map<String,Object> params)` → `SqlResult`，生成 `UPDATE t SET col=? WHERE cond=?`；`tableConditions` 由调用方按 `tableName` 过滤后传入 |
| `test/M25UpdateConfigTest.java` | 集成测试（见第五节） |

### 扩展文件

**`InterfaceConfigService`**

- `executeUpdate(Long id, Map<String, Object> params)`
  1. 加载 `interface_config`，解析 `config_json` → `UpdateConfigJson`
  2. 逐表：`DataSourceResolver.resolve()` → `ColumnValidator.validate()` → `UpdateBuilder.build()`
  3. JDBC 手动事务，任意表失败全部回滚
- `saveInterfaceConfig()` 保存时校验：`conditions` 中每张表至少有一个字段是主键或唯一索引（调用 M2-2 的 `TableMetaService`），否则抛 `BusinessException`

**`InterfaceConfigController`**

- `execute()` 按 `type` 字段分发：`INSERT` → `executeInsert`，`UPDATE` → `executeUpdate`（执行接口对外开放，无需登录态）

**`SqlAuditAspect`（M2-9）**

- `@Around` 拦截 `executeUpdate`，在放行前：
  1. 从方法入参取接口 ID，通过 **`InterfaceConfigMapper`**（直接注入，避免循环依赖）加载 `interface_config`
  2. 解析 `config_json` → `UpdateConfigJson`，对每张表按对应 `conditions` 执行 `SELECT * WHERE {条件} LIMIT 100`，收集快照 JSON
  3. 执行完成后，将快照写入 `sql_audit_log.before_snapshot`

### 复用组件（不重新实现）

| 组件 | 来源 | 用途 |
|------|------|------|
| `DataSourceResolver` | M2-4 | 解析 REQUEST / CONST / CALC |
| `ColumnValidator` | M2-4 | 校验字段非空约束、类型 |
| `TableMetaService` | M2-2 | 唯一键/主键元数据查询 |

---

## 四、前端设计

### 新建：`views/interface/UpdateConfig.vue`

两区布局：

**修改字段配置区**
- 「添加表」按钮（最多3张），表名从 M2-2 表结构树选择
- 每张表展开一个字段表格：字段名 | 类型 | 数据来源（REQUEST / CONST / CALC）| 值/表达式
- 交互模式与 `InsertConfig.vue` 一致

**修改条件配置区**（使用扩展后的 `ConditionBuilder.vue`）
- 每行：目标表下拉 + 字段下拉（主键字段加 ★ 后缀）+ 操作符 + 参数名
- 保存前端预校验：每张表至少有一个条件，否则阻止提交

### 扩展：`ConditionBuilder.vue`（M2-3）

新增两个 prop（均可选，默认 `false`，向后兼容 M2-3 调用处）：

| Prop | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `highlightPrimaryKeys` | Boolean | false | 为 true 时主键字段名后追加 ★ |
| `showTableColumn` | Boolean | false | 为 true 时每行条件前增加"目标表"列 |

M2-3 调用处不传这两个 prop，行为完全不变。

---

## 五、测试覆盖（M25UpdateConfigTest.java）

```
正常路径：
1. 保存 UPDATE 配置（单表，含主键条件） → 返回 id，type=UPDATE
2. 单表 UPDATE 执行（REQUEST 数据来源）→ 字段值正确更新
3. 单表 UPDATE 执行（CONST 数据来源）→ 字段值为固定值
4. 多表 UPDATE 执行（成功）→ 两张表均正确更新
5. 审计日志含 before_snapshot → 修改前数据快照不为空

边界 / 异常：
6. 保存时无任何条件 → 报错（BusinessException）
7. 保存时条件字段均非主键/唯一键 → 报错（BusinessException）
8. 多表 UPDATE 第二张表失败 → 事务回滚，两张表均未修改
```

---

## 六、API 端点

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/interface/save` | 保存 UPDATE 配置（含唯一键校验） | 需登录 |
| POST | `/api/interface/{id}/execute` | 执行 UPDATE（对外开放） | 无需登录 |

---

## 七、不含内容

- 接口发布、状态流转（M2-7）
- 分库分表路由（M2-8）
- 缓存（M2-10）
