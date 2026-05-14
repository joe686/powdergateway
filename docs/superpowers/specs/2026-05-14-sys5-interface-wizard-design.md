# SYS-5 接口配置9步导航 设计文档

**日期：** 2026-05-14  
**单元：** SYS-5  
**状态：** 已审批，待实现

---

## 背景与目标

SYS-5 将 M2-3～M2-7 已有的分散式接口配置页包装为统一步骤向导，目标是让用户在 10 分钟内完成一个接口的完整配置与发布，降低新手使用门槛。

现有接口类型对应4个独立配置页（QueryConfig / InsertConfig / UpdateConfig / DeleteConfig），各自有2~4步内部向导。SYS-5 提供一个统一的10步向导作为新增入口，原有配置页保留不变。

---

## 范围约定

- **纯前端实现**，不新增任何后端接口
- 复用 `ConditionBuilder.vue`（步骤⑦），其余步骤在向导内直接实现
- 现有4个 Config 页不做任何修改
- 向导与原有配置页并存（方案C），用户可自由选择入口

---

## 架构

### 新增文件

| 文件 | 说明 |
|------|------|
| `frontend/src/views/interface/InterfaceWizard.vue` | 向导主组件，包含全部10步模板与逻辑 |
| `frontend/src/store/wizard.js` | Pinia store，管理向导全局状态 + localStorage 持久化 |

### 路由变更

在 `frontend/src/router/index.js` 的接口开发路由组中新增：

```js
{
  path: 'interface/wizard',
  name: 'InterfaceWizard',
  component: () => import('@/views/interface/InterfaceWizard.vue'),
  meta: { title: '接口配置向导' }
}
```

### 菜单变更

在 `frontend/src/components/layout/SideMenu.vue` 的"接口开发"子菜单组中，在现有菜单项**上方**插入：

```
/interface/wizard  →  接口配置向导（新）
```

原有 `/interface/dev`、`/interface/insert`、`/interface/update`、`/interface/delete` 菜单项**保留**。

### InterfaceList 入口

`frontend/src/views/interface/InterfaceList.vue` 顶部新增"向导新建"按钮，点击时先调用 `wizardStore.reset()` 清空状态，再跳转 `/interface/wizard`。

---

## wizardStore 结构

**文件：** `frontend/src/store/wizard.js`  
**localStorage key：** `wizard_draft`

```js
{
  currentStep: 0,           // 当前步骤索引（0-based）
  interfaceType: '',        // 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE'

  // 步骤②
  dbConnectionId: null,
  tableColumns: {},         // { tableName: [ColumnMeta] }，从 getTableStructure 缓存

  // 步骤③
  // SELECT 使用 mainTable + joinConfigs
  mainTable: { name: '', alias: '' },
  joinConfigs: [],          // [{ rightTableName, rightAlias, type, leftCol, rightCol }]
  // INSERT / UPDATE / DELETE 使用 tables
  tables: [],               // [{ name }]，INSERT 最多3张

  // 步骤④
  // SELECT 使用 selectedColumns
  selectedColumns: [],      // [{ tableAlias, name, type, selected, alias }]
  // INSERT / UPDATE 使用 fieldSources
  fieldSources: [],         // [{ tableName, fieldName, fieldType, sourceType, value }]
  // sourceType: 'REQUEST' | 'CONST' | 'CALC'

  // 步骤⑤
  shardConfigId: null,

  // 步骤⑥
  processRules: [],         // 同 InsertConfig / M1-3 格式

  // 步骤⑦
  conditions: [],           // ConditionBuilder 格式

  // 步骤⑧
  logEnabled: true,

  // 步骤⑨
  previewParams: {},
  previewResult: [],

  // 步骤⑩
  interfaceName: '',
  savedId: null,            // 步骤⑨自动保存后获得的接口 ID
}
```

**持久化：** 每次状态变化自动写 localStorage。进入 `/interface/wizard` 时若存在草稿，弹 `ElMessageBox.confirm` 询问"发现未完成的配置，是否恢复？"，用户拒绝则调用 `wizardStore.reset()`。

---

## 10步规格

### 步骤可见性

| 步骤 | 内容 | SELECT | INSERT | UPDATE | DELETE |
|------|------|:------:|:------:|:------:|:------:|
| ① 接口类型 | `el-radio-group` | ✓ | ✓ | ✓ | ✓ |
| ② 数据库连接 | `el-select` | ✓ | ✓ | ✓ | ✓ |
| ③ 选表结构 | 主表+JOIN / 目标表 | ✓ | ✓ | ✓ | ✓ |
| ④ 字段配置 | 勾选列 / 数据来源 | ✓ | ✓ | ✓ | **skip** |
| ⑤ 分库分表 | `el-select`+跳过 | opt | opt | opt | opt |
| ⑥ 字段加工 | 规则列表+跳过 | opt | opt | opt | **skip** |
| ⑦ 条件配置 | `ConditionBuilder.vue` | opt | **skip** | ✓ | ✓ |
| ⑧ 日志开关 | `el-switch` | ✓ | ✓ | ✓ | ✓ |
| ⑨ 预览测试 | 调用 preview API | ✓ | ✓ | ✓ | ✓ |
| ⑩ 保存发布 | 填名称，save+publish | ✓ | ✓ | ✓ | ✓ |

**跳过机制：** DELETE 接口的步骤④⑥在步骤条上不渲染，步骤编号自动收缩为8步（向导内部 `computedSteps` 过滤掉不适用步骤）。INSERT 跳过步骤⑦同理。

### 各步骤详细说明

**步骤① 接口类型**
- `el-radio-group`：SELECT / INSERT / UPDATE / DELETE
- 选择后更新 `wizardStore.interfaceType`，重置后续所有步骤数据

**步骤② 数据库连接**
- `el-select`，选项来自 `listConnections()`
- 选择后触发 `getTableStructure(dbId)` 并缓存到 `wizardStore.tableColumns`

**步骤③ 选表结构**
- SELECT：主表选择器（表名+别名）+ JOIN 关联表配置（与 QueryConfig 步骤①逻辑相同）
- INSERT：最多3张表，每张选择表名（`el-select`），"添加表"按钮控制
- UPDATE / DELETE：单表选择器

**步骤④ 字段配置**（DELETE 跳过）
- SELECT：`el-table` 展示所有可用列（来自所选表+关联表），每行含勾选框和输出别名输入框
- INSERT：`el-table` 展示目标表所有字段，每行含数据来源下拉（REQUEST/CONST/CALC）和值输入框
- UPDATE：上半部分"修改字段"（同 INSERT），下半部分"条件字段"（移至步骤⑦配置）

**步骤⑤ 分库分表**（可选）
- `el-select` 列出所有已配置的分片规则（来自 `listShardConfigs()`）
- 右侧"跳过"链接，点击直接进入下一步

**步骤⑥ 字段加工**（可选，DELETE 跳过）
- 加工规则列表（`el-table`），每行：选择加工类型 + 参数
- "跳过"链接，processRules 保持 `[]`

**步骤⑦ 条件配置**（INSERT 跳过）
- 直接渲染 `<ConditionBuilder v-model="conditions" :field-options="conditionFieldOptions" />`
- `conditionFieldOptions` 派生规则：SELECT 取 mainTable + joinConfigs 的所有列；UPDATE/DELETE 取 `tables[0]` 的所有列（均从 `wizardStore.tableColumns` 读取）
- UPDATE/DELETE：必填，校验至少1条条件
- SELECT：可选，有"跳过"链接

**步骤⑧ 日志开关**
- `el-switch`，默认开启，对应 `interface_config.log_enabled`

**步骤⑨ 预览测试**
- 先自动调用 `saveInterface(buildPayload())` 获得 savedId（后端保存为 draft 状态）
- SELECT/UPDATE：调用 `POST /api/interface/{id}/preview`，填写条件参数后展示结果表格
- DELETE：调用 `POST /api/interface/{id}/delete-preview`，展示待删数据预览（含警告提示）
- INSERT：调用 `POST /api/interface/{id}/preview` 做插入预检（不实际写库）

**步骤⑩ 保存发布**
- `el-form`：接口名称（必填，预填步骤中已输入的名称）
- "仅保存（草稿）"按钮：调用 `saveInterface()`，不发布
- "保存并发布"按钮：调用 `saveInterface()` + `publishInterface(savedId)`，成功后跳转 `/interface/list`

---

## 数据流

```
步骤② 选DB  →  getTableStructure(dbId)  →  wizardStore.tableColumns
步骤③ 选表  →  派生 allColumns (SELECT) / 加载目标表字段 (INSERT/UPDATE)
步骤④ 字段  →  selectedColumns / fieldSources 写入 store
步骤⑤ 分片  →  listShardConfigs()  →  shardConfigId
步骤⑨ 预览  →  saveInterface(buildPayload())  →  savedId  →  previewInterface(savedId, params)
步骤⑩ 发布  →  publishInterface(savedId)  →  跳转 /interface/list
```

**buildPayload()：** 根据 `interfaceType` 组装 `InterfaceSaveRequest`，字段含义与现有 Config 页保持一致：

```js
{
  id: savedId || undefined,
  name: interfaceName,
  dbConnectionId,
  type: interfaceType,
  logEnabled,
  shardConfigId: shardConfigId || undefined,
  configJson: JSON.stringify({
    // SELECT: tables, joins, fields, conditions, processRules
    // INSERT: tables, fieldSources, processRules
    // UPDATE: tables, fieldSources, conditions, processRules
    // DELETE: tables, conditions
  })
}
```

---

## 页面布局

```
┌─────────────────────────────────────────────────────────┐
│ 接口配置向导                    [草稿已保存] [返回列表]  │
├─────────────────────────────────────────────────────────┤
│ ①✓ ②✓ ③● ④○ ⑤○ ⑥○ ⑦○ ⑧○ ⑨○ ⑩○      步骤条 │
├─────────────────────────────────────────────────────────┤
│                                                      [?] │
│  Step N · 步骤标题                                       │
│  ┌─────────────────────────────────────────────────┐    │
│  │  步骤内容区                                      │    │
│  └─────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────┤
│ [← 上一步]               步骤 N/10     [下一步 →]       │
└─────────────────────────────────────────────────────────┘
```

- 步骤条：已完成绿色 ✓，当前蓝色高亮，未到达灰色
- 每步右上角 `el-tooltip` 帮助气泡（`?` 图标）
- 底部自动保存提示文字

---

## 前置校验规则

| 步骤 | 校验 |
|------|------|
| ① | 已选接口类型 |
| ② | 已选数据库连接 |
| ③ SELECT | 已选主表 |
| ③ INSERT/UPDATE/DELETE | 已选至少1张表 |
| ④ SELECT | 至少勾选1个返回字段 |
| ④ INSERT/UPDATE | 所有字段已配置数据来源 |
| ⑦ UPDATE/DELETE | 至少1条条件，且含主键或唯一键字段 |
| ⑩ | 接口名称不为空 |

---

## 验收标准

1. SELECT 接口：按10步走完，发布后通过 `/api/exec/{id}` 成功调用
2. INSERT 接口：字段数据来源含 REQUEST/CONST/CALC 各一，发布后可执行
3. DELETE 接口：步骤条自动收缩为8步（无④⑥），配置完成后发布可执行
4. UPDATE 接口：步骤条显示全部10步（无跳过），步骤⑦条件配置为必填
5. 中途关闭再访问 `/interface/wizard`，弹恢复草稿提示，恢复后从中断步骤继续
6. "向导新建"入口与原有各类型配置页入口均可正常使用，互不影响
7. 步骤⑨ DELETE 预览显示待删数据，展示行数与数据库一致

---

## 不含内容

- 不新增任何后端接口
- 不修改现有4个 Config 页（QueryConfig / InsertConfig / UpdateConfig / DeleteConfig）
- 不实现向导的编辑已有接口功能（只支持新建；编辑走原有 Config 页）
- 不实现 UPDATE 接口步骤⑩中的接口路径（path）自定义（使用系统默认 `/api/exec/{id}`）
