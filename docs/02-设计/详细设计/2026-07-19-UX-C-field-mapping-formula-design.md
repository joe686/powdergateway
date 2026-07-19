# UX-C 字段映射 / 加工 / 公式补齐 · 详细设计

- **日期**：2026-07-19
- **单元**：UX-C（阶段六）
- **前置依赖**：M1-2 字段映射、M1-3 字段加工引擎、M2-1 数据库连接、M2-2 表结构、M2-3 查询接口（复用 `ConditionBuilder.vue`）、SYS-3 菜单权限
- **总览设计**：`docs/02-设计/详细设计/2026-07-19-visual-refresh-and-fixes-overview.md`
- **问题清单**：`docs/03-开发/问题清单.md` § 待解决 / 2026-07-19 批次 / C 组
- **对应 CHG 编号**（待实施后追加）：CHG-018

---

## 一、目标

一次性解决 2026-07-19 用户巡检发现的 3 项字段类问题：

1. **FN-01（bug）** 字段映射页"源字段"面板解析报文 / 手工添加后不显示内容
2. **FN-02（bug）** 字段加工页"添加规则"按钮点击后规则行不出现
3. **FN-03（新功能）** 常用字段公式管理菜单当前是 `PlaceholderView`，按需求 3.2.8 落地

三者集中在 `frontend/src/views/convert/` 与 `frontend/src/views/interface/`，其中 FN-01 / FN-02 属于历史修复 CHG-003 的**回滚复发**，必须一并加自动化断言防止再次退化。

---

## 二、需求来源与范围

### 2.1 需求文档索引

| 需求出处 | 内容 |
|---------|------|
| 产品需求说明书 § 3.2.8 常用字段公式 | 明确"常用字段公式管理"专属菜单 + 基础信息 / 数据库 / 条件 / 接口字段关联 / 复制新增 / 校验 5 大要素 |
| 需求拆分与最小实现方案 P0-3 §7 | `field_formula` 表 DDL：`id / name(UNIQUE) / scene / db_connection_id / formula_json / remark / deleted / creator / create_time` |
| 问题清单 · 2026-07-19 批次 · C 组 | FN-01 / FN-02 / FN-03 三项定义与验收要点 |
| 前端 CLAUDE.md · vue-draggable-next 说明 | 明确"必须用 default slot + `v-for`，禁止 `<template #item>`"—— FN-01/02 根因直接指向该规约 |
| 变更记录 CHG-003 | 2026-03-27 已修过同一 bug，本次是**回滚复发** |
| 变更记录 CHG-005 F-4 / F-7 | FieldMapping.vue 之前已修过"空字符串固定值"与"损坏 JSON 用户提示"两个相关 bug，本次修复不得破坏 |

### 2.2 范围界定

**含**：

- FN-01：修复 `FieldMapping.vue` 中源字段面板 + 映射规则表两处 `<draggable>` 渲染
- FN-02：修复 `FieldProcess.vue` 中规则链 `<draggable>` 渲染
- FN-03：新增 `field_formula` 后端全套（Service / Controller / DTO / 测试）+ 前端 `FieldFormula.vue` 页面（列表 / 新增 / 编辑 / 复制 / 校验预览）+ 公式引用下拉组件（`FormulaPicker.vue`）
- 兜底：抽公共 `WithDraggable` 使用样例注释 + 单元测试断言渲染

**不含**：

- 公式引擎 SQL 化执行（本单元只落配置存储 + 前端可视化 + 静态语义校验；实际"把公式注入到查询 SQL 的 WHERE 子句"的运行时集成，交由后续 UX-D / 未来某单元独立衔接）
- Excel 导入 / 导出公式（需求 3.2.8 最后一段所述，标记为 P1 延后）
- 公式版本历史留存（当前设计只覆盖软删除，不做多版本）
- 现有 `FieldMapping.vue` 的视觉重塑（归 UX-A）

---

## 三、FN-01 现状分析与修复方案

### 3.1 现象

- 打开 `字段映射配置`（`/convert/field-mapping`）页面
- 点击"手动添加"填入字段名 / 或者点击"解析报文"粘贴 JSON 并解析
- 预期："源字段"区显示新增的字段块，可以拖拽到右侧映射区
- 实际："源字段"区始终为空（`el-empty` 一直显示），控制台无报错

### 3.2 根因

阅读 `frontend/src/views/convert/FieldMapping.vue` 第 54~72 行的源字段面板：

```vue
<draggable
  v-model="srcFields"
  :group="{ name: 'srcGroup', pull: 'clone', put: false }"
  :sort="false"
  item-key="id"
  class="src-field-list"
>
  <template #item="{ element }">     <!-- ← 这里 -->
    <div class="field-tag">
      ...
    </div>
  </template>
</draggable>
```

以及第 105~168 行映射规则列表使用了完全一样的 `<template #item>` 写法。

`vue-draggable-next`（本项目 `package.json` 使用的组件）v2.x 的 `render()` 函数**只读 `$slots.default`，从不读 `$slots.item`**。`#item` 是官方 `vuedraggable` v4.x 的 API，本组件完全忽略该 slot，最终渲染为空容器。

数据是有的（`srcFields.value.push(...)` 已执行），但 DOM 上没元素。`el-empty` 是通过 `v-if="srcFields.length === 0"` 控制的，实测数组非空时它不显示，只是原本该显示的字段块无处渲染，用户视觉上就是"一片空白"。

CHG-003（2026-03-27）已经修过同样的 bug；本次 FN-01 说明修复回滚了。查 git 历史可看到 CHG-005 / CHG-011 等后续多次编辑 `FieldMapping.vue` 时，被某次重构或复制粘贴回滚成 `<template #item>` 写法。

### 3.3 修复方案

将两处 `<draggable>` 全部改为 default slot + `v-for` 写法（vue-draggable-next v2.x 唯一正确用法）。

**源字段面板**（改后示例）：

```vue
<draggable
  v-model="srcFields"
  :group="{ name: 'srcGroup', pull: 'clone', put: false }"
  :sort="false"
  item-key="id"
  class="src-field-list"
>
  <div v-for="element in srcFields" :key="element.id" class="field-tag">
    <el-icon class="drag-icon"><Grid /></el-icon>
    <span class="field-name">{{ element.name }}</span>
    <el-button
      link size="small" type="danger"
      class="remove-btn"
      @click.stop="removeSrcField(element)"
    ><el-icon><Close /></el-icon></el-button>
  </div>
</draggable>
```

**映射规则列表**（改后示例）：

```vue
<draggable
  v-model="mappingRules"
  :group="{ name: 'srcGroup', pull: false, put: true }"
  item-key="id"
  handle=".row-drag-handle"
  @add="onDragAdd"
  class="mapping-rule-list"
>
  <div
    v-for="(element, index) in mappingRules"
    :key="element.id"
    class="mapping-row"
  >
    <!-- 原有的所有子元素照搬 -->
  </div>
</draggable>
```

### 3.4 附加防退化措施

1. **在文件顶部添加规约注释**（首个 `<draggable>` 上方）：

    ```vue
    <!--
      vue-draggable-next v2.x 强制约束（详见 frontend/CLAUDE.md）：
      必须用 default slot + v-for，禁止 <template #item>；否则列表渲染为空。
      历史事故：CHG-003 (2026-03-27) 首次修复 → 2026-07-19 FN-01 回滚复发。
    -->
    ```

2. **新增前端渲染断言测试**（`frontend/tests/FieldMapping.spec.js`，若前端还没测试基础设施则记录为技术债，本单元不强求）：
    - 挂载 `FieldMapping.vue`
    - 调用 `srcFields.value.push({ id: 1, name: 'foo' })`
    - 等待 `nextTick`，断言 `wrapper.findAll('.field-tag').length === 1`

3. **保留 CHG-005 F-4 / F-7 的行为**：本次修复不动 `loadTemplate()` 的 `fixedValue ?? null` 与 `catch` 中的 `ElMessage.warning`，两处已在 CHG-005 修复，不能因合并冲突退化。

---

## 四、FN-02 现状分析与修复方案

### 4.1 现象

- 打开 `字段加工规则配置`（`/convert/field-process`）页面
- 点击"添加规则"按钮
- 预期：规则链下方出现一行新规则（含步骤号、类型下拉、参数区、删除按钮）
- 实际：无任何行出现，`el-empty` 一直显示

### 4.2 根因

`frontend/src/views/convert/FieldProcess.vue` 第 52~181 行：

```vue
<draggable v-model="rules" item-key="id" handle=".drag-handle" ... @end="runPreview">
  <template #item="{ element, index }">
    <div class="rule-item">
      ...
    </div>
  </template>
</draggable>
```

与 FN-01 完全同一根因：`vue-draggable-next` 忽略 `#item` slot。CHG-003 一同修复过，此处也是回滚。

**注意**：CHG-005（问题4）已修过 `addRule()` 中 `runPreview()` 的空输入保护逻辑，本次修复必须保留 `addRule()` 中的 `if (inputValue.value) runPreview()` 判断，以及 `runPreview()` 中的 `if (!inputValue.value || rules.value.length === 0)` 判断，不能因合并冲突退化为无条件调用预览接口。

### 4.3 修复方案

同 FN-01 手法，改为 default slot + `v-for`：

```vue
<draggable v-model="rules" item-key="id" handle=".drag-handle" animation="200" @end="runPreview">
  <div
    v-for="(element, index) in rules"
    :key="element.id"
    class="rule-item"
  >
    <!-- 原有的所有子元素照搬 -->
  </div>
</draggable>
```

在文件顶部同样追加 vue-draggable-next 规约注释。

### 4.4 复盘：为什么两次回滚

历史上 CHG-003 修完后至少经过 CHG-005（F-4/F-7 fixedValue 空串 + 损坏 JSON 提示）、CHG-011（E2E 修 6 个 bug）多次编辑同一文件。**开发者在 IDE 中查看 `<draggable>` 用法时，很容易参考官方 `vuedraggable` 文档误用 `#item`**。防退化措施：

- 增加 lint 规则（长线）：`eslint-plugin-vue` 自定义规则，若同一文件同时出现 `import.*vue-draggable-next` 和 `<template #item`，报 error
- 短期通过第 3.4 节的注释 + 前端断言测试兜底

---

## 五、FN-03 字段公式管理详细设计

### 5.1 数据结构

**表：`field_formula`**（配置库，`init.sql` 中已存在 DDL；本单元只做校验和补充索引）

```sql
CREATE TABLE IF NOT EXISTS field_formula (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '公式主键',
  name              VARCHAR(128) NOT NULL COMMENT '公式名称（全局唯一）',
  scene             VARCHAR(128)          COMMENT '所属业务场景（如 客户信息、交易流水）',
  db_connection_id  BIGINT                COMMENT '关联数据库连接 db_connection.id',
  formula_json      TEXT                  COMMENT '公式配置 JSON（见 5.2）',
  remark            VARCHAR(512)          COMMENT '备注',
  deleted           TINYINT DEFAULT 0     COMMENT '软删除标记',
  creator           VARCHAR(64)           COMMENT '创建人 username',
  create_time       DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_field_formula_name (name),
  KEY idx_field_formula_scene (scene),
  KEY idx_field_formula_db_conn (db_connection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用字段公式表（UX-C FN-03）';
```

现存实体 `FieldFormula.java` / `FieldFormulaMapper.java`（P0-3 已建）字段完全对应，`update_time` 需要在实体上补 `@TableField(fill = FieldFill.INSERT_UPDATE)`。

**迁移脚本**：`backend/src/main/resources/db/migration-field-formula.sql`（幂等），增加 `update_time` 列 + 三个索引 + 表注释，供旧库升级使用。

### 5.2 `formula_json` Schema

公式本质是"一段可求值的布尔表达式或算术表达式"，最外层是一个**根节点**，根节点可以是"条件组"（逻辑连接一组条件）或"算术表达式"（用于 `SELECT ... AS xxx` 计算字段场景）。

```jsonc
{
  "version": 1,
  "type": "CONDITION_GROUP",          // "CONDITION_GROUP" | "ARITH_EXPR"
  "logic": "AND",                     // 仅 CONDITION_GROUP 使用：AND / OR / NOT
  "children": [                       // CONDITION_GROUP 的成员，可嵌套 CONDITION_GROUP 或 CONDITION
    {
      "nodeType": "CONDITION",
      "op": "GT",                     // EQ / NE / GT / GE / LT / LE / LIKE / IN / BETWEEN / IS_NULL / IS_NOT_NULL
      "left":  { "kind": "COLUMN",    "tableName": "orders", "columnName": "amount" },
      "right": { "kind": "ARITH",     "expr": {
          "op": "MUL",                // ADD / SUB / MUL / DIV
          "left":  { "kind": "REQUEST_PARAM", "paramKey": "unitPrice" },
          "right": { "kind": "CONST",         "constType": "NUMBER", "constValue": "1.2" }
      }}
    },
    {
      "nodeType": "CONDITION",
      "op": "IN",
      "left":  { "kind": "COLUMN", "tableName": "orders", "columnName": "status" },
      "right": { "kind": "CONST",  "constType": "STRING_ARRAY", "constValue": ["PAID","SHIPPED"] }
    }
  ],
  "interfaceRefs": [                  // 接口字段关联（需求 3.2.8 · 接口字段关联）
    { "interfaceId": 12, "paramKey": "unitPrice", "columnHint": "products.unit_price" }
  ]
}
```

**Operand `kind` 枚举**（Java 侧对应 `FormulaOperandKind`）：

| kind | 含义 | 附加字段 |
|------|------|---------|
| `COLUMN` | 数据库列 | `tableName`, `columnName` |
| `REQUEST_PARAM` | 接口请求参数 | `paramKey` |
| `CONST` | 字面常量 | `constType`（NUMBER/STRING/BOOLEAN/STRING_ARRAY/NUMBER_ARRAY）+ `constValue` |
| `ARITH` | 嵌套算术表达式 | `expr`（子对象，`op` + `left` + `right`，同样是 Operand） |
| `FORMULA_REF` | 引用其它公式（后续可选） | `formulaId` |

**约束**：

1. 根节点 `type=CONDITION_GROUP` 时，`children` 至少 1 项；`logic=NOT` 时 `children` 只能 1 项
2. `op=IN` 的 right 必须是 `STRING_ARRAY` 或 `NUMBER_ARRAY`
3. `op=BETWEEN` 的 right 必须是长度为 2 的 `NUMBER_ARRAY`
4. `op=IS_NULL` / `IS_NOT_NULL` 无 right
5. `ARITH.expr.op ∈ {ADD, SUB, MUL, DIV}`，且两个操作数必须是数值类型或可求值为数值的表达式
6. 所有 `COLUMN` 引用的 `tableName + columnName` 必须在 `db_connection_id` 对应的数据库中真实存在（校验时通过 `TableMetaService` 查询）

### 5.3 后端类清单

| 类 | 包 | 说明 | 是否新增 |
|----|-----|-----|---------|
| `FieldFormula.java` | `model/` | 实体（已存在，补 `update_time` 字段 + `@TableField(fill=INSERT_UPDATE)`） | 修改 |
| `FieldFormulaMapper.java` | `dao/` | Mapper（已存在，无需改） | — |
| `dto/FieldFormulaDto.java` | `model/dto/` | 前端交互 DTO：`id/name/scene/dbConnectionId/formulaJson(String→POJO)/remark/creator/createTime` | 新增 |
| `dto/FormulaJson.java` | `model/dto/` | 与 5.2 schema 一一对应的嵌套 POJO；`type / logic / children[] / interfaceRefs[]` | 新增 |
| `dto/FormulaSaveRequest.java` | `model/dto/` | 保存请求 DTO：`id?/name/scene/dbConnectionId/formulaJson/remark` | 新增 |
| `dto/FormulaValidateRequest.java` | `model/dto/` | 校验请求 DTO：`dbConnectionId/formulaJson` | 新增 |
| `dto/FormulaValidateResult.java` | `model/dto/` | 校验结果：`ok/errors[]`，`errors[i]={path, message}` | 新增 |
| `service/FieldFormulaService.java` | `service/` | 业务：list / getById / save / delete / duplicate / validate（详见下） | 新增 |
| `service/FormulaValidator.java` | `service/` | 静态语义校验器；调用 `TableMetaService.getColumns()` 校验列存在，检查 op / operand 匹配规则 | 新增 |
| `controller/FieldFormulaController.java` | `controller/` | REST 端点（5 个），Swagger `@Tag("字段公式管理")` | 新增 |
| `test/M31FieldFormulaServiceTest.java` | `test/` | Service 层测试（H2） | 新增 |
| `test/M31FieldFormulaControllerTest.java` | `test/` | Controller 层测试（MockMvc） | 新增 |
| `test/M31FormulaValidatorTest.java` | `test/` | 校验器纯单元测试 | 新增 |

（编号 `M31` 为占位，正式命名可用 `UXC01` / `UXC02` / `UXC03`，避免与已交付 M2-x 单元冲突。）

### 5.4 API 端点（5 个）

| 方法 | 路径 | 说明 | 鉴权 | 幂等 |
|------|------|-----|-----|-----|
| GET | `/api/field-formula/list?scene=&keyword=&pageNo=&pageSize=` | 分页查询公式列表，支持 scene 精确过滤 + name/remark 模糊匹配 | 需登录 | 是 |
| GET | `/api/field-formula/{id}` | 查询单条公式详情，`formulaJson` 反序列化为对象 | 需登录 | 是 |
| POST | `/api/field-formula/save` | 新增或更新（`id` 为空即新增，非空即更新）；保存前调用 `FormulaValidator.validate()`，不通过抛 `BusinessException(400, ...)` | 需登录 | 否 |
| POST | `/api/field-formula/{id}/duplicate` | 复制新增，返回新公式 id；服务端生成默认名 `{原名}_copy_{yyyyMMddHHmmss}`，前端可再改；复制不含 `create_time / creator` 继承（用当前用户和当前时间） | 需登录 | 否 |
| DELETE | `/api/field-formula/{id}` | 软删除；被任何 `interface_config.config_json` 引用时拒绝（打预留标签，本单元先不检查引用，只做软删） | 需登录 admin | 否 |
| POST | `/api/field-formula/validate` | **独立校验端点**（不保存），前端"校验预览"按钮调用；入参：`dbConnectionId + formulaJson`；返回 `FormulaValidateResult` | 需登录 | 是 |

（合计 6 个端点，用户任务清单里"5 个"是约数，`validate` 单独出来更利于前端解耦，写文档明确为 6 个。）

**审计与日志**：

- 所有写操作方法加 `@SysLogRecord(module="字段公式管理", opType="...")`
- 所有写操作方法加 `@AuditLog(type=OpType.CONFIG)`（借用现有 `SqlAuditAspect` 的通用配置操作分类；如无 CONFIG 类型则本单元只加 `@SysLogRecord`，`@AuditLog` 主要用于 SQL 增删改）

**数据源约束**：`FieldFormulaService` 全部操作使用默认 `master` 数据源（配置库），无 `@DS` 注解；`FormulaValidator` 通过 `TableMetaService.getColumns(dbConnectionId, tableName)` 查询业务库元数据（`TableMetaService` 内部已通过 `DynamicDataSourceContextHolder` 切换）。

### 5.5 Service 方法契约

```java
public interface FieldFormulaService {
    // 分页查询，keyword 匹配 name/remark
    IPage<FieldFormulaDto> list(String scene, String keyword, int pageNo, int pageSize);

    // 获取详情
    FieldFormulaDto getById(Long id);

    // 保存（新增或更新）；返回持久化后的 id
    // 内部：1) 名称唯一校验（软删除后同名允许覆盖已删记录）
    //       2) 调用 FormulaValidator.validate，不通过抛 BusinessException(400, ...)
    Long save(FormulaSaveRequest req, String creator);

    // 复制新增；返回新 id
    // 内部：读原记录 → 修改 name → 强制走 save 校验分支
    Long duplicate(Long originId, String creator);

    // 软删除
    void delete(Long id);

    // 静态校验（不写库）
    FormulaValidateResult validate(FormulaValidateRequest req);
}
```

**`FormulaValidator.validate()` 校验规则**：

1. `formulaJson` 反序列化成功（JSON 结构合法）
2. 根节点 `type ∈ {CONDITION_GROUP, ARITH_EXPR}`
3. 递归遍历所有子节点，逐一检查 5.2 节列出的约束
4. 收集所有 `COLUMN` operand，去重后按 `tableName` 分组，调用 `TableMetaService.getColumns(dbConnectionId, tableName)`，检查 `columnName` 是否存在
5. `interfaceRefs[i].interfaceId` 必须在 `interface_config` 表中存在且 `deleted=0`
6. 所有错误累加到 `FormulaValidateResult.errors`，不做短路（一次性反馈所有问题）
7. `errors.isEmpty()` 时 `ok=true`

### 5.6 前端设计

#### 5.6.1 页面 `FieldFormula.vue`

路径：`frontend/src/views/interface/FieldFormula.vue`（替换现有 `PlaceholderView`；路由已在 `router/index.js` 第 121 行注册为 `/interface/formula`，只需改 `component` import）

布局（沿用 `InterfaceList.vue` 的列表页范式，兼容 UX-A 视觉重塑后的 tokens）：

```
┌──────────────────────────────────────────────────────────┐
│ 顶部搜索栏                                                │
│  场景 [下拉]  关键字 [输入]  [查询] [重置]  [新增公式] │
├──────────────────────────────────────────────────────────┤
│ el-table                                                 │
│  ID | 名称 | 场景 | 关联数据库 | 备注 | 创建人 | 创建时间 | 操作│
│                                                          │
│  操作列：[查看] [编辑] [复制] [删除(el-popconfirm)]       │
├──────────────────────────────────────────────────────────┤
│ el-pagination                                            │
└──────────────────────────────────────────────────────────┘
```

**新增/编辑对话框**（`FormulaEditor.vue` 子组件，或作为同文件内的 `el-dialog`）：

```
[基础信息]
  公式名称 [输入]     场景 [输入或下拉]
  关联数据库 [db_connection 下拉]
  备注 [textarea]

[条件配置]
  <FormulaBuilder v-model="formulaJson"
                  :dbConnectionId="form.dbConnectionId"
                  :tableOptions="loadedTables"
                  :fieldOptionsMap="loadedFieldsMap" />

[接口字段关联]（可选，折叠区）
  接口 [下拉，从 interface_config list 拉] | 参数键 | 列名提示 | [删除]
  [+ 添加接口字段引用]

[操作]
  [校验预览]  [取消]  [保存]
  校验结果：ok=true 显示绿色 tag「校验通过」；ok=false 展开错误列表
```

#### 5.6.2 核心组件 `FormulaBuilder.vue`

路径：`frontend/src/components/formula/FormulaBuilder.vue`

**与 `ConditionBuilder.vue` 的复用关系**：

`ConditionBuilder.vue` 现只支持"扁平条件行 + 表 + 字段 + 操作符 + paramKey"，不支持嵌套逻辑组、算术表达式、常量操作数。字段公式的表达能力显著更强，**不能直接复用**，但设计上应保持一致的交互风格（下拉选择表 + 字段、el-select 选操作符、每行有删除按钮）。

新组件 `FormulaBuilder.vue` 具体结构：

```
根节点：条件组 (AND | OR | NOT)
  ├─ [+ 添加条件]     [+ 添加子组]
  │
  ├─ 条件行：<OperandInput> [操作符] <OperandInput> [删除]
  │
  ├─ 条件行：...
  │
  └─ 子组（缩进 + 递归渲染）
       ...
```

**`OperandInput.vue`**（新建，操作数编辑器）：

- 左侧：`el-select` 选择 kind：`列 / 请求参数 / 常量 / 算术表达式`
- 右侧：根据 kind 动态渲染
  - `列`：先选表（下拉），再选列（下拉，从 `TableMetaService` 拉取）
  - `请求参数`：一个 `el-input`（paramKey）
  - `常量`：先选常量类型（NUMBER/STRING/BOOLEAN/STRING_ARRAY/NUMBER_ARRAY），再输入值
  - `算术表达式`：递归打开一个 mini editor（左操作数 + 运算符 + 右操作数）

**开发提醒**：`FormulaBuilder` / `OperandInput` 都是递归组件，如果内部要用列表交互**避免拖拽**（因此无需 `vue-draggable-next`，规避 FN-01/02 的雷区）；列表用普通 `v-for` + 上下移按钮即可。

#### 5.6.3 公式复用组件 `FormulaPicker.vue`

路径：`frontend/src/components/formula/FormulaPicker.vue`

用途：需求 3.2.8 明确"在查询条件配置、增删改操作的数据来源配置中，可直接下拉选择已保存的常用公式"。本组件对外提供最小侵入：

```vue
<template>
  <el-select
    v-model="selected"
    filterable clearable
    placeholder="选择已存字段公式"
    @change="onChange"
  >
    <el-option
      v-for="f in list"
      :key="f.id"
      :value="f.id"
      :label="`${f.name}${f.scene ? '（'+f.scene+'）' : ''}`"
    />
  </el-select>
  <el-button link @click="showDetail" v-if="selected">查看详情</el-button>
</template>
```

- 挂载时调用 `GET /api/field-formula/list?pageSize=200` 一次性拉全
- `emit('update:modelValue', selectedId)` + `emit('select', fullDto)`，父组件根据需要把 `formulaJson` 内嵌到 `interface_config.config_json` 或者其它字段

**未来的接入点**（本单元不实现，只在文档标注）：

| 使用场景 | 位置 | 集成方式 |
|---------|------|---------|
| M2-3 查询接口条件 | `QueryConfig.vue` 条件区上方 | 允许"从公式引用 / 手动配置"二选一 |
| M2-5 更新条件 | `UpdateConfig.vue` 条件区上方 | 同上 |
| M2-6 删除条件 | `DeleteConfig.vue` 条件区上方 | 同上 |
| SYS-5 九步向导 · 步骤6 条件 | `InterfaceWizard.vue` step6 | 同上 |

上述集成留给后续单元（可能是 UX-D 转换向导或独立"公式集成"单元）。**本单元 FN-03 只保证公式能被创建、编辑、复制、校验、删除，且 `FormulaPicker.vue` 可用**。

#### 5.6.4 前端 API 封装 `frontend/src/api/fieldFormula.js`

严格通过 `@/api/request` 发起，不得直接 axios：

```js
import request from '@/api/request'

export const listFormulas = (params) => request.get('/field-formula/list', { params })
export const getFormula = (id) => request.get(`/field-formula/${id}`)
export const saveFormula = (data) => request.post('/field-formula/save', data)
export const duplicateFormula = (id) => request.post(`/field-formula/${id}/duplicate`)
export const deleteFormula = (id) => request.delete(`/field-formula/${id}`)
export const validateFormula = (data) => request.post('/field-formula/validate', data)
```

#### 5.6.5 前端权限接入

- 路由已存在 `/interface/formula`，component 从 `PlaceholderView` 改为 `() => import('@/views/interface/FieldFormula.vue')`
- `MenuPermission.java` 中 `ADMIN_MENUS` 和 `USER_MENUS` 已含 `/interface/formula`（CHG-011 后确认），无需改
- 页面内"删除"按钮仅在 `useUserStore.role === 'admin'` 时可见，非 admin 隐藏；后端 `DELETE` 端点也做同样校验

---

## 六、测试用例

### 6.1 FN-01 测试

**方式**：手工回归 + 前端断言（前端断言基础设施如缺失则记为技术债）

| 编号 | 步骤 | 预期 |
|------|-----|-----|
| FN-01-T1 | 打开 `/convert/field-mapping`，点击"手动添加"，输入 `orderId`，确定 | 左侧源字段区出现 `orderId` 块，可拖拽 |
| FN-01-T2 | 点击"解析报文"，粘贴 `{"a":1,"b":{"c":2}}`，格式选 JSON，解析并填充 | 左侧新增 `a` 和 `b.c` 两个字段块 |
| FN-01-T3 | 拖拽 `orderId` 块到右侧映射规则区 | 右侧新增一行映射规则，`targetField=orderId`、`srcField=orderId` |
| FN-01-T4 | 点击"添加行"，右侧新增空规则行；输入 `targetField=foo`，"来源"下拉可见 `orderId`/`a`/`b.c` | 三项均可选 |
| FN-01-T5 | 保存模板；重新进入页面并 `?templateId={id}` 加载 | 规则行完整回显 |
| FN-01-T6（防退化） | 保留 CHG-005 F-4 场景：勾"固定值"、清空输入、保存、重进 | 该行仍是"固定值"模式，输入框空 |
| FN-01-T7（防退化） | 保留 CHG-005 F-7 场景：DB 手改 `mapping_rule` 为 `"{broken"`，加载页面 | 弹 warning `模板映射规则解析失败，已清空规则，请重新配置`，规则区空 |

### 6.2 FN-02 测试

| 编号 | 步骤 | 预期 |
|------|-----|-----|
| FN-02-T1 | 打开 `/convert/field-process`，点击"添加规则" | 出现步骤 1 行，类型默认 `TRIM`，参数默认 `BOTH` |
| FN-02-T2 | 连续点 3 次"添加规则" | 出现步骤 1/2/3 三行 |
| FN-02-T3 | 未输入"测试输入值"时点"添加规则" | **不发起 preview API 请求**（复用 CHG-005 问题4 的修复） |
| FN-02-T4 | 输入 `  hello  `，添加 TRIM 规则 | 最终输出值显示 `hello` |
| FN-02-T5 | 添加 TRIM + CASE(UPPER) 两条，拖拽调整顺序 | 拖拽结束后自动重新预览，最终输出正确 |
| FN-02-T6 | 删除某规则 | 剩余规则重新预览，序号 1..N 正确 |

### 6.3 FN-03 测试

**后端测试**（`M31FieldFormulaServiceTest.java` + `M31FieldFormulaControllerTest.java` + `M31FormulaValidatorTest.java`，全部 `@ActiveProfiles("test")`）

| 编号 | 用例 | 类型 |
|------|-----|-----|
| FN-03-T1 | 保存合法 CONDITION_GROUP 公式 → 返回 id > 0 | 正常 |
| FN-03-T2 | 保存同名公式 → 抛 `BusinessException(400, "公式名称已存在")` | 边界 |
| FN-03-T3 | 保存时 `formulaJson` 为 null → 抛 `BusinessException(400, "公式配置不能为空")` | 边界 |
| FN-03-T4 | 更新已存在公式（提供 id） → 覆盖成功，`update_time` 更新 | 正常 |
| FN-03-T5 | 复制公式 → 新记录 id 不同，name 追加 `_copy_yyyyMMddHHmmss`，其余字段完全一致 | 正常 |
| FN-03-T6 | 删除公式 → 软删（`deleted=1`），后续 `getById` 返回 null | 正常 |
| FN-03-T7 | 分页查询按场景过滤 | 正常 |
| FN-03-T8 | Validator：`op=IN` 但 right 为 STRING → error | 边界 |
| FN-03-T9 | Validator：`COLUMN` 引用的列不在数据库中 → error（含 path） | 边界 |
| FN-03-T10 | Validator：`op=BETWEEN` right 长度 3 → error | 边界 |
| FN-03-T11 | Validator：多个错误一次性返回（不短路） | 边界 |
| FN-03-T12 | Validator：`interfaceRefs[0].interfaceId` 不存在 → error | 边界 |
| FN-03-T13 | Controller：GET /list 分页参数默认值 | 正常 |
| FN-03-T14 | Controller：DELETE 非 admin → 403 | 权限 |
| FN-03-T15 | Controller：POST /save 未登录 → 401 | 权限 |

**前端手工用例**：

| 编号 | 步骤 | 预期 |
|------|-----|-----|
| FN-03-UI1 | 侧边栏点击"字段公式管理" | 进入 `/interface/formula`，展示列表页 |
| FN-03-UI2 | 点"新增公式"，填基础信息，用 FormulaBuilder 添加 2 个条件 + 1 个子组，点"校验预览" | 显示"校验通过"绿色 tag |
| FN-03-UI3 | 故意选一个不存在的列，点"校验预览" | 显示红色错误列表，含"column 'xxx' not found in table 'yyy'" |
| FN-03-UI4 | 保存 → 列表出现新行 | 正常 |
| FN-03-UI5 | 点击行"复制" → 弹提示"已复制为 xxx_copy_20260719...，请修改后保存" → 列表刷新出现副本 | 正常 |
| FN-03-UI6 | 点击"删除" → el-popconfirm → 列表去掉该行 | 正常 |
| FN-03-UI7 | 非 admin 用户登录 | 列表页"删除"按钮不显示；直接 curl DELETE 后端返回 403 |

---

## 七、验收标准

| 门槛 | 标准 |
|------|-----|
| **FN-01 修复** | 表 6.1 全部 7 项手工用例通过；`FieldMapping.vue` 顶部含 vue-draggable-next 规约注释；CHG-005 已修的 F-4/F-7 行为不退化 |
| **FN-02 修复** | 表 6.2 全部 6 项手工用例通过；`FieldProcess.vue` 顶部含 vue-draggable-next 规约注释 |
| **FN-03 后端** | 表 6.3 中 T1~T15 后端测试全部通过；`mvn test -Dtest=M31*` 全绿 |
| **FN-03 前端** | 表 6.3 中 UI1~UI7 手工用例通过；列表页 / 编辑对话框 / FormulaBuilder / OperandInput / FormulaPicker 五个新文件均创建；`fieldFormula.js` API 封装通过 `request.js` |
| **权限** | 非 admin 用户不可删除公式（前端隐藏 + 后端 403） |
| **数据库** | `field_formula` 表存在 `uk_field_formula_name` 唯一索引 + `scene` / `db_connection_id` 两个普通索引；`migration-field-formula.sql` 幂等可重复执行 |
| **文档** | 变更记录 `CHG-018` 追加；`需求拆分与最小实现方案.md` 追加"字段公式管理"节；`开发计划.md` 阶段六 UX-C 行标记"已完成"；`问题清单.md` 中 FN-01/02/03 从"待解决"移至"已解决" |
| **回归** | pg-testkit 冒烟场景（登录 → 新建公式 → 校验 → 复制 → 删除）通过 |

---

## 八、实施顺序建议

按风险从低到高、依赖从简到繁排：

1. **第 1 步 · FN-01 修复**（~1 小时）
   - 改 `FieldMapping.vue` 两处 `<draggable>`
   - 加规约注释
   - 手工回归 7 项用例
   - 提交单独 commit：`fix(FieldMapping): 修复 vue-draggable-next slot 用法导致源字段不显示（FN-01, 复发自 CHG-003）`

2. **第 2 步 · FN-02 修复**（~30 分钟）
   - 改 `FieldProcess.vue`
   - 加规约注释
   - 手工回归 6 项用例
   - 单独 commit：`fix(FieldProcess): 修复 vue-draggable-next slot 用法导致规则行不显示（FN-02, 复发自 CHG-003）`

3. **第 3 步 · FN-03 后端 Skeleton + 测试红**（~2 小时）
   - 创建 DTO / Service 接口 / Controller 空实现 / Validator 空实现
   - 写完 T1~T15 全部测试（Red 阶段）
   - `mvn test -Dtest=M31*` 全部失败为预期

4. **第 4 步 · FN-03 后端实现 + 测试绿**（~4 小时）
   - 补 Service 实现 / Validator 递归遍历 / Controller 端点
   - 让测试全绿
   - 单独 commit：`feat(field-formula): 字段公式管理后端（FN-03，含 Service / Controller / Validator / 6 端点）`

5. **第 5 步 · FN-03 前端组件**（~4 小时）
   - `fieldFormula.js` API 封装
   - `OperandInput.vue` → `FormulaBuilder.vue` → `FieldFormula.vue`（自底向上）
   - `FormulaPicker.vue`（可选，本单元交付以备后续复用）
   - 手工回归 UI1~UI7
   - 单独 commit：`feat(FieldFormula): 字段公式管理前端页面 + FormulaBuilder 组件（FN-03）`

6. **第 6 步 · 文档 + 迁移脚本 + 交付清单**（~1 小时）
   - `migration-field-formula.sql`
   - 更新 `变更记录.md` CHG-018
   - 更新 `需求拆分与最小实现方案.md` 追加节
   - 更新 `开发计划.md` 阶段六 UX-C 状态
   - 更新 `问题清单.md`（FN-01/02/03 移入已解决）
   - 最终 commit：`docs(UX-C): 归档 CHG-018 + 更新问题清单/需求/开发计划`

**总工作量估算**：约 12.5 小时，可由 1 人 2 天完成，或分给 2 人（前端 / 后端各一人）1 天并行完成。前后端接口契约通过本文件第 5.4 节固化，允许并行。

---

## 九、附录：与其它 UX 组的边界

| 交互点 | 本单元处理 | 由哪组处理 |
|--------|----------|-----------|
| 字段公式管理页面的视觉风格 | 沿用当前 Element Plus 默认样式，兼容 UX-A 的 tokens.css 全局覆盖 | UX-A（视觉）|
| "字段公式管理"菜单位置 | 保留当前位置（`/interface/formula`，在接口配置分组下）；如 UX-B 决定移动，本单元跟随即可 | UX-B（信息架构）|
| 公式在其它接口配置页的"下拉调用" | 只交付 `FormulaPicker.vue` 组件本身，不改 M2-3/5/6 页面；集成到查询/更新/删除条件配置的工作留给后续单元 | 后续（可能是 UX-D 转换向导内嵌，或独立"公式集成"单元）|
| `sys_config` 中文乱码 | 无关（本单元无 sys_config 变更） | UX-F |
| 报文格式 XML 输入支持 | 无关 | UX-E FN-08 |

**并行安全性**：本单元触及的所有文件（`FieldMapping.vue` / `FieldProcess.vue` / 新增 `FieldFormula.vue` 等）均不与 UX-A / UX-B / UX-D / UX-E / UX-F 的关键文件重叠；`router/index.js` 仅修改第 121~125 行现有 route 块中的 `component` 字段（`PlaceholderView` → `FieldFormula.vue`），不新增 route，不会与其它组冲突。
