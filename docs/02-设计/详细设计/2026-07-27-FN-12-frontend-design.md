# FN-12 前端设计（v0.2.0 ③）

> **单元**：v0.2.0 ③ · **CR 依据**：[CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
> **前置**：v0.2.0 ①（[后端 spec](./2026-07-26-FN-12-dict-mapping-backend-design.md)）· v0.2.0 ②（[Processor spec](./2026-07-27-FN-12-processor-design.md)）已交付
> **设计日期**：2026-07-27 · **状态**：✅ 已 brainstorm · 待 writing-plans

---

## 一、范围与边界

### 1.1 做

1. **`DictMappingList.vue`** at `/tools/dict` — RegistryManagement 模式 · 三级筛选 + el-table + 三选一 direction radio + Excel 导入/导出
2. **`api/dictMapping.js`** — 9 endpoint · 对齐 `api/registry.js` 结构
3. **`stores/dictMapping.js`**（Pinia）— 全量字典 groupBy 缓存 · 5min TTL（Q4/A 一次拉全量）
4. **`DictMappingParamDialog.vue`**（共享组件）— 三级联动下拉 · 供 3 处向导 dialog 弹出编辑
5. **TransformWizard Step 5**（M1-6）映射表"加工规则" el-select 追加 `DICT_MAP` · 选中弹 DictMappingParamDialog
6. **InterfaceWizard Step 6**（M2-3/4/5/6）SelectInterfaceSteps 加工类型 el-select 追加 `DICT_MAP` · 弹同一 dialog
7. **FieldProcess.vue** 独立编辑器加 `DICT_MAP` 分支 · 弹同一 dialog
8. **路由 + 菜单 + 权限** — `/tools/dict` lazy import · SideMenu 追加菜单 · TOOLS_PATHS 加 · `MenuPermission.java` ADMIN + USER + READONLY 都加（Q5/A）
9. **vitest** 8~12 用例覆盖关键交互

### 1.2 不做（划出 v0.2.0 ④）

- FN-09 联动（xlsx 4-sheet + md/html 字典 key 列）→ v0.2.0 ④
- pg-testkit 完整 DDL / Faker 数据 / Mock 规则持久化 → v0.2.0 后段
- SQLite 分支 → v0.2.0 后段

---

## 二、DictMappingList.vue 结构

**位置**：`frontend/src/views/tools/DictMappingList.vue`

**顶部三级筛选 + 操作按钮**：

```vue
<el-card>
  <div class="toolbar">
    <el-select v-model="filter.system"    clearable placeholder="系统"    style="width:180px" />
    <el-select v-model="filter.dictKey"   clearable placeholder="字典键"  style="width:180px" />
    <el-select v-model="filter.direction" clearable placeholder="方向"    style="width:120px">
      <el-option label="出向" :value="1"/>
      <el-option label="入向" :value="2"/>
    </el-select>
    <el-button @click="reload">查询</el-button>
    <el-button @click="resetFilter">重置</el-button>
    <el-button v-if="canWrite" type="primary" @click="openEdit()">新增</el-button>
    <el-button v-if="canWrite" @click="importVisible = true">导入 xlsx</el-button>
    <el-button @click="download">导出 xlsx</el-button>
  </div>

  <el-table :data="pagedData" v-loading="loading">
    <el-table-column prop="id" width="80" />
    <el-table-column prop="systemCode" label="系统" />
    <el-table-column prop="dictKey" label="字典键" />
    <el-table-column label="方向">
      <template #default="{row}">
        <el-tag :type="row.direction === 1 ? 'success' : 'warning'">
          {{ row.direction === 1 ? '出向' : '入向' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="sourceValue" label="source" />
    <el-table-column prop="targetValue" label="target" />
    <el-table-column prop="cnLabel" label="中文含义" />
    <el-table-column label="状态">
      <template #default="{row}">
        <el-switch v-model="row.status" :active-value="1" :inactive-value="0"
                   :disabled="!canWrite" @change="onStatusChange(row)" />
      </template>
    </el-table-column>
    <el-table-column label="操作" v-if="canWrite">
      <template #default="{row}">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)">
          <template #reference>
            <el-button size="small" type="danger">删除</el-button>
          </template>
        </el-popconfirm>
      </template>
    </el-table-column>
  </el-table>

  <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredData.length" />
</el-card>
```

**新增/编辑 dialog · 三选一 radio（Q3/A）**：

```vue
<el-dialog v-model="editVisible" :title="form.id ? '编辑字典条目' : '新增字典条目'" width="600px">
  <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
    <el-form-item label="系统" prop="systemCode">
      <el-select v-model="form.systemCode" filterable allow-create default-first-option
                 placeholder="选择或输入新系统代号">
        <el-option v-for="s in allSystems" :key="s" :label="s" :value="s" />
      </el-select>
    </el-form-item>
    <el-form-item label="字典键" prop="dictKey">
      <el-input v-model="form.dictKey" placeholder="如 GENDER" />
    </el-form-item>
    <el-form-item label="方向" prop="mode">
      <el-radio-group v-model="form.mode">
        <el-radio value="1">出向 (PG → 对端)</el-radio>
        <el-radio value="2">入向 (对端 → PG)</el-radio>
        <el-radio value="both">双向（后端拆两条）</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="源值" prop="sourceValue"><el-input v-model="form.sourceValue"/></el-form-item>
    <el-form-item label="目标值" prop="targetValue"><el-input v-model="form.targetValue"/></el-form-item>
    <el-form-item label="中文含义"><el-input v-model="form.cnLabel"/></el-form-item>
    <el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0"/></el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="editVisible = false">取消</el-button>
    <el-button type="primary" @click="doSave">保存</el-button>
  </template>
</el-dialog>
```

**doSave 拆条逻辑**（Q3/A）：

```js
const payload = {
  systemCode: form.systemCode,
  dictKey: form.dictKey,
  sourceValue: form.sourceValue,
  targetValue: form.targetValue,
  cnLabel: form.cnLabel,
  status: form.status,
  bidirectional: form.mode === 'both',
  direction: form.mode === 'both' ? 1 : Number(form.mode)
}
if (form.id) await updateDictMapping(form.id, payload)
else         await saveDictMapping(payload)
```

**Excel 导入 dialog（Q6/A）**：

```vue
<el-dialog v-model="importVisible" title="导入字典 Excel" width="700px">
  <el-upload accept=".xlsx" :auto-upload="false" :on-change="handleFileSelect" :show-file-list="false">
    <el-button>选择 xlsx 文件</el-button>
  </el-upload>
  <el-button type="primary" :disabled="!selectedFile" @click="doImport">上传</el-button>

  <template v-if="importResult">
    <el-alert v-if="importResult.failedRows.length > 0"
              :title="`共 ${importResult.failedRows.length} 行失败，事务已回滚，无数据入库`"
              type="error" :closable="false" />
    <el-alert v-else :title="`导入成功：${importResult.successCount} 条`" type="success" :closable="false" />

    <el-table v-if="importResult.failedRows.length > 0" :data="importResult.failedRows" max-height="300">
      <el-table-column prop="rowIndex" label="行号" width="100" />
      <el-table-column prop="errorMsg" label="错误描述" />
    </el-table>
  </template>
</el-dialog>
```

---

## 三、DictMappingParamDialog.vue（共享组件）

**位置**：`frontend/src/components/dict/DictMappingParamDialog.vue`

**用途**：TransformWizard Step 5 + InterfaceWizard Step 6 + FieldProcess.vue 三处内部字段加工规则参数编辑复用同一 dialog。

**关键约束**：字段加工场景 direction 只有二选一（1 或 2），**不含"双向"** —— 一条加工规则只走单方向 lookup，"双向加工"没意义（双向配置由管理页处理）。

```vue
<script setup>
import { computed, ref, watch } from 'vue'
import { useDictMappingStore } from '@/stores/dictMapping'

const props = defineProps({ modelValue: Object, visible: Boolean })
const emit  = defineEmits(['update:modelValue', 'update:visible', 'confirm'])

const store = useDictMappingStore()
const local = ref({ system: '', dictKey: '', direction: 1 })

watch(() => props.visible, async v => {
  if (v) {
    await store.ensureLoaded()
    local.value = { ...(props.modelValue || {}), direction: props.modelValue?.direction || 1 }
  }
})

const systems  = computed(() => store.systems)
const dictKeys = computed(() => local.value.system ? store.dictKeysOf(local.value.system) : [])

const doConfirm = () => {
  if (!local.value.system || !local.value.dictKey || !local.value.direction) return
  emit('confirm', {
    system: local.value.system,
    dictKey: local.value.dictKey,
    direction: String(local.value.direction)   // ProcessRule.params 只能存字符串
  })
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog :model-value="visible" @update:model-value="emit('update:visible', $event)"
             title="字典转换参数" width="480px">
    <el-form label-width="90px">
      <el-form-item label="系统">
        <el-select v-model="local.system" filterable placeholder="选择系统">
          <el-option v-for="s in systems" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="字典键">
        <el-select v-model="local.dictKey" filterable placeholder="选择字典键" :disabled="!local.system">
          <el-option v-for="k in dictKeys" :key="k" :label="k" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="方向">
        <el-radio-group v-model="local.direction">
          <el-radio :value="1">出向</el-radio>
          <el-radio :value="2">入向</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!local.system || !local.dictKey" @click="doConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>
```

---

## 四、API + Pinia Store

### 4.1 `frontend/src/api/dictMapping.js`

```js
import axios from './request'
export const listDictMappings   = (params) => axios.get('/api/dict-mapping/list', { params })
export const listSystems        = ()       => axios.get('/api/dict-mapping/systems')
export const getDictMapping     = (id)     => axios.get(`/api/dict-mapping/${id}`)
export const saveDictMapping    = (data)   => axios.post('/api/dict-mapping', data)
export const updateDictMapping  = (id, data) => axios.put(`/api/dict-mapping/${id}`, data)
export const deleteDictMapping  = (id)     => axios.delete(`/api/dict-mapping/${id}`)
export const importDictMappings = (fd)     => axios.post('/api/dict-mapping/import', fd,
                                                { headers: {'Content-Type':'multipart/form-data'} })
export const exportDictMappings = (params) => axios.get('/api/dict-mapping/export',
                                                { params, responseType: 'blob' })
export const lookupDictMapping  = (params) => axios.post('/api/dict-mapping/lookup', null, { params })
```

### 4.2 `frontend/src/stores/dictMapping.js`（Pinia）

```js
import { defineStore } from 'pinia'
import { listDictMappings } from '@/api/dictMapping'

const TTL_MS = 5 * 60 * 1000  // 5 min

export const useDictMappingStore = defineStore('dictMapping', {
  state: () => ({ all: [], groupedData: {}, refreshedAt: 0 }),
  getters: {
    systems: (s) => Object.keys(s.groupedData).sort(),
    dictKeysOf: (s) => (sys) => Object.keys(s.groupedData[sys] || {}).sort(),
  },
  actions: {
    async ensureLoaded(force = false) {
      if (!force && Date.now() - this.refreshedAt < TTL_MS && this.all.length > 0) return
      const res = await listDictMappings()
      this.all = res.data.data || []
      this.groupedData = groupBy(this.all)
      this.refreshedAt = Date.now()
    },
    invalidate() { this.refreshedAt = 0 }
  }
})

function groupBy(rows) {
  const g = {}
  for (const r of rows) {
    if (r.status !== 1) continue  // 只装启用条目供加工使用
    g[r.systemCode]                       ??= {}
    g[r.systemCode][r.dictKey]            ??= {}
    g[r.systemCode][r.dictKey][r.direction] ??= []
    g[r.systemCode][r.dictKey][r.direction].push({ source: r.sourceValue, target: r.targetValue })
  }
  return g
}
```

---

## 五、路由 · 菜单 · 权限

### 5.1 路由

修改 `frontend/src/router/index.js`，在其他 `/tools/*` 之后加：

```js
{
  path: 'tools/dict',
  name: 'DictMappingList',
  component: () => import('@/views/tools/DictMappingList.vue'),
  meta: { title: '字典映射管理' }
}
```

### 5.2 菜单

修改 `frontend/src/components/layout/SideMenu.vue`：

```vue
<el-menu-item v-if="can('/tools/dict')" index="/tools/dict">字典映射管理</el-menu-item>
```

`TOOLS_PATHS` 数组加入 `'/tools/dict'`。

### 5.3 后端权限（Q5/A）

修改 `backend/src/main/java/com/powergateway/config/MenuPermission.java`：

- `ADMIN_MENUS` · `USER_MENUS` · `READONLY_MENUS` **都追加** `/tools/dict`
- 页面内 `canWrite = computed(() => ['admin','user'].includes(userStore.role))`
- 写按钮 `v-if="canWrite"` · 状态 switch `:disabled="!canWrite"`

---

## 六、Excel 导入 dialog · 错误处理（Q6/A）

后端返 `{successCount, failedRows: [{rowIndex, errorMsg}]}`（v0.2.0 ① 已定）。

**前端策略**：
- `successCount > 0 && failedRows.length === 0` → `ElMessage.success` + 关闭 dialog + `store.invalidate()` + 刷新列表
- `failedRows.length > 0` → 保持 dialog 打开 · 顶部红色 el-alert `"共 X 行失败，事务已回滚，无数据入库"` + 下方 el-table 展示 failedRows（rowIndex + errorMsg 两列，可复制）
- 请求本身失败（400/500）→ `ElMessage.error(errorMsg)`

---

## 七、测试策略

### 7.1 vitest 新增（8~12 用例）

**`DictMappingList.spec.js`**：
- 三级筛选联动（选 system 后 dictKey 下拉只显该 system 的键）
- 三选一 direction 提交拆条（选双向 → payload `bidirectional=true, direction=1`）
- readonly 角色写按钮不渲染 / status switch disabled
- Excel 导入 dialog 展示 failedRows

**`DictMappingParamDialog.spec.js`**：
- 二选一 direction（无"双向"选项）
- 三级联动读 Pinia store · system 变化重置 dictKey

**`dictMapping-api.spec.js`**：mock axios 9 endpoint 断言 URL / method / body

### 7.2 回归

- `npm run build` exit 0
- `npx vitest run` 全绿（含既有 TransformWizard/InterfaceWizard vitest）
- 手工冒烟：登录 → 进 /tools/dict → 新增双向条目 → 查 DB 2 条 → 进转换向导 Step 5 → 选 DICT_MAP → 弹 dialog → 选参数 → 保存

---

## 八、Task 拆分预告（详见 writing-plans）

预估 **5 Task · 约 2 人日**：

| Task | 内容 | 累计 vitest |
|:-:|---|:-:|
| 1 | api/dictMapping.js + stores/dictMapping.js Pinia + 路由 + 菜单 + MenuPermission.java 三角色都加 | 3 |
| 2 | DictMappingList.vue 主页（表格 + 三级筛选 + 编辑 dialog 三选一 radio + delete） | 5 |
| 3 | Excel 导入 dialog（Q6/A）+ 导出按钮 | 7 |
| 4 | DictMappingParamDialog.vue + TransformWizard Step 5 + InterfaceWizard Step 6 + FieldProcess.vue 三处集成 | 10 |
| 5 | vitest 补齐 + npm run build 冒烟 + CHG-031 归档 + 待办 v0.2.0 ③ 勾掉 | 10~12 |

---

## 九、CHG-031 归档规划（实施完成后）

- 类型：新增单元（FN-12 前端）+ 扩展既有单元（TransformWizard / InterfaceWizard / FieldProcess / MenuPermission）
- 关联：[CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射) · [FB-039](../../06-项目管理/反馈簿.md#fb-039)
- 归入：v0.2.0 ③

---

## 十、相关文档

- [v0.2.0 ① 后端 spec](./2026-07-26-FN-12-dict-mapping-backend-design.md) · [plan](../../03-开发/任务计划/2026-07-26-FN-12-backend.md)
- [v0.2.0 ② Processor spec](./2026-07-27-FN-12-processor-design.md) · [plan](../../03-开发/任务计划/2026-07-27-FN-12-processor.md)
- [CR-001](../../06-项目管理/待办与缺陷池.md#cr-001-字典转换配置字段字典映射)
- [路线图 § v0.2.0](../../06-项目管理/路线图.md)
- 实施 TDD 分解将保存至 `docs/03-开发/任务计划/2026-07-27-FN-12-frontend.md`（writing-plans 阶段产出）
