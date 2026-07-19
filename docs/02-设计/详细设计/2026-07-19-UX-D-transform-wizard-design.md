# UX-D 接口转换配置流程重构 + 转换向导 详细设计

**日期：** 2026-07-19
**单元：** UX-D（阶段六 · 第 3 波）
**关联问题：** FN-04（111.txt #12）、FN-05（111.txt #13）
**状态：** 设计中，待审批
**依赖：** UX-B（信息架构，菜单分组落定后开工）

---

## 1. 目标

用户在 111.txt 中明确指出，接口转换配置的六大配置项（报文格式转换 / 字段映射 / 字段加工 / 渠道模板 / 转换模板 / 端口路由）当前散落在同一个菜单分组下，**功能都已完备，但没有一条"从系统到系统的报文全生命周期"的引导流程**。UX-D 单元要在不改动已有 6 个配置页的前提下：

1. **FN-04**：把用户表述的"层层递进"业务流程沉淀成一条明确的向导路径 —— 系统 → 功能号 → 端口 → 路由 → 模板 → 测试 → 发布。
2. **FN-05**：仿照 SYS-5 `InterfaceWizard.vue` 提供"接口转换配置向导"（`/convert/wizard`），一次性带用户走完 7 步。
3. **架构收敛**：抽出公共向导框架 `WizardShell.vue`，可视化接口向导（原 10 步）与转换接口向导（新 7 步）复用同一套导航、草稿保存、步骤条组件。

**核心约束：**
- 现有 `/interface/wizard` 路由与其可视化接口 10 步交互**行为不得退化**（SYS-5 已上线，`InterfaceWizard.vue` 曾出过 E2E-6 空引用崩溃，重构时需保留同类保护）
- 不允许改动 M1-1 ～ M1-7 的既有 CRUD 页面、Controller、Service
- 尽量少加数据库字段，能通过前端拼装的绝不落库

---

## 2. 需求来源与范围

### 2.1 用户原文（111.txt #12）

> 首先要明确的就是什么系统到什么系统、报文要求是什么。同一种报文的转换仅是换报文头，或者换报文体的字段。更复杂一点是格式、报文体、加工字段送到对方系统。这些功能上大方向要明确。然后定端口，把功能号定清楚，功能号设置好了之后按照前面设置好的系统的定义，跟系统定义挂钩的端口路由转发绑定，这样一个报文的来龙去脉就搞清楚了。再然后是对报文的解析还有加工。设置转换模板时选择我们转换的功能号，层层递进。最后转换的细节设置完成后可以在报文格式转换的地方测试。

### 2.2 范围

- **含**：
  - `WizardShell.vue` 公共骨架抽取（`el-steps` + 前/后一步 + 草稿保存 + 返回列表）
  - `SelectInterfaceSteps.vue` 迁移原 `InterfaceWizard.vue` 的 10 步业务视图
  - `TransformInterfaceSteps.vue` 新增 7 步业务视图
  - `useWizardShell` composable 封装步骤索引、跳过规则、校验回调
  - `useTransformWizardStore` Pinia store（转换向导专属状态 + `localStorage` 草稿）
  - 新路由 `/convert/wizard` + 菜单入口 + `MenuPermission.java` 白名单
- **不含**：
  - 不新增/修改 6 个转换配置页（M1-1 ～ M1-7）的 CRUD 行为
  - 不重构 `SYS-5 InterfaceWizard.vue` 的业务字段（只做同构抽壳）
  - 不做转换接口的"编辑既有配置"入口（首版只支持新建，编辑走各 CRUD 页）
  - 不实现"系统"实体的独立管理页面（本单元只作为渠道概念的前端别名，见 §3.2）

---

## 3. FN-04 接口转换配置流程重构分析

### 3.1 现有流程 vs 用户期望流程

| 步骤 | 现状（分散在 6 个菜单） | 用户期望（层层递进） |
|------|-------------------------|----------------------|
| 1 | 用户需自行判断先做哪个 | **明确"从系统 A 到系统 B"** —— 定义源/目标端及报文形态 |
| 2 | 无独立"功能号"概念 | **定义功能号** —— 该次转换的业务编号，后续所有关联配置都挂靠这个功能号 |
| 3 | 端口路由配置项在最后一屏 | **定端口** —— 定目标系统的物理接入端口（URL + Method + 超时） |
| 4 | 端口路由与渠道解耦，需手工挑 | **端口路由绑定** —— 把功能号绑定到端口和渠道，让"报文来龙去脉"贯通 |
| 5 | 模板独立管理，需手工挑 | **选/建转换模板** —— 选择时以功能号为线索检索既有模板 |
| 6 | 报文格式转换页作为独立工具 | **在报文格式转换页做端到端测试** —— 关联刚才选定的模板 |
| 7 | 无"发布"概念（模板/路由都是即存即用） | **发布/启用** —— 从"草稿"进入"正式启用"状态 |

### 3.2 是否需要新增数据模型？

**结论：不新增独立表，只在既有表上加两个可选字段。**

原因分析：
- **"系统 A → 系统 B"** 概念：在 M1-4 `channel_config` 表中已经通过 `channel_code`/`channel_name` 表达了"外部系统"这个语义。转换的方向由 `port_route.request_template_id`（A→B）/`response_template_id`（B→A）体现。**首版不新建"系统"表**，前端把"来源系统"作为一个下拉选择框，选项直接来自 `channel_config.list`，"目标系统"则通过端口路由的目标 URL 主机名派生并允许人工命名。
- **"功能号"** 概念：现有 `port_route` 表**没有** `function_code` 字段。这是 FN-04 用户表述的核心线索，也是 5、6 两步"以功能号找模板"的检索键。**必须新增**。

#### 最小字段新增（DB 层）

| 表 | 新增字段 | 类型 | 说明 |
|----|---------|------|------|
| `port_route` | `function_code` | VARCHAR(64) | 功能号；同一渠道内应唯一（软唯一，代码层校验）；用于关联渠道 + 端口 + 模板三方 |
| `port_route` | `function_name` | VARCHAR(128) | 功能号中文名，便于阅读；可空 |
| `convert_template` | `function_code` | VARCHAR(64) | 模板绑定的功能号；可空（老模板兼容）；有值时向导 Step 5 按功能号过滤 |

> **说明**：三个字段全部可空，向导流程完整走完时会自动填充。既有 `port_route` / `convert_template` 记录 `function_code` 为 NULL 时，前端 CRUD 列表照常显示、编辑；只是它们不会出现在向导按功能号过滤后的选择结果里。

后端影响：
- 新增迁移脚本 `backend/src/main/resources/db/migration-ux-d-function-code.sql`（幂等，检测列是否存在再 ALTER）
- 实体 `PortRoute.java`、`ConvertTemplate.java` 加两个 String 字段（含 `function_code` 的 getter/setter）
- `PortRouteService.save`、`ConvertTemplateService.save` 保持零改动（MyBatis-Plus 自动映射）
- **不新增任何 Controller**；向导只调既有 API

---

## 4. FN-05 转换向导详细设计

### 4.1 WizardShell.vue 公共框架抽取

从 `InterfaceWizard.vue` 抽离出的 **纯骨架** 组件，不含任何业务字段。

**文件路径**：`frontend/src/components/wizard/WizardShell.vue`

#### Props

| prop | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `title` | String | ✓ | 页面标题（如 "接口配置向导" / "接口转换配置向导"） |
| `steps` | Array<StepDef> | ✓ | 步骤定义列表，每项 `{ key, label, tip, skipWhen?: Function }` |
| `currentStep` | Number | ✓ | 当前步骤索引（0-based），支持 `v-model` |
| `draftSaved` | Boolean | | 是否显示"草稿已自动保存" |
| `submitLabel` | String | | 最后一步的按钮文字，默认 "完成" |
| `validateNext` | Function | | `(currentStepKey) => boolean \| string`，返回 `false` 或错误字符串时阻止进入下一步 |

#### Slots

| slot | 说明 |
|------|------|
| `default`（作用域）：`{ currentKey, isActive }` | 由父组件根据 `currentKey` 决定渲染哪一步；`isActive(key)` 提供便捷方法用于 `v-show` |
| `header-right` | 页头右侧扩展区（默认放"返回列表"按钮） |
| `footer-extra` | 底部扩展区（例如"跳过本步"按钮） |

#### Events

| event | payload | 说明 |
|-------|---------|------|
| `update:currentStep` | newIndex | v-model 支持 |
| `submit` | — | 用户点击最后一步的"完成"按钮 |
| `back` | — | 用户点击"返回列表"按钮 |

#### 内部逻辑

- `visibleSteps` computed：过滤 `skipWhen(state)` 返回 true 的步骤
- `currentStepDef` computed：`visibleSteps[currentStep]`
- `isLastStep` computed
- 步骤条使用 `el-steps + el-step v-for`
- 底部导航栏：`← 上一步` / `步骤 N/M` / `下一步 →` 或 `完成`
- 步骤切换前调用 `validateNext(currentKey)`，返回字符串则 `ElMessage.warning(该字符串)`
- **保留 CHG-011 E2E-6 的经验**：Shell 只做骨架，不 assume 任何 `state.xxx.yyy` 的深层字段存在；深引用保护在业务视图组件内部通过 `v-if` 兜底

#### composable：useWizardShell

`frontend/src/composables/useWizardShell.js`

封装两个向导都需要的通用逻辑：
- `useDraft(storeName, storeInstance)`：注册 `watch(state, deep) → persist()`；提供 `promptRestoreDraft()`（返回 Promise，用户拒绝时执行 `store.reset()`）
- `useStepGuard(shellRef)`：提供 `nextStep()`、`prevStep()`、`skipStep()` 便捷方法（内部委托 Shell）

### 4.2 SelectInterfaceSteps.vue（原 10 步的迁移）

**文件路径**：`frontend/src/views/interface/SelectInterfaceSteps.vue`

**改造原则**：**完全等价搬迁**，不修改任何字段名、行为、校验规则。

- 现有 `InterfaceWizard.vue` 的 script 部分（`STEP_DEFS`、`buildPayload`、`onDbChange`、`rebuildSelectedColumns` 等所有函数）迁入本组件的 `<script setup>`
- 现有 template 中的 10 个 `<div v-show="isActive('xxx')">` 全部迁入本组件的 `default` slot
- `wizardStore` 继续使用现有 `frontend/src/store/wizard.js`（不改）
- **保留** `wizard.tables[0]?.tableName` 的 `?.` 保护 + `v-if="wizard.tables[0]"`（CHG-011 E2E-6 教训）

**新的 `frontend/src/views/interface/InterfaceWizard.vue`**（重构后）：

```vue
<template>
  <WizardShell
    v-model:current-step="wizard.currentStep"
    title="接口配置向导"
    :steps="visibleSteps"
    :draft-saved="draftSaved"
    :validate-next="validateStep"
    @submit="onSubmit"
    @back="goList"
  >
    <template #default="{ isActive }">
      <SelectInterfaceSteps :is-active="isActive" ref="stepsRef" />
    </template>
  </WizardShell>
</template>
```

**验收保护**：合并后立即在 pg-testkit 冒烟中跑一遍 SELECT/INSERT/UPDATE/DELETE 四类接口的全流程，验收标准同 SYS-5 原 6 条。

### 4.3 TransformInterfaceSteps.vue 7 步骤明细

**文件路径**：`frontend/src/views/convert/TransformInterfaceSteps.vue`

**入口路由**：`/convert/wizard`，对应 `frontend/src/views/convert/TransformWizard.vue`（Shell 容器）

#### 步骤总览

| # | 步骤 key | 标题 | 内容 | 关联既有单元 |
|---|---------|------|------|---------------|
| 1 | `system` | 选择系统 | 来源系统 + 目标系统 + 报文格式 | M1-4 渠道配置 |
| 2 | `function` | 定义功能号 | 功能号（英文）+ 中文名 + 报文类别 | 新增字段 |
| 3 | `port` | 端口配置 | 目标端口地址 + Method + 超时 + 重试 | M1-7 端口路由 |
| 4 | `route` | 端口路由绑定 | 渠道 + 端口 + 功能号 三方绑定 + Header 配置 | M1-7 |
| 5 | `template` | 转换模板 | 选/新建 转换模板（按功能号过滤，含字段映射+加工） | M1-2、M1-3、M1-5 |
| 6 | `test` | 报文格式转换测试 | 输入报文 → 调 `/api/dispatch` 端到端验证 | M1-1、M1-6 |
| 7 | `publish` | 发布 | 汇总回显 + 一键启用（保存路由 + 模板到正式态） | — |

#### Step 1 · 选择系统

**目的**：让用户明确"这次转换是谁给谁"，并把报文形态在最前面圈定。

**UI**：
- 「来源系统」`el-select`（可搜索、可创建）
  - 选项 = `channel_config.list()` 返回的所有渠道，`label = channelName · channelCode`
  - 底部"+ 新增渠道"链接，点击弹出小对话框（复用 `ChannelConfig.vue` 保存 API），输入 `channelCode` / `channelName` / `identifyField` 后即时刷新下拉
- 「目标系统」`el-select`（可搜索、可创建）—— 同上
- 「来源报文格式」`el-radio-group`：JSON / XML / CSV / FormData
- 「目标报文格式」`el-radio-group`：同上
- 「转换复杂度」`el-radio-group`（仅用于引导，不落库）：
  - `HEADER_ONLY`：只换报文头
  - `BODY_FIELDS`：换报文体字段
  - `FORMAT_AND_PROCESS`：换格式+字段+加工

**Store 字段**：`sourceChannelCode`、`targetChannelCode`、`sourceFormat`、`targetFormat`、`complexity`

**下一步启用**：来源系统、目标系统、来源格式、目标格式四项均非空。

#### Step 2 · 定义功能号

**目的**：给这次转换一个稳定业务标识，后续 3~7 步都以此为主线索。

**UI**：
- 「功能号」`el-input`（英文/数字/下划线，5~64 位，正则校验）
  - 提示："例如 CBS_QUERY_ACCOUNT、PAY_NOTIFY_SEND"
  - 校验：调 `GET /api/port-route/list?functionCode=xxx` 检查是否已存在（重名则给出黄色警告"该功能号已被使用，继续将覆盖既有路由"）
- 「功能号中文名」`el-input`（可选）
- 「报文类别」`el-select`：查询类 / 交易类 / 通知类 / 其他

**Store 字段**：`functionCode`、`functionName`、`messageCategory`

**下一步启用**：`functionCode` 非空且格式合法。

#### Step 3 · 端口配置

**目的**：确定目标系统的物理接入点。

**UI**：
- 「端口地址」`el-input`（完整 URL，含协议）—— 校验 http/https 前缀
- 「HTTP Method」`el-select`：GET / POST / PUT / DELETE
- 「超时（ms）」`el-input-number`（默认 3000）
- 「失败重试次数」`el-input-number`（默认 3）
- 「连通测试」`el-button`（可选）—— 点击后前端本地组一份临时 `PortRoute` payload，调 `POST /api/port-route/test-transient`（若不存在则接受"保存后再测"的降级方案：直接跳过按钮，标签写"发布后可测试连通"）

> **实现说明**：`/api/port-route/test-transient` 并不存在。**首版直接不提供 Step 3 的连通测试**，避免引入新 API；Step 4 保存路由后可在 Step 6 端到端测试中间接验证。

**Store 字段**：`portAddress`、`portMethod`、`timeout`、`retryCount`

**下一步启用**：`portAddress` 合法 URL 且 `portMethod` 非空。

#### Step 4 · 端口路由绑定

**目的**：把 Step 1（系统/渠道）+ Step 2（功能号）+ Step 3（端口）三者绑定成一条 `port_route` 记录，是"报文来龙去脉"的中枢。

**UI**：
- 只读回显：来源系统、目标系统、功能号、端口地址（灰底展示，避免用户误改）
- 「Header 配置」折叠区（复用现有 `PortRoute.vue` 的 Header 编辑子组件）
  - Content-Type：`el-select`（`application/json` / `application/xml` / `text/plain` / `application/x-www-form-urlencoded`）
  - Charset：`el-select`（UTF-8 / GBK / ISO-8859-1）
  - 自定义 Header KV 表格（可增删）
- 「渠道内识别字段」`el-input`（关联 `channel_config.identify_field`，用于运行时 ChannelRouter 匹配；已有则回填只读）

**动作**：
- 页面挂载时不立即保存
- 点击"下一步"时：
  - 若 `wizard.savedPortRouteId` 为空 → 调 `POST /api/port-route/save` 保存一条 `channel_code = sourceChannelCode`、`function_code`、`port_address`、`header_config` 完整的记录 → 回填 `savedPortRouteId`
  - 若已保存过 → 调 `POST /api/port-route/save` 带 id 更新
- 保存失败：`ElMessage.error` 提示，停留在当前步骤

**Store 字段**：`headerConfig`（对象）、`savedPortRouteId`

**下一步启用**：Header 配置合法（可为默认值） + 上一次保存成功

#### Step 5 · 转换模板选择/新增

**目的**：为这个功能号绑定报文转换规则（格式 + 字段映射 + 字段加工）。

**UI**：
- 顶部单选：「选择已有模板」 / 「新建模板」
- **分支 A · 选择已有模板**
  - `el-select`：选项来自 `GET /api/template/list?functionCode=xxx`（后端已有列表接口，前端在 `api/template.js` 中传参过滤）
  - 若无匹配 → 显示"暂无该功能号的模板，请切换到新建"提示
  - 选定后回显：`srcFmt → targetFmt`、映射规则条数、加工规则条数
- **分支 B · 新建模板**（本步骤内嵌简化配置，覆盖用户表述里"最简就是换报文头/换字段"的场景）
  - 「模板名」`el-input`（默认 `{functionCode}_TPL_{yyyymmdd}`）
  - 「字段映射」`el-table`（源字段名 → 目标字段名 → 加工规则）—— 加工规则下拉复用 M1-3 的类型枚举（TRIM/UPPER/LOWER/SUBSTRING/PAD）
  - 提交时调 `POST /api/template/save` 保存，回填 `savedTemplateId`
  - 若用户需要更复杂的映射（如条件映射、字段公式），底部提供"跳转到完整字段映射页配置 →"链接，打开新标签页保留向导草稿

**动作**：
- 保存/选定后立即调 `POST /api/port-route/save`（带 `savedPortRouteId`）更新 `request_template_id = savedTemplateId`
- 若来源、目标都是同格式（如 JSON→JSON）：仅需字段映射；否则强制要求模板的 `srcFmt`/`targetFmt` 与 Step 1 一致（不一致给红色错误）

**Store 字段**：`templateMode`（`EXISTING` / `NEW`）、`savedTemplateId`、`newTemplateDraft`

**下一步启用**：`savedTemplateId` 有值。

#### Step 6 · 报文格式转换测试

**目的**：让用户在向导内就把整条链路（渠道匹配 → 格式转换 → 字段映射 → 加工 → 目标端口）跑通，避免发布后才发现问题。

**UI**：
- 「输入报文」`el-input type="textarea"`（10 行）—— 提供"填充示例"按钮，从模板的 `mappingRule` 反推示例
- 「实际调用」/「模拟调用」`el-radio-group`
  - 实际调用：`POST /api/dispatch` 真的转发到目标端口
  - 模拟调用：`POST /api/convert`（M1-6）只做格式+映射+加工，不发 HTTP —— 首版默认用**模拟调用**，避免不小心打到生产系统
- 「执行」按钮 → 展示结果分栏（左：转换后报文；右：目标端口应答；底：耗时/错误信息）
- 「保存为测试用例」链接（可选，跳到 `MessageDebug.vue` 保留数据）

**Store 字段**：`testInput`、`testMode`（`SIMULATE` / `LIVE`）、`testOutput`、`testError`

**下一步启用**：至少执行过一次测试（不强制成功，用户可决定继续发布）；给"跳过测试直接发布"链接。

#### Step 7 · 发布

**目的**：汇总所有配置，把 `port_route` 状态从"临时保存"标记为"正式启用"。

**UI**：
- 汇总卡片：Step 1 ~ 6 各步的关键字段回显
- 「保存草稿」`el-button`（灰）：不改任何状态字段，草稿保留
- 「保存并启用」`el-button`（绿，主按钮）：
  - 无需额外 API —— `port_route` 表本身没有 `status` 字段，本单元的"启用"语义即"确保 `savedPortRouteId` 记录存在且完整"
  - 成功后：`useTransformWizardStore.reset()` + `router.push('/convert/port-route')`

**Store 字段**：无（读汇总）

**下一步**：无（最终步）。

### 4.4 每步数据存储

**Pinia store**：`frontend/src/store/transformWizard.js`，`localStorage` key = `transform_wizard_draft`

结构：

```js
{
  _skipNextPersist: false,
  currentStep: 0,

  // Step 1
  sourceChannelCode: '', sourceChannelName: '',
  targetChannelCode: '', targetChannelName: '',
  sourceFormat: 'JSON', targetFormat: 'JSON',
  complexity: 'BODY_FIELDS',

  // Step 2
  functionCode: '', functionName: '', messageCategory: '',

  // Step 3
  portAddress: '', portMethod: 'POST', timeout: 3000, retryCount: 3,

  // Step 4
  headerConfig: { contentType: 'application/json', charset: 'UTF-8', requestHeaders: {}, responseHeaders: {} },
  savedPortRouteId: null,

  // Step 5
  templateMode: 'EXISTING',
  savedTemplateId: null,
  newTemplateDraft: { name: '', srcFmt: '', targetFmt: '', mappingRules: [], processRules: [] },

  // Step 6
  testInput: '', testMode: 'SIMULATE', testOutput: '', testError: '',
}
```

- 持久化：与 SYS-5 `wizard.js` 完全一致的实现方式（`persist()` 排除 `testOutput` 等运行时字段）
- 首次进入 `/convert/wizard` 时若存在草稿 → 弹 `ElMessageBox.confirm("发现未完成的转换向导配置，是否恢复？")`
- 已保存过 `savedPortRouteId` 时草稿恢复后 Step 4~5 显示"已保存过，继续将更新既有记录"提示

### 4.5 每步校验规则与"下一步"按钮启用条件

| 步骤 | 前置校验（阻止前进） | 触发后动作 |
|------|----------------------|------------|
| Step 1 | 来源/目标渠道非空 · 来源/目标格式非空 | — |
| Step 2 | `functionCode` 5~64 位 `[A-Za-z0-9_]+` · 若已存在给警告不阻止 | — |
| Step 3 | `portAddress` 匹配 `^https?://` · `timeout > 0` · `retryCount ∈ [0, 10]` | — |
| Step 4 | Header 配置结构合法（allow 空表） | `POST /api/port-route/save` 保存/更新 |
| Step 5 | `savedTemplateId` 有值 · 分支 B 时先保存模板 | `POST /api/port-route/save` 更新 `request_template_id` |
| Step 6 | 至少执行过一次测试（`testOutput` 或 `testError` 非空） | — |
| Step 7 | — | 最终按钮：`reset()` + 跳转 `/convert/port-route` |

统一策略：所有前置校验通过 `WizardShell.validateNext(currentKey)` 返回字符串或 `false` 实现，字符串会被 `ElMessage.warning` 显示。

---

## 5. 前端文件变更清单

| 类型 | 路径 | 说明 |
|------|------|------|
| 新增 | `frontend/src/components/wizard/WizardShell.vue` | 公共向导骨架（约 180 行） |
| 新增 | `frontend/src/composables/useWizardShell.js` | 草稿保存 + 步骤守卫 composable |
| 新增 | `frontend/src/views/interface/SelectInterfaceSteps.vue` | 从 `InterfaceWizard.vue` 迁出的 10 步业务视图 |
| **改** | `frontend/src/views/interface/InterfaceWizard.vue` | 瘦身为 Shell + Steps 组合容器（约 30 行） |
| 新增 | `frontend/src/views/convert/TransformWizard.vue` | 转换向导 Shell 容器（约 30 行） |
| 新增 | `frontend/src/views/convert/TransformInterfaceSteps.vue` | 转换向导 7 步业务视图 |
| 新增 | `frontend/src/store/transformWizard.js` | 转换向导 Pinia store |
| 新增 | `frontend/src/api/functionCode.js` | 功能号存在性检查封装（内部调既有 port-route 列表 API） |
| **改** | `frontend/src/api/template.js` | `listTemplates()` 支持传 `functionCode` 参数 |
| **改** | `frontend/src/api/portRoute.js` | 若 `savePortRoute()` 尚未支持 `functionCode` 字段透传，补齐 |
| **改** | `frontend/src/router/index.js` | append `/convert/wizard` 路由（不影响既有条目） |
| **改** | `frontend/src/components/layout/SideMenu.vue` | "接口转换配置"子菜单顶部插入"接口转换配置向导" |
| **改** | `frontend/src/views/convert/PortRoute.vue` | 列表新增"功能号"列（可选展示 + 搜索）；编辑弹窗新增 `functionCode`/`functionName` 输入框 |
| **改** | `frontend/src/views/convert/TemplateList.vue` | 列表新增"功能号"列（可选展示）；编辑弹窗新增 `functionCode` 输入框 |

**vue-draggable-next 提醒**：本单元不使用 draggable，无需关注 `#item` 陷阱。

---

## 6. 后端影响

### 6.1 是否新增 API？

**不新增。** 所有向导操作复用既有 Controller：

| 向导动作 | 复用的既有 API |
|---------|----------------|
| 加载渠道下拉 | `GET /api/channel/list` |
| 新增渠道（Step 1 弹框） | `POST /api/channel/save` |
| 功能号重名检查 | `GET /api/port-route/list?functionCode=xxx` |
| Step 4 保存路由 | `POST /api/port-route/save` |
| Step 5 列已有模板 | `GET /api/template/list?functionCode=xxx` |
| Step 5 新建模板 | `POST /api/template/save` |
| Step 6 模拟测试 | `POST /api/convert`（M1-6） |
| Step 6 实际测试 | `POST /api/dispatch`（M1-7） |

### 6.2 数据库/实体改动

| 文件 | 改动 |
|------|------|
| `backend/src/main/resources/db/init.sql` | `port_route` 表 DDL 加 `function_code VARCHAR(64)` / `function_name VARCHAR(128)`；`convert_template` 表 DDL 加 `function_code VARCHAR(64)` |
| `backend/src/main/resources/db/migration-ux-d-function-code.sql`（**新增**） | 幂等 ALTER 脚本，通过存储过程检查列存在性 |
| `backend/src/main/java/com/powergateway/model/PortRoute.java` | 加两个字段 |
| `backend/src/main/java/com/powergateway/model/ConvertTemplate.java`（如类名不同，参考实际路径） | 加一个字段 |
| `backend/src/main/java/com/powergateway/service/PortRouteService.java` | `list()` 支持 `functionCode` 精确匹配（若尚未支持） |
| `backend/src/main/java/com/powergateway/service/ConvertTemplateService.java` | 同上 |

三个新字段 **不加索引**（数据量小 + 只在向导入口过滤，避免影响既有热点写入）。

### 6.3 兼容性

- 老 `port_route` / `convert_template` 记录 `function_code` 为 NULL —— 各自 CRUD 页正常显示、编辑
- 向导按功能号过滤时 NULL 记录不出现（用户期望：向导流是全新记录）
- MyBatis-Plus 下划线↔驼峰自动映射保证前后端字段一致

---

## 7. 路由与菜单变更

### 7.1 前端路由（`frontend/src/router/index.js`）

在"接口转换配置（模块一）"路由组末尾 append：

```js
{
  path: 'convert/wizard',
  name: 'TransformWizard',
  component: () => import('@/views/convert/TransformWizard.vue'),
  meta: { title: '接口转换配置向导' }
}
```

现有 `/interface/wizard` 条目**保持完全不变**。

### 7.2 菜单入口（`frontend/src/components/layout/SideMenu.vue`）

在"接口转换配置"子菜单最上方（`/convert/format` 之前）插入：

```html
<el-menu-item v-if="can('/convert/wizard')" index="/convert/wizard">接口转换配置向导</el-menu-item>
```

同时更新 `CONVERT_PATHS` 数组把 `/convert/wizard` 加进去，让 `hasConvert` computed 正确判定。

> **配合 UX-B**：UX-B 单元会重新调整"接口转换配置"子菜单的排序（NAV-02/03/04），本入口按 UX-B 定稿的分组归入"基础配置"小节顶部。若 UX-B 引入分组标签组件，向导入口所在分组标签为"新建"或"向导"。

### 7.3 后端菜单白名单（`MenuPermission.java`）

`ADMIN_MENUS` 与 `USER_MENUS` 两个列表均新增 `/convert/wizard`：

- ADMIN_MENUS：可访问全部转换配置，向导必开
- USER_MENUS：可访问全部转换配置（现状），向导必开
- READONLY_MENUS：不加（只读角色不能新建）

> **教训引用**：CHG-011 E2E-5 —— SYS-5 上线后 admin 也被路由守卫踢回 dashboard，原因就是 `MenuPermission.java` 白名单漏了 `/interface/wizard`。本次上线前**必须**执行"backend 重启 + admin/user 两角色实测访问 `/convert/wizard`"作为交付前置检查。

---

## 8. 测试用例

### 8.1 单元/组件测试

| ID | 用例 | 类型 |
|----|------|------|
| UX-D-U01 | `WizardShell` steps 空数组时不崩溃，渲染空步骤条 | 组件测试 |
| UX-D-U02 | `WizardShell` 传 `skipWhen` 返回 true 的步骤在步骤条中不渲染 | 组件测试 |
| UX-D-U03 | `WizardShell.validateNext` 返回字符串时 `ElMessage.warning` 被调用且步骤不前进 | 组件测试 |
| UX-D-U04 | `WizardShell` v-model `currentStep` 双向绑定生效 | 组件测试 |
| UX-D-U05 | `transformWizard` store `persist()` 后 `loadDraft()` 状态还原一致（除排除字段外） | Vitest |
| UX-D-U06 | `transformWizard` store `reset()` 后 localStorage 被清除且 state 回默认 | Vitest |

### 8.2 迁移回归测试（`SelectInterfaceSteps` 抽出后）

| ID | 用例 | 期望 |
|----|------|------|
| UX-D-R01 | 访问 `/interface/wizard`，SELECT 类型走完 10 步，发布后 `/api/exec/{id}` 成功 | 与 SYS-5 验收标准 1 完全一致 |
| UX-D-R02 | INSERT / UPDATE / DELETE 各走一遍完整流程 | 与 SYS-5 验收标准 2、3、4 一致 |
| UX-D-R03 | 中途关闭浏览器再访问，弹恢复草稿提示 | 与 SYS-5 验收标准 5 一致 |
| UX-D-R04 | `wizard.tables[0]?.tableName` 在初始态不崩溃（CHG-011 E2E-6 回归） | 页面进入不抛错 |

### 8.3 转换向导端到端测试

| ID | 用例 | 期望 |
|----|------|------|
| UX-D-E01 | 走完 7 步，功能号 `E2E_UXD_01`、JSON→JSON、简单字段映射 | Step 4 生成 `port_route` 记录含 `function_code=E2E_UXD_01`；Step 6 模拟测试返回预期报文；Step 7 跳转到端口路由列表可见新记录 |
| UX-D-E02 | Step 1 新增渠道 → 立即在下拉里出现 | 无需刷新页面 |
| UX-D-E03 | Step 2 输入已存在功能号 → 显示黄色警告 | 不阻止前进 |
| UX-D-E04 | Step 5 选择既有模板，模板绑定的 `function_code` 与向导不一致 | Step 5 列表按功能号过滤后不出现该模板 |
| UX-D-E05 | Step 6 实际调用（LIVE 模式）目标端口不可达 | `testError` 显示错误，允许用户点"跳过测试直接发布" |
| UX-D-E06 | Step 4 保存路由后回退到 Step 3 改端口地址 → Step 4 再前进 | 用 `savedPortRouteId` 走 UPDATE 路径，`port_route` 记录被更新而非新增 |
| UX-D-E07 | 走到 Step 5 后关浏览器 → 重新打开 → 选"恢复" → 从 Step 5 继续 | 状态完整还原 |

### 8.4 权限/路由测试

| ID | 用例 | 期望 |
|----|------|------|
| UX-D-P01 | admin 登录后访问 `/convert/wizard` | 页面正常渲染 |
| UX-D-P02 | user 登录后访问 `/convert/wizard` | 页面正常渲染 |
| UX-D-P03 | readonly 登录后访问 `/convert/wizard` | 路由守卫踢回 `/dashboard` |
| UX-D-P04 | 侧边菜单在三个角色下的显示/隐藏正确 | admin/user 有"接口转换配置向导"菜单项；readonly 无 |

---

## 9. 验收标准

1. **公共 Shell 抽取干净**：`WizardShell.vue` 不含任何"接口/转换"业务字段；`InterfaceWizard.vue` 瘦身后 ≤ 60 行
2. **原 10 步向导零退化**：SELECT/INSERT/UPDATE/DELETE 四类接口的所有 SYS-5 验收用例（1~7）全绿；CHG-011 E2E-6 空引用场景仍不崩溃
3. **转换向导 7 步全通**：能从空白配置状态一路走到"发布"并在 `/convert/port-route` 列表看到新记录，`function_code` 正确落库
4. **草稿保存生效**：中途关浏览器再打开，两个向导都能恢复到中断步骤
5. **API 无新增**：向导所有交互都走既有 8 个 Controller 端点
6. **数据库字段最小新增**：只加 3 列（`port_route.function_code` / `port_route.function_name` / `convert_template.function_code`），无索引
7. **菜单权限就绪**：admin/user 能在 `/convert/wizard` 页面正常操作；readonly 被守卫拦截
8. **文档 3 步齐**：
   - `docs/03-开发/变更记录.md` 追加 `CHG-019 UX-D 接口转换向导` 条目
   - `docs/01-需求/需求拆分与最小实现方案.md` 阶段六 UX-D 单元描述更新
   - `docs/03-开发/开发计划.md` UX-D 行填交付时间与状态

---

## 10. 实施顺序建议

推荐分成 4 个 PR 合入，每个 PR 独立可回滚：

### PR-1（约 2 天）：抽 Shell + 迁移原向导

1. 新建 `WizardShell.vue` + `useWizardShell.js`
2. 新建 `SelectInterfaceSteps.vue`（完全从现有 `InterfaceWizard.vue` 拷贝迁入）
3. `InterfaceWizard.vue` 重构为容器组合
4. 冒烟：pg-testkit 跑 SELECT/INSERT/UPDATE/DELETE 全流程

### PR-2（约 1 天）：后端字段与迁移脚本

1. 新增 `migration-ux-d-function-code.sql`（幂等）
2. `init.sql` 更新 `port_route` / `convert_template` DDL
3. 实体类加字段
4. `PortRouteService.list` / `ConvertTemplateService.list` 支持 `functionCode` 过滤参数
5. `MenuPermission.java` 加 `/convert/wizard` 到 ADMIN/USER 白名单
6. 补 Controller 层单元测试（`?functionCode=xxx` 过滤生效）

### PR-3（约 3 天）：转换向导 7 步实现

1. 新建 `transformWizard.js` store
2. 新建 `TransformInterfaceSteps.vue`（Step 1~7 视图）
3. 新建 `TransformWizard.vue` 容器
4. `router/index.js` append 新路由
5. `SideMenu.vue` 加菜单入口 + `CONVERT_PATHS` 数组更新
6. `PortRoute.vue` / `TemplateList.vue` 编辑弹窗补 `functionCode` 输入框
7. Vitest 组件测试 + 手工走完 7 步冒烟

### PR-4（约 1 天）：E2E 验收 + 文档

1. pg-testkit 编写 UX-D-E01 ~ E07 自动化脚本（若受限则手工走）
2. 更新 `变更记录.md` / `需求拆分与最小实现方案.md` / `开发计划.md`
3. 三角色（admin/user/readonly）实测菜单显示与页面访问权限
4. Merge 前跑一次全链路冒烟：登录 → 主题切 → 字段公式 → **转换向导（本单元）** → 可视化接口 exec

**总工期**：约 7 人日。

---

## 11. 不含内容

- 不实现"编辑既有转换配置"入口（只支持新建）
- 不引入独立"系统"表（用渠道概念承担）
- 不实现 Step 3 独立的连通测试 API（用 Step 6 端到端测试代替）
- 不改 M1-1 ~ M1-7 任何 Controller/Service 行为
- 不做转换接口的"发布状态机"（`port_route` 无 status 字段，本单元不引入）
- 不做向导内的"字段公式"高级配置（延伸阅读跳到 UX-C 的字段公式页）
