# SYS-5 接口配置9步向导 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增统一的10步接口配置向导 `InterfaceWizard.vue`，支持 SELECT/INSERT/UPDATE/DELETE 四种接口类型，向导与现有各类型配置页并存。

**Architecture:** 单文件向导组件 `InterfaceWizard.vue` 包含所有10步模板与逻辑；`wizardStore`（Pinia）管理跨步骤状态并自动持久化到 `localStorage`；`computedSteps` 根据接口类型过滤不适用步骤（DELETE 跳过步骤④⑥，INSERT 跳过步骤⑦）。不新增任何后端接口，不修改现有4个 Config 页。

**Tech Stack:** Vue 3 `<script setup>`、Element Plus（el-steps/el-table/el-select/el-switch/el-tooltip/ElMessageBox）、Pinia、Vue Router

---

## 文件清单

| 操作 | 文件 | 说明 |
|------|------|------|
| 创建 | `frontend/src/store/wizard.js` | Pinia store，管理向导全局状态 |
| 创建 | `frontend/src/views/interface/InterfaceWizard.vue` | 向导主组件 |
| 修改 | `frontend/src/router/index.js` | 新增 `/interface/wizard` 路由 |
| 修改 | `frontend/src/components/layout/SideMenu.vue` | 新增菜单项 + 更新 INTERFACE_PATHS |
| 修改 | `frontend/src/views/interface/InterfaceList.vue` | 新增「向导新建」按钮 |

---

## Task 1: wizardStore.js

**文件：**
- 创建: `frontend/src/store/wizard.js`

- [ ] **步骤1：创建 store 文件**

```js
// frontend/src/store/wizard.js
import { defineStore } from 'pinia'

const DRAFT_KEY = 'wizard_draft'

function defaultState() {
  return {
    currentStep: 0,
    interfaceType: '',

    // 步骤②
    interfaceName: '',
    dbConnectionId: null,
    tableColumns: {},       // { tableName: [{ name, type, isPrimary, nullable }] }

    // 步骤③ SELECT
    mainTable: { name: '', alias: '' },
    joinConfigs: [],        // [{ rightTableName, rightAlias, type, leftCol, rightCol }]

    // 步骤③ INSERT/UPDATE/DELETE
    tables: [],             // [{ tableName }]，INSERT 最多3张

    // 步骤④ SELECT
    selectedColumns: [],    // [{ tableAlias, name, type, selected, alias }]

    // 步骤④ INSERT/UPDATE
    fieldTables: [],        // [{ tableName, fields: [{ column, columnType, sourceType, paramKey, constValue, expression }] }]

    // 步骤⑤
    shardConfigId: null,

    // 步骤⑥
    processRules: [],

    // 步骤⑦
    conditions: [],

    // 步骤⑧
    logEnabled: true,

    // 步骤⑨
    previewParams: {},
    previewResult: [],

    // 步骤⑩
    savedId: null,
  }
}

export const useWizardStore = defineStore('wizard', {
  state: defaultState,
  actions: {
    reset() {
      this.$patch(defaultState())
      localStorage.removeItem(DRAFT_KEY)
    },
    persist() {
      try {
        const { previewResult, tableColumns, ...rest } = this.$state
        localStorage.setItem(DRAFT_KEY, JSON.stringify(rest))
      } catch {}
    },
    hasDraft() {
      return !!localStorage.getItem(DRAFT_KEY)
    },
    loadDraft() {
      try {
        const raw = localStorage.getItem(DRAFT_KEY)
        if (!raw) return false
        this.$patch(JSON.parse(raw))
        return true
      } catch {
        return false
      }
    },
  },
})
```

- [ ] **步骤2：验证 store 可正常导入**

在 `frontend/` 目录下运行：
```bash
npm run dev
```
打开浏览器控制台，无报错即可。按 Ctrl+C 停止。

- [ ] **步骤3：提交**

```bash
git add frontend/src/store/wizard.js
git commit -m "feat(SYS-5): add wizardStore with state/persist/draft"
```

---

## Task 2: 路由 + 菜单 + InterfaceList 入口

**文件：**
- 修改: `frontend/src/router/index.js`
- 修改: `frontend/src/components/layout/SideMenu.vue`
- 修改: `frontend/src/views/interface/InterfaceList.vue`

- [ ] **步骤1：在 router/index.js 中新增路由**

找到以下代码块（约第78行）：
```js
        {
          path: 'interface/dev',
```

在其**上方**插入：
```js
        {
          path: 'interface/wizard',
          name: 'InterfaceWizard',
          component: () => import('@/views/interface/InterfaceWizard.vue'),
          meta: { title: '接口配置向导' }
        },
```

- [ ] **步骤2：在 SideMenu.vue 中新增菜单项和路径**

找到 `INTERFACE_PATHS` 数组（约第103行）：
```js
var INTERFACE_PATHS = ['/interface/db', '/interface/table', '/interface/dev',
                       '/interface/insert', '/interface/update', '/interface/delete',
                       '/interface/list', '/interface/shard', '/interface/formula', '/interface/cache']
```

替换为：
```js
var INTERFACE_PATHS = ['/interface/wizard', '/interface/db', '/interface/table', '/interface/dev',
                       '/interface/insert', '/interface/update', '/interface/delete',
                       '/interface/list', '/interface/shard', '/interface/formula', '/interface/cache']
```

再找到以下菜单项（约第45行）：
```html
        <el-menu-item v-if="can('/interface/db')" index="/interface/db">数据库连接管理</el-menu-item>
```

在其**上方**插入：
```html
        <el-menu-item v-if="can('/interface/wizard')" index="/interface/wizard">接口配置向导</el-menu-item>
```

- [ ] **步骤3：在 InterfaceList.vue 中新增「向导新建」按钮**

找到 InterfaceList.vue 中 toolbar div 的 `el-button type="primary"` 查询按钮（约第12行），在其**后面**追加：

```html
      <el-button type="success" @click="goWizard">向导新建</el-button>
```

在 `<script setup>` 中导入 wizardStore 并添加 `goWizard` 函数。找到现有的 `import` 和 `router` 引用，新增：

```js
import { useWizardStore } from '@/store/wizard'
const wizardStore = useWizardStore()

function goWizard() {
  wizardStore.reset()
  router.push('/interface/wizard')
}
```

- [ ] **步骤4：创建空占位文件让路由不报错**

在创建 InterfaceWizard.vue 之前，先创建一个最小占位文件：

```vue
<!-- frontend/src/views/interface/InterfaceWizard.vue -->
<template>
  <div style="padding: 20px">接口配置向导（开发中）</div>
</template>
<script setup></script>
```

- [ ] **步骤5：启动开发服务器验证**

```bash
npm run dev
```

在浏览器中：
1. 登录后，左侧"可视化接口开发"菜单下应出现"接口配置向导"
2. 点击该菜单项，页面显示"接口配置向导（开发中）"
3. 进入"接口管理"页，应看到"向导新建"绿色按钮
4. 点击"向导新建"应跳转到 `/interface/wizard`

按 Ctrl+C 停止。

- [ ] **步骤6：提交**

```bash
git add frontend/src/router/index.js frontend/src/components/layout/SideMenu.vue frontend/src/views/interface/InterfaceList.vue frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): add wizard route, menu item, InterfaceList entry"
```

---

## Task 3: InterfaceWizard.vue 骨架（步骤条 + 导航 + 草稿恢复）

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：用完整骨架替换占位文件**

```vue
<template>
  <div class="wizard-page">
    <!-- 页头 -->
    <div class="wizard-header">
      <span class="wizard-title">接口配置向导</span>
      <div class="wizard-header-right">
        <span class="draft-hint" v-if="draftSaved">草稿已自动保存</span>
        <el-button size="small" @click="goList">返回列表</el-button>
      </div>
    </div>

    <!-- 步骤条 -->
    <el-steps
      :active="wizard.currentStep"
      finish-status="success"
      align-center
      class="wizard-steps"
    >
      <el-step v-for="s in visibleSteps" :key="s.key" :title="s.label" />
    </el-steps>

    <!-- 内容区 -->
    <el-card class="wizard-content">
      <template #header>
        <div class="step-header">
          <span>Step {{ wizard.currentStep + 1 }} · {{ currentStepDef.label }}</span>
          <el-tooltip :content="currentStepDef.tip" placement="left">
            <span class="help-btn">?</span>
          </el-tooltip>
        </div>
      </template>

      <!-- 占位：各步骤内容将在后续 Task 中填充 -->
      <div v-for="s in visibleSteps" :key="s.key">
        <div v-show="isActive(s.key)">
          <p style="color:#909399">步骤 {{ s.label }} 内容开发中</p>
        </div>
      </div>
    </el-card>

    <!-- 底部导航 -->
    <div class="wizard-footer">
      <el-button :disabled="wizard.currentStep === 0" @click="prevStep">← 上一步</el-button>
      <span class="step-counter">步骤 {{ wizard.currentStep + 1 }} / {{ visibleSteps.length }}</span>
      <el-button
        v-if="!isLastStep"
        type="primary"
        @click="nextStep"
      >
        下一步 →
      </el-button>
      <el-button
        v-else
        type="success"
        @click="nextStep"
      >
        完成
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useWizardStore } from '@/store/wizard'

const router = useRouter()
const wizard = useWizardStore()
const draftSaved = ref(false)

// ─── 步骤定义 ──────────────────────────────────────────────────────────────
const STEP_DEFS = [
  { key: 'type',    label: '接口类型',  skipFor: [],         tip: '选择要创建的接口类型：查询(SELECT)/插入(INSERT)/修改(UPDATE)/删除(DELETE)' },
  { key: 'db',      label: '数据库连接', skipFor: [],         tip: '选择目标数据库连接，并填写接口名称' },
  { key: 'tables',  label: '选表结构',  skipFor: [],         tip: 'SELECT 选主表和关联表；INSERT 选目标表（最多3张）；UPDATE/DELETE 选单表' },
  { key: 'fields',  label: '字段配置',  skipFor: ['DELETE'], tip: 'SELECT 勾选返回字段；INSERT/UPDATE 配置每字段的数据来源' },
  { key: 'shard',   label: '分库分表',  skipFor: [],         tip: '可选：选择分库分表规则，不需要可跳过' },
  { key: 'process', label: '字段加工',  skipFor: ['DELETE'], tip: '可选：配置字段加工规则（截位/补位/大小写等），不需要可跳过' },
  { key: 'cond',    label: '条件配置',  skipFor: ['INSERT'], tip: 'SELECT 可选条件；UPDATE/DELETE 必须配置至少一个含主键的条件' },
  { key: 'log',     label: '日志开关',  skipFor: [],         tip: '是否记录该接口的操作日志（审计用），默认开启' },
  { key: 'preview', label: '预览测试',  skipFor: [],         tip: '自动保存配置并执行预览查询，验证接口逻辑正确' },
  { key: 'publish', label: '保存发布',  skipFor: [],         tip: '填写接口名称后可仅保存草稿或立即发布' },
]

const visibleSteps = computed(() =>
  STEP_DEFS.filter(s => !wizard.interfaceType || !s.skipFor.includes(wizard.interfaceType))
)

const currentStepDef = computed(() =>
  visibleSteps.value[wizard.currentStep] ?? STEP_DEFS[0]
)

const isLastStep = computed(() =>
  wizard.currentStep === visibleSteps.value.length - 1
)

function isActive(key) {
  return currentStepDef.value.key === key
}

// ─── 草稿持久化 ───────────────────────────────────────────────────────────
watch(
  () => wizard.$state,
  () => {
    wizard.persist()
    draftSaved.value = true
  },
  { deep: true }
)

// ─── 初始化：恢复草稿 ─────────────────────────────────────────────────────
onMounted(async () => {
  if (wizard.hasDraft() && wizard.interfaceType === '') {
    try {
      await ElMessageBox.confirm(
        '发现未完成的向导配置，是否恢复？',
        '恢复草稿',
        { confirmButtonText: '恢复', cancelButtonText: '重新开始', type: 'info' }
      )
      wizard.loadDraft()
    } catch {
      wizard.reset()
    }
  }
})

// ─── 步骤导航 ─────────────────────────────────────────────────────────────
function prevStep() {
  if (wizard.currentStep > 0) wizard.currentStep--
}

function nextStep() {
  wizard.currentStep++
}

function goList() {
  router.push('/interface/list')
}
</script>

<style scoped>
.wizard-page {
  padding: 20px;
}
.wizard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.wizard-title {
  font-size: 18px;
  font-weight: 600;
}
.wizard-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.draft-hint {
  font-size: 12px;
  color: #67C23A;
}
.wizard-steps {
  margin-bottom: 20px;
}
.wizard-content {
  min-height: 300px;
  margin-bottom: 20px;
}
.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.help-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f0f2f5;
  border: 1px solid #ddd;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
}
.wizard-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}
.step-counter {
  font-size: 13px;
  color: #909399;
}
</style>
```

- [ ] **步骤2：启动开发服务器验证**

```bash
npm run dev
```

在浏览器访问 http://localhost:5173/interface/wizard：
1. 页面显示"接口配置向导"标题
2. 顶部步骤条显示（此时 interfaceType 为空，显示所有10步）
3. 内容区显示"步骤 XX 内容开发中"
4. 底部有上/下一步按钮，点击可切换步骤
5. 到最后一步时，按钮变为"完成"

按 Ctrl+C 停止。

- [ ] **步骤3：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): add wizard shell with step bar, navigation, draft restore"
```

---

## Task 4: 步骤①② — 接口类型 + 数据库连接

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：在 script setup 中增加步骤①②需要的数据和逻辑**

在 `import` 区域追加：
```js
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
```

在 `wizard` 定义后追加：
```js
// ─── 远程数据 ─────────────────────────────────────────────────────────────
const dbList = ref([])

onMounted(async () => {
  // ...（现有草稿恢复代码）...
  try {
    dbList.value = await listConnections() || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
})
```

追加 DB 切换函数：
```js
async function onDbChange(dbId) {
  wizard.tableColumns = {}
  wizard.mainTable = { name: '', alias: '' }
  wizard.joinConfigs = []
  wizard.tables = []
  wizard.selectedColumns = []
  wizard.fieldTables = []
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId) || []
    const map = {}
    for (const t of list) map[t.tableName] = t.columns || []
    wizard.tableColumns = map
  } catch {
    ElMessage.error('加载表结构失败')
  }
}
```

- [ ] **步骤2：填充步骤① — 接口类型内容**

找到模板中 `v-for="s in visibleSteps"` 的 `<div v-show="isActive(s.key)">` 占位块，替换为对每个 key 的具体实现。将模板中的步骤内容区改为：

```html
    <!-- ① 接口类型 -->
    <div v-show="isActive('type')">
      <el-radio-group
        v-model="wizard.interfaceType"
        size="large"
        style="display:flex;gap:20px;flex-wrap:wrap"
        @change="() => { wizard.currentStep = 0; wizard.interfaceType = wizard.interfaceType }"
      >
        <el-radio-button value="SELECT">
          <div style="text-align:center;padding:8px 16px">
            <div style="font-size:16px;font-weight:600">SELECT</div>
            <div style="font-size:12px;color:#909399">查询接口</div>
          </div>
        </el-radio-button>
        <el-radio-button value="INSERT">
          <div style="text-align:center;padding:8px 16px">
            <div style="font-size:16px;font-weight:600">INSERT</div>
            <div style="font-size:12px;color:#909399">插入接口</div>
          </div>
        </el-radio-button>
        <el-radio-button value="UPDATE">
          <div style="text-align:center;padding:8px 16px">
            <div style="font-size:16px;font-weight:600">UPDATE</div>
            <div style="font-size:12px;color:#909399">修改接口</div>
          </div>
        </el-radio-button>
        <el-radio-button value="DELETE">
          <div style="text-align:center;padding:8px 16px">
            <div style="font-size:16px;font-weight:600">DELETE</div>
            <div style="font-size:12px;color:#909399">删除接口</div>
          </div>
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- ② 数据库连接 -->
    <div v-show="isActive('db')">
      <el-form label-width="120px" style="max-width:600px">
        <el-form-item label="接口名称" required>
          <el-input v-model="wizard.interfaceName" placeholder="请输入接口名称" />
        </el-form-item>
        <el-form-item label="数据库连接" required>
          <el-select
            v-model="wizard.dbConnectionId"
            placeholder="请选择数据库连接"
            style="width:100%"
            @change="onDbChange"
          >
            <el-option v-for="db in dbList" :key="db.id" :label="db.name" :value="db.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- 其余步骤占位 -->
    <div v-show="isActive('tables')"><p style="color:#909399">步骤③内容开发中</p></div>
    <div v-show="isActive('fields')"><p style="color:#909399">步骤④内容开发中</p></div>
    <div v-show="isActive('shard')"><p style="color:#909399">步骤⑤内容开发中</p></div>
    <div v-show="isActive('process')"><p style="color:#909399">步骤⑥内容开发中</p></div>
    <div v-show="isActive('cond')"><p style="color:#909399">步骤⑦内容开发中</p></div>
    <div v-show="isActive('log')"><p style="color:#909399">步骤⑧内容开发中</p></div>
    <div v-show="isActive('preview')"><p style="color:#909399">步骤⑨内容开发中</p></div>
    <div v-show="isActive('publish')"><p style="color:#909399">步骤⑩内容开发中</p></div>
```

**注意：** 步骤①的 `@change` 需要在切换类型时重置 `currentStep=0` 以防越界（因 visibleSteps 长度变化）。将 `@change` 改为：
```js
@change="onTypeChange"
```
并添加函数：
```js
function onTypeChange() {
  wizard.currentStep = 0
  // 重置步骤③之后的数据
  wizard.mainTable = { name: '', alias: '' }
  wizard.joinConfigs = []
  wizard.tables = []
  wizard.selectedColumns = []
  wizard.fieldTables = []
  wizard.conditions = []
  wizard.shardConfigId = null
  wizard.processRules = []
}
```

- [ ] **步骤3：验证**

```bash
npm run dev
```

1. 访问 `/interface/wizard`，步骤①显示4个类型卡片
2. 选择 SELECT → 步骤条显示10步；选择 DELETE → 步骤条显示8步（无步骤④⑥）
3. 点击"下一步"到步骤②，显示接口名称输入框和数据库连接下拉
4. 选择已配置的数据库连接，控制台无报错

- [ ] **步骤4：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard steps 1-2 (type selection, DB connection)"
```

---

## Task 5: 步骤③ — 选表结构

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：在 script setup 中添加表操作函数**

```js
// ─── 步骤③ 辅助函数 ───────────────────────────────────────────────────────

/** SELECT：主表变更 → 重建 selectedColumns */
function onMainTableChange(tableName) {
  wizard.mainTable.alias = tableName ? tableName[0].toLowerCase() : ''
  rebuildSelectedColumns()
}

function onJoinTableChange(idx, tableName) {
  wizard.joinConfigs[idx].rightAlias = tableName ? tableName[0].toLowerCase() + idx : ''
  rebuildSelectedColumns()
}

function rebuildSelectedColumns() {
  const prev = wizard.selectedColumns
  const newCols = []
  const addTable = (tableName, alias) => {
    for (const col of (wizard.tableColumns[tableName] || [])) {
      const existing = prev.find(p => p.tableAlias === alias && p.name === col.name)
      newCols.push({
        tableAlias: alias,
        name: col.name,
        type: col.type,
        selected: existing ? existing.selected : true,
        alias: existing ? existing.alias : col.name
      })
    }
  }
  if (wizard.mainTable.name && wizard.mainTable.alias) {
    addTable(wizard.mainTable.name, wizard.mainTable.alias)
  }
  for (const j of wizard.joinConfigs) {
    if (j.rightTableName && j.rightAlias) addTable(j.rightTableName, j.rightAlias)
  }
  wizard.selectedColumns = newCols
}

function addJoin() {
  wizard.joinConfigs.push({ rightTableName: '', rightAlias: '', type: 'LEFT', leftCol: '', rightCol: '' })
}

function removeJoin(idx) {
  wizard.joinConfigs.splice(idx, 1)
  rebuildSelectedColumns()
}

/** INSERT/UPDATE/DELETE：目标表变更 → 重建 fieldTables */
function onTargetTableChange(idx, tableName) {
  if (wizard.interfaceType === 'INSERT' || wizard.interfaceType === 'UPDATE') {
    const cols = wizard.tableColumns[tableName] || []
    // 确保 fieldTables[idx] 存在
    while (wizard.fieldTables.length <= idx) {
      wizard.fieldTables.push({ tableName: '', fields: [] })
    }
    wizard.fieldTables[idx].tableName = tableName
    wizard.fieldTables[idx].fields = cols.map(col => ({
      column: col.name,
      columnType: col.type,
      sourceType: 'REQUEST',
      paramKey: col.name,
      constValue: '',
      expression: ''
    }))
  }
}

function addTargetTable() {
  if (wizard.tables.length < 3) {
    wizard.tables.push({ tableName: '' })
    wizard.fieldTables.push({ tableName: '', fields: [] })
  }
}

function removeTargetTable(idx) {
  wizard.tables.splice(idx, 1)
  wizard.fieldTables.splice(idx, 1)
}

/** 表名列表（来自 tableColumns key） */
const tableList = computed(() => Object.keys(wizard.tableColumns).map(name => ({ tableName: name })))
```

- [ ] **步骤2：替换步骤③占位内容**

将模板中 `<div v-show="isActive('tables')">` 替换为：

```html
    <!-- ③ 选表结构 -->
    <div v-show="isActive('tables')">
      <!-- SELECT：主表 + JOIN -->
      <template v-if="wizard.interfaceType === 'SELECT'">
        <el-form label-width="80px">
          <el-form-item label="主表" required>
            <el-select
              v-model="wizard.mainTable.name"
              placeholder="请选择主表"
              filterable
              style="width:220px"
              @change="onMainTableChange"
            >
              <el-option
                v-for="t in tableList"
                :key="t.tableName"
                :label="t.tableName"
                :value="t.tableName"
              />
            </el-select>
            <span style="margin:0 8px;color:#606266">别名</span>
            <el-input v-model="wizard.mainTable.alias" style="width:80px" placeholder="如 a" />
          </el-form-item>

          <el-form-item label="关联表">
            <div v-for="(join, idx) in wizard.joinConfigs" :key="idx" class="join-row">
              <el-select
                v-model="join.rightTableName"
                placeholder="关联表"
                filterable
                style="width:160px"
                @change="v => onJoinTableChange(idx, v)"
              >
                <el-option v-for="t in tableList" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
              </el-select>
              <span style="margin:0 6px;color:#606266">别名</span>
              <el-input v-model="join.rightAlias" style="width:70px" placeholder="如 b" />
              <el-select v-model="join.type" style="width:110px;margin:0 6px">
                <el-option label="LEFT JOIN" value="LEFT" />
                <el-option label="INNER JOIN" value="INNER" />
                <el-option label="RIGHT JOIN" value="RIGHT" />
              </el-select>
              <span style="color:#606266">ON</span>
              <el-select v-model="join.leftCol" placeholder="左表字段" filterable style="width:140px;margin:0 6px">
                <el-option
                  v-for="col in (wizard.tableColumns[wizard.mainTable.name] || [])"
                  :key="col.name"
                  :label="wizard.mainTable.alias + '.' + col.name"
                  :value="col.name"
                />
              </el-select>
              <span>=</span>
              <el-select v-model="join.rightCol" placeholder="右表字段" filterable style="width:140px;margin:0 6px">
                <el-option
                  v-for="col in (wizard.tableColumns[join.rightTableName] || [])"
                  :key="col.name"
                  :label="join.rightAlias + '.' + col.name"
                  :value="col.name"
                />
              </el-select>
              <el-button type="danger" link @click="removeJoin(idx)">删除</el-button>
            </div>
            <el-button plain size="small" @click="addJoin">+ 添加关联表</el-button>
          </el-form-item>
        </el-form>
      </template>

      <!-- INSERT：多目标表（最多3张） -->
      <template v-else-if="wizard.interfaceType === 'INSERT'">
        <div v-for="(tbl, idx) in wizard.tables" :key="idx" class="table-block">
          <div style="display:flex;align-items:center;margin-bottom:8px">
            <span style="font-weight:600;margin-right:12px">表 {{ idx + 1 }}</span>
            <el-select
              v-model="tbl.tableName"
              placeholder="请选择目标表"
              filterable
              style="width:260px"
              @change="v => onTargetTableChange(idx, v)"
            >
              <el-option v-for="t in tableList" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
            </el-select>
            <el-button
              v-if="wizard.tables.length > 1"
              type="danger"
              plain
              size="small"
              style="margin-left:8px"
              @click="removeTargetTable(idx)"
            >删除</el-button>
          </div>
        </div>
        <el-button
          :disabled="wizard.tables.length >= 3"
          size="small"
          @click="addTargetTable"
        >+ 添加表（最多3张）</el-button>
      </template>

      <!-- UPDATE/DELETE：单目标表 -->
      <template v-else>
        <el-form label-width="100px" style="max-width:500px">
          <el-form-item label="目标表" required>
            <el-select
              v-model="wizard.tables[0].tableName"
              placeholder="请选择目标表"
              filterable
              style="width:260px"
              @change="v => onTargetTableChange(0, v)"
            >
              <el-option v-for="t in tableList" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
    </div>
```

同时在 `onMounted` 的 `wizard.reset()` 后添加初始化：
```js
// 确保 UPDATE/DELETE 的 tables[0] 存在
if ((wizard.interfaceType === 'UPDATE' || wizard.interfaceType === 'DELETE') && wizard.tables.length === 0) {
  wizard.tables = [{ tableName: '' }]
}
```

并在 `onTypeChange` 中对 UPDATE/DELETE 初始化 tables：
```js
// 在 onTypeChange 函数末尾追加
if (type === 'DELETE') {
  wizard.tables = [{ tableName: '' }]
}
if (type === 'INSERT' || type === 'UPDATE') {
  wizard.tables = [{ tableName: '' }]
  wizard.fieldTables = [{ tableName: '', fields: [] }]
}
```

> **注意：** `onTypeChange` 参数需接收 `type`，将函数签名改为 `function onTypeChange(type)` 并将 `@change="onTypeChange"` 保持不变（Element Plus radio-button change 传入新值）。

- [ ] **步骤3：验证**

```bash
npm run dev
```

1. 选 SELECT → 步骤③ 显示主表选择 + 关联表配置
2. 选已有数据库 → 步骤③ 主表下拉有数据，选中后别名自动填充
3. 添加关联表，JOIN 配置正常
4. 选 INSERT → 步骤③ 显示目标表选择，点"添加表"可添加到3张
5. 选 DELETE → 步骤③ 显示单目标表选择

- [ ] **步骤4：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard step 3 (table selection, type-aware)"
```

---

## Task 6: 步骤④ — 字段配置

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：替换步骤④占位内容**

将 `<div v-show="isActive('fields')">` 替换为：

```html
    <!-- ④ 字段配置（DELETE 不显示此步） -->
    <div v-show="isActive('fields')">
      <!-- SELECT：勾选返回字段 -->
      <template v-if="wizard.interfaceType === 'SELECT'">
        <el-table :data="wizard.selectedColumns" border size="small">
          <el-table-column label="表别名" width="100" prop="tableAlias" />
          <el-table-column label="字段名" prop="name" />
          <el-table-column label="类型" prop="type" width="100" />
          <el-table-column label="选中" width="70" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.selected" />
            </template>
          </el-table-column>
          <el-table-column label="输出别名" min-width="160">
            <template #default="{ row }">
              <el-input
                v-if="row.selected"
                v-model="row.alias"
                size="small"
                placeholder="自定义列名"
              />
              <span v-else style="color:#999">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="wizard.selectedColumns.length === 0" style="color:#E6A23C;margin-top:8px">
          请先在步骤③中选择主表
        </div>
      </template>

      <!-- INSERT/UPDATE：字段数据来源配置 -->
      <template v-else>
        <div v-for="(tbl, tIdx) in wizard.fieldTables" :key="tIdx" style="margin-bottom:20px">
          <div style="font-weight:600;margin-bottom:8px">
            表：{{ tbl.tableName || '（未选表）' }}
          </div>
          <el-table :data="tbl.fields" border size="small">
            <el-table-column label="字段名" width="160" prop="column" />
            <el-table-column label="类型" width="100" prop="columnType" />
            <el-table-column label="数据来源" width="150">
              <template #default="{ row }">
                <el-select v-model="row.sourceType" size="small" style="width:100%">
                  <el-option label="请求字段" value="REQUEST" />
                  <el-option label="固定值" value="CONST" />
                  <el-option label="运算表达式" value="CALC" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="值配置">
              <template #default="{ row }">
                <el-input
                  v-if="row.sourceType === 'REQUEST'"
                  v-model="row.paramKey"
                  size="small"
                  placeholder="请求参数名，如 userId"
                />
                <el-input
                  v-else-if="row.sourceType === 'CONST'"
                  v-model="row.constValue"
                  size="small"
                  placeholder="固定值，如 active"
                />
                <el-input
                  v-else
                  v-model="row.expression"
                  size="small"
                  placeholder="四则运算，如 price * qty"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button
                  size="small"
                  type="danger"
                  link
                  @click="tbl.fields.splice($index, 1)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            size="small"
            style="margin-top:8px"
            @click="tbl.fields.push({ column: '', columnType: '', sourceType: 'REQUEST', paramKey: '', constValue: '', expression: '' })"
          >+ 添加字段</el-button>
        </div>
        <div v-if="wizard.fieldTables.length === 0" style="color:#E6A23C">
          请先在步骤③中选择目标表
        </div>
      </template>
    </div>
```

- [ ] **步骤2：验证**

```bash
npm run dev
```

1. SELECT 接口 → 步骤④ 显示字段勾选列表，可取消勾选、修改别名
2. INSERT 接口 → 步骤④ 显示字段数据来源配置表，切换 REQUEST/CONST/CALC 对应输入框变化
3. DELETE 接口 → 步骤④不出现在步骤条中

- [ ] **步骤3：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard step 4 (field config, type-aware)"
```

---

## Task 7: 步骤⑤⑥ — 分库分表 + 字段加工

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：在 script setup 中添加分片规则加载逻辑**

```js
import { listShardConfigs } from '@/api/shardConfig'

const shardList = ref([])

onMounted(async () => {
  // ...（现有代码）...
  try {
    const res = await listShardConfigs()
    shardList.value = res?.records || res || []
  } catch {}
})
```

添加跳过步骤函数：
```js
function skipStep() {
  wizard.currentStep++
}
```

- [ ] **步骤2：替换步骤⑤⑥占位内容**

将 `<div v-show="isActive('shard')">` 替换为：

```html
    <!-- ⑤ 分库分表（可选） -->
    <div v-show="isActive('shard')">
      <p style="color:#606266;margin-bottom:16px">
        如果此接口需要分库分表路由，请选择对应的分片规则；否则可直接跳过。
      </p>
      <el-form label-width="120px" style="max-width:500px">
        <el-form-item label="分片规则">
          <el-select
            v-model="wizard.shardConfigId"
            placeholder="选择分片规则（可为空）"
            clearable
            style="width:280px"
          >
            <el-option
              v-for="s in shardList"
              :key="s.id"
              :label="s.name"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div style="margin-top:16px">
        <el-button type="primary" plain @click="skipStep">跳过，直接下一步</el-button>
      </div>
    </div>
```

将 `<div v-show="isActive('process')">` 替换为：

```html
    <!-- ⑥ 字段加工（可选，DELETE 不显示） -->
    <div v-show="isActive('process')">
      <p style="color:#606266;margin-bottom:16px">
        配置字段加工规则（如截位、补位、大小写转换），不需要可直接跳过。
      </p>
      <el-table :data="wizard.processRules" border size="small" style="margin-bottom:12px">
        <el-table-column label="加工类型" width="160">
          <template #default="{ row }">
            <el-select v-model="row.type" size="small" style="width:100%">
              <el-option label="去空格" value="TRIM" />
              <el-option label="转大写" value="UPPER" />
              <el-option label="转小写" value="LOWER" />
              <el-option label="截取" value="SUBSTRING" />
              <el-option label="补位" value="PAD" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="目标字段" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.field" size="small" placeholder="字段名" />
          </template>
        </el-table-column>
        <el-table-column label="参数" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.params" size="small" placeholder="如：length=10,padChar=0" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button size="small" type="danger" link @click="wizard.processRules.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;gap:12px">
        <el-button size="small" @click="wizard.processRules.push({ type: 'TRIM', field: '', params: '' })">
          + 添加规则
        </el-button>
        <el-button type="primary" plain @click="skipStep">跳过，直接下一步</el-button>
      </div>
    </div>
```

- [ ] **步骤3：验证**

```bash
npm run dev
```

1. 步骤⑤ 显示分片规则下拉（若有已配置规则则有选项），点"跳过"可直接到步骤⑥
2. 步骤⑥ 显示加工规则表，可添加规则，点"跳过"可继续
3. DELETE 接口 → 步骤⑤⑥ 均不在步骤条中出现

- [ ] **步骤4：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard steps 5-6 (shard config, field processing)"
```

---

## Task 8: 步骤⑦⑧ — 条件配置 + 日志开关

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：导入 ConditionBuilder**

在 `import` 区域追加：
```js
import ConditionBuilder from '@/components/ConditionBuilder.vue'
```

添加条件字段选项的 computed：
```js
const conditionFieldOptions = computed(() => {
  const opts = []
  if (wizard.interfaceType === 'SELECT') {
    if (wizard.mainTable.alias && wizard.mainTable.name) {
      for (const col of (wizard.tableColumns[wizard.mainTable.name] || [])) {
        opts.push({
          label: `${wizard.mainTable.alias}.${col.name} (${col.type})`,
          value: `${wizard.mainTable.alias}.${col.name}`
        })
      }
    }
    for (const join of wizard.joinConfigs) {
      if (!join.rightAlias || !join.rightTableName) continue
      for (const col of (wizard.tableColumns[join.rightTableName] || [])) {
        opts.push({
          label: `${join.rightAlias}.${col.name} (${col.type})`,
          value: `${join.rightAlias}.${col.name}`
        })
      }
    }
  } else {
    const tableName = wizard.tables[0]?.tableName
    if (tableName) {
      for (const col of (wizard.tableColumns[tableName] || [])) {
        opts.push({
          label: `${col.name} (${col.type})`,
          value: col.name
        })
      }
    }
  }
  return opts
})
```

- [ ] **步骤2：替换步骤⑦⑧占位内容**

将 `<div v-show="isActive('cond')">` 替换为：

```html
    <!-- ⑦ 条件配置（INSERT 不显示） -->
    <div v-show="isActive('cond')">
      <p v-if="wizard.interfaceType === 'SELECT'" style="color:#909399;margin-bottom:12px">
        可选：配置查询条件，不需要可直接跳过
      </p>
      <p v-else style="color:#E6A23C;margin-bottom:12px">
        必填：UPDATE/DELETE 接口必须配置至少一个包含主键的条件
      </p>
      <ConditionBuilder
        v-model="wizard.conditions"
        :field-options="conditionFieldOptions"
      />
      <div v-if="wizard.interfaceType === 'SELECT'" style="margin-top:16px">
        <el-button type="primary" plain @click="skipStep">跳过，直接下一步</el-button>
      </div>
    </div>
```

将 `<div v-show="isActive('log')">` 替换为：

```html
    <!-- ⑧ 日志开关 -->
    <div v-show="isActive('log')">
      <el-form label-width="120px" style="max-width:500px">
        <el-form-item label="操作日志">
          <el-switch
            v-model="wizard.logEnabled"
            active-text="开启"
            inactive-text="关闭"
          />
          <div style="color:#909399;font-size:12px;margin-top:6px">
            开启后，每次接口调用都将记录到操作日志（审计用）
          </div>
        </el-form-item>
      </el-form>
    </div>
```

- [ ] **步骤3：验证**

```bash
npm run dev
```

1. SELECT 步骤⑦ 显示 ConditionBuilder，有"跳过"按钮
2. DELETE 步骤⑦ 显示必填提示，无跳过按钮
3. INSERT 接口步骤条中不含步骤⑦
4. 步骤⑧ 日志开关默认开启，可切换

- [ ] **步骤4：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard steps 7-8 (conditions, log switch)"
```

---

## Task 9: 步骤⑨ — 预览测试 + buildPayload

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：在 script setup 中添加 buildPayload 和预览逻辑**

```js
import { saveInterface, previewInterface, deletePreview } from '@/api/interface'

const previewing = ref(false)
const previewDone = ref(false)
const previewColumns = computed(() =>
  wizard.previewResult.length ? Object.keys(wizard.previewResult[0]) : []
)

function buildPayload() {
  const base = {
    id: wizard.savedId || undefined,
    name: wizard.interfaceName,
    dbConnectionId: wizard.dbConnectionId,
    type: wizard.interfaceType,
    logEnabled: wizard.logEnabled,
    shardConfigId: wizard.shardConfigId || undefined,
  }

  if (wizard.interfaceType === 'SELECT') {
    const tables = [{ name: wizard.mainTable.name, alias: wizard.mainTable.alias }]
    for (const j of wizard.joinConfigs) {
      if (j.rightTableName) tables.push({ name: j.rightTableName, alias: j.rightAlias })
    }
    const joins = wizard.joinConfigs
      .filter(j => j.rightTableName && j.leftCol && j.rightCol)
      .map(j => ({
        leftTable: wizard.mainTable.alias, leftCol: j.leftCol,
        rightTable: j.rightAlias, rightCol: j.rightCol, type: j.type
      }))
    const fields = wizard.selectedColumns
      .filter(c => c.selected)
      .map(c => ({ table: c.tableAlias, column: c.name, alias: c.alias || c.name }))
    base.configJson = JSON.stringify({ tables, joins, fields, conditions: wizard.conditions, processRules: wizard.processRules })

  } else if (wizard.interfaceType === 'INSERT') {
    const tables = wizard.fieldTables.map(t => ({
      name: t.tableName,
      fields: t.fields.map(f => ({
        column: f.column, columnType: f.columnType,
        sourceType: f.sourceType, paramKey: f.paramKey,
        constValue: f.constValue, expression: f.expression
      }))
    }))
    base.configJson = JSON.stringify({ tables, processRules: wizard.processRules })

  } else if (wizard.interfaceType === 'UPDATE') {
    const tables = wizard.fieldTables.map(t => ({
      name: t.tableName,
      fields: t.fields.map(f => ({
        column: f.column, columnType: f.columnType,
        sourceType: f.sourceType, paramKey: f.paramKey,
        constValue: f.constValue, expression: f.expression
      }))
    }))
    base.configJson = JSON.stringify({ tables, conditions: wizard.conditions, processRules: wizard.processRules })

  } else if (wizard.interfaceType === 'DELETE') {
    const tables = wizard.tables.map(t => ({ name: t.tableName }))
    base.configJson = JSON.stringify({ tables, conditions: wizard.conditions })
  }

  return base
}

async function doPreview() {
  if (!wizard.interfaceName.trim()) {
    ElMessage.warning('请先在步骤②填写接口名称')
    return
  }
  previewing.value = true
  previewDone.value = false
  try {
    const id = await saveInterface(buildPayload())
    wizard.savedId = id
    if (wizard.interfaceType === 'DELETE') {
      wizard.previewResult = await deletePreview(id, wizard.previewParams) || []
    } else {
      wizard.previewResult = await previewInterface(id, wizard.previewParams) || []
    }
    previewDone.value = true
  } catch {
    // request.js 统一提示
  } finally {
    previewing.value = false
  }
}
```

- [ ] **步骤2：替换步骤⑨占位内容**

将 `<div v-show="isActive('preview')">` 替换为：

```html
    <!-- ⑨ 预览测试 -->
    <div v-show="isActive('preview')">
      <!-- DELETE 警告 -->
      <el-alert
        v-if="wizard.interfaceType === 'DELETE'"
        title="以下将预览待删除数据，执行预览不会真正删除"
        type="warning"
        :closable="false"
        style="margin-bottom:16px"
      />

      <!-- 条件参数输入 -->
      <div v-if="wizard.conditions.length > 0" style="margin-bottom:16px">
        <p style="font-weight:500;margin-bottom:8px">输入预览参数</p>
        <el-form label-width="120px" size="small">
          <el-form-item
            v-for="cond in wizard.conditions"
            :key="cond.paramKey"
            :label="cond.paramKey || '参数'"
          >
            <el-input
              v-model="wizard.previewParams[cond.paramKey]"
              :placeholder="`${cond.field} ${cond.op}`"
              style="width:260px"
            />
          </el-form-item>
        </el-form>
      </div>

      <el-button type="primary" :loading="previewing" @click="doPreview">
        执行预览（前10条）
      </el-button>

      <div v-if="wizard.previewResult.length > 0" style="margin-top:16px">
        <p style="font-weight:500">预览结果（{{ wizard.previewResult.length }} 条）</p>
        <el-table :data="wizard.previewResult" border size="small" max-height="300">
          <el-table-column
            v-for="col in previewColumns"
            :key="col"
            :prop="col"
            :label="col"
            show-overflow-tooltip
          />
        </el-table>
      </div>
      <el-empty v-else-if="previewDone" description="无数据" :image-size="60" />
    </div>
```

- [ ] **步骤3：验证**

```bash
npm run dev
```

1. 完成步骤①～⑧后进入步骤⑨，点"执行预览"
2. SELECT：返回数据行，显示在表格中
3. DELETE：显示待删数据预览和警告
4. 若未填接口名称，提示"请先在步骤②填写接口名称"

- [ ] **步骤4：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard step 9 (preview, buildPayload)"
```

---

## Task 10: 步骤⑩ — 保存发布

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：在 script setup 中添加保存发布逻辑**

```js
import { publishInterface } from '@/api/interface'

const saving = ref(false)
const publishing = ref(false)

async function doSave() {
  if (!wizard.interfaceName.trim()) {
    ElMessage.warning('请填写接口名称')
    return
  }
  saving.value = true
  try {
    const id = await saveInterface(buildPayload())
    wizard.savedId = id
    wizard.reset()
    ElMessage.success('保存成功')
    router.push('/interface/list')
  } catch {
  } finally {
    saving.value = false
  }
}

async function doPublish() {
  if (!wizard.interfaceName.trim()) {
    ElMessage.warning('请填写接口名称')
    return
  }
  publishing.value = true
  try {
    const id = await saveInterface(buildPayload())
    wizard.savedId = id
    await publishInterface(id)
    wizard.reset()
    ElMessage.success('发布成功')
    router.push('/interface/list')
  } catch {
  } finally {
    publishing.value = false
  }
}
```

- [ ] **步骤2：替换步骤⑩占位内容**

将 `<div v-show="isActive('publish')">` 替换为：

```html
    <!-- ⑩ 保存发布 -->
    <div v-show="isActive('publish')">
      <el-form label-width="120px" style="max-width:600px">
        <el-form-item label="接口名称" required>
          <el-input
            v-model="wizard.interfaceName"
            placeholder="请输入接口名称"
            style="width:300px"
          />
        </el-form-item>
        <el-form-item label="接口类型">
          <el-tag>{{ wizard.interfaceType }}</el-tag>
        </el-form-item>
        <el-form-item label="数据库">
          <span>{{ dbList.find(d => d.id === wizard.dbConnectionId)?.name || '—' }}</span>
        </el-form-item>
        <el-form-item label="日志开关">
          <span>{{ wizard.logEnabled ? '已开启' : '已关闭' }}</span>
        </el-form-item>
      </el-form>

      <div style="display:flex;gap:12px;margin-top:24px">
        <el-button :loading="saving" @click="doSave">仅保存（草稿）</el-button>
        <el-button type="primary" :loading="publishing" @click="doPublish">
          保存并发布
        </el-button>
      </div>
    </div>
```

- [ ] **步骤3：验证**

```bash
npm run dev
```

1. 完整走一遍 SELECT 接口向导（10步），点"保存并发布"
2. 成功后跳转 `/interface/list`，列表中出现新接口且状态为 published
3. 通过 `/api/exec/{id}` 调用该接口，返回数据正确
4. 重新访问 `/interface/wizard` 弹出草稿恢复提示（此时 reset 已清除，应无草稿）

- [ ] **步骤4：提交**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): implement wizard step 10 (save, publish)"
```

---

## Task 11: 步骤校验 + 完整验收

**文件：**
- 修改: `frontend/src/views/interface/InterfaceWizard.vue`

- [ ] **步骤1：替换 nextStep 函数，加入各步骤前置校验**

```js
function nextStep() {
  const key = currentStepDef.value.key

  if (key === 'type') {
    if (!wizard.interfaceType) {
      ElMessage.warning('请选择接口类型')
      return
    }
  } else if (key === 'db') {
    if (!wizard.interfaceName.trim()) {
      ElMessage.warning('请填写接口名称')
      return
    }
    if (!wizard.dbConnectionId) {
      ElMessage.warning('请选择数据库连接')
      return
    }
  } else if (key === 'tables') {
    if (wizard.interfaceType === 'SELECT') {
      if (!wizard.mainTable.name) {
        ElMessage.warning('请选择主表')
        return
      }
    } else {
      if (!wizard.tables[0]?.tableName) {
        ElMessage.warning('请选择目标表')
        return
      }
    }
  } else if (key === 'fields') {
    if (wizard.interfaceType === 'SELECT') {
      const hasSelected = wizard.selectedColumns.some(c => c.selected)
      if (!hasSelected) {
        ElMessage.warning('请至少勾选一个返回字段')
        return
      }
    } else {
      for (const tbl of wizard.fieldTables) {
        if (tbl.fields.length === 0) {
          ElMessage.warning(`表 ${tbl.tableName} 尚未配置任何字段`)
          return
        }
        for (const f of tbl.fields) {
          if (!f.column) {
            ElMessage.warning(`表 ${tbl.tableName} 有字段名为空`)
            return
          }
        }
      }
    }
  } else if (key === 'cond') {
    if (wizard.interfaceType === 'UPDATE' || wizard.interfaceType === 'DELETE') {
      if (wizard.conditions.length === 0) {
        ElMessage.warning('UPDATE/DELETE 必须配置至少一个条件')
        return
      }
    }
  }

  wizard.currentStep++
}
```

- [ ] **步骤2：端到端验收 — SELECT 接口**

```bash
npm run dev
```

按以下步骤操作，每步验证通过才继续：
1. 访问 `/interface/wizard` → 步骤①选 SELECT
2. 步骤②填接口名"测试查询接口"，选已有数据库
3. 步骤③选主表，主表别名自动填充
4. 步骤④勾选3个字段，一个改输出别名
5. 步骤⑤点跳过
6. 步骤⑥点跳过
7. 步骤⑦配置一个条件（如 id = :id），点下一步
8. 步骤⑧日志开关保持开启
9. 步骤⑨输入预览参数，点执行预览，结果显示
10. 步骤⑩点"保存并发布"，跳转列表页
11. 在接口列表找到新接口，状态 published
12. 关闭浏览器，重新打开 `/interface/wizard`，无草稿提示

- [ ] **步骤3：端到端验收 — DELETE 接口**

1. 访问 `/interface/wizard` → 步骤①选 DELETE
2. 步骤条显示8步（无④⑥）
3. 步骤②填名称+选数据库
4. 步骤③选目标表
5. 步骤⑤（分库分表）跳过
6. 步骤⑦（条件配置）配置条件，不配置直接点下一步 → 应提示"必须配置至少一个条件"
7. 配置主键条件，点下一步
8. 步骤⑧日志开关
9. 步骤⑨显示 DELETE 预览警告，预览结果正确
10. 步骤⑩发布，接口列表有新 DELETE 接口

- [ ] **步骤4：端到端验收 — 草稿恢复**

1. 进入向导走到步骤④，不完成
2. 关闭页面
3. 重新访问 `/interface/wizard`，弹出"发现未完成配置，是否恢复？"
4. 点"恢复" → 从步骤④继续
5. 重新访问，点"重新开始" → 从步骤①重来

- [ ] **步骤5：验证「向导新建」与原有配置页互不影响**

1. 通过向导完成并发布一个 INSERT 接口
2. 访问 `/interface/insert`（直接配置页），正常工作
3. 访问 `/interface/list`，同时出现两种方式创建的接口

- [ ] **步骤6：提交并推送**

```bash
git add frontend/src/views/interface/InterfaceWizard.vue
git commit -m "feat(SYS-5): add step validation, complete end-to-end wizard"
git push
```

---

## 验收门槛回顾

| # | 验收项 | 验证方式 |
|---|--------|---------|
| 1 | SELECT 接口走完10步，发布后 `/api/exec/{id}` 成功调用 | Task 11 步骤2 |
| 2 | INSERT 接口含 REQUEST/CONST/CALC 三种来源，发布后可执行 | 手动测试 |
| 3 | DELETE 步骤条自动收缩为8步 | Task 11 步骤3 |
| 4 | UPDATE 步骤条显示完整10步，步骤⑦必填 | 手动测试 |
| 5 | 草稿恢复流程正确 | Task 11 步骤4 |
| 6 | 向导与原有配置页互不影响 | Task 11 步骤5 |
| 7 | DELETE 预览显示待删数据 | Task 11 步骤3 |
