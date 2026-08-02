<template>
  <div class="query-config-page">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <el-button @click="goList">返回列表</el-button>
    </div>

    <!-- v0.3.5 · 步骤条 4 → 6 · 加字段加工 + 分片(参考 v0.3.1 Task 5) -->
    <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="选择数据库与表" />
      <el-step title="选择返回字段" />
      <el-step title="字段加工(可选)" />
      <el-step title="分库分表(可选)" />
      <el-step title="配置查询条件" />
      <el-step title="预览与保存" />
    </el-steps>

    <!-- 步骤1：选择数据库连接和表 -->
    <div v-show="step === 0">
      <el-card>
        <template #header>Step 1 · 选择数据库与表</template>

        <el-form label-width="120px">
          <el-form-item label="接口名称" required>
            <el-input v-model="form.name" placeholder="请输入接口名称" style="width: 400px" />
          </el-form-item>

          <el-form-item label="数据库连接" required>
            <el-select
              v-model="form.dbConnectionId"
              placeholder="请选择数据库连接"
              style="width: 400px"
              @change="onDbChange"
            >
              <el-option
                v-for="db in dbList"
                :key="db.id"
                :label="db.name"
                :value="db.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="默认响应格式">
            <el-select v-model="form.responseFormat" style="width:180px">
              <el-option label="JSON" value="JSON" />
              <el-option label="XML" value="XML" />
              <el-option label="CSV" value="CSV" />
              <el-option label="FormData" value="FORM_DATA" />
            </el-select>
            <el-tooltip content="调用方可通过 Accept 头或 ?format= 覆盖此默认值">
              <el-icon style="margin-left:6px"><QuestionFilled /></el-icon>
            </el-tooltip>
          </el-form-item>
          <el-form-item label="自定义响应头">
            <ResponseHeadersEditor v-model="form.responseHeaders" />
          </el-form-item>

          <el-form-item label="主表" required>
            <el-select
              v-model="mainTable.name"
              placeholder="请选择主表"
              style="width: 200px"
              filterable
              @change="onMainTableChange"
            >
              <el-option
                v-for="t in tableList"
                :key="t.tableName"
                :label="t.tableName + (t.comment ? ` (${t.comment})` : '')"
                :value="t.tableName"
              />
            </el-select>
            <span style="margin: 0 8px">别名</span>
            <el-input v-model="mainTable.alias" style="width: 100px" placeholder="如 u" />
          </el-form-item>

          <!-- 关联表 -->
          <el-form-item label="关联表">
            <div v-for="(join, idx) in joinConfigs" :key="idx" class="join-row">
              <el-select
                v-model="join.rightTableName"
                placeholder="选择关联表"
                style="width: 160px"
                filterable
                @change="(v) => onJoinTableChange(idx, v)"
              >
                <el-option
                  v-for="t in tableList"
                  :key="t.tableName"
                  :label="t.tableName"
                  :value="t.tableName"
                />
              </el-select>
              <span style="margin: 0 6px">别名</span>
              <el-input v-model="join.rightAlias" style="width: 80px" placeholder="如 ic" />
              <span style="margin: 0 6px">JOIN</span>
              <el-select v-model="join.type" style="width: 100px">
                <el-option label="LEFT JOIN" value="LEFT" />
                <el-option label="INNER JOIN" value="INNER" />
                <el-option label="RIGHT JOIN" value="RIGHT" />
              </el-select>
              <span style="margin: 0 6px">ON</span>
              <el-select v-model="join.leftCol" placeholder="左表字段" style="width: 140px" filterable>
                <el-option
                  v-for="col in mainTableColumns"
                  :key="col.name"
                  :label="mainTable.alias + '.' + col.name"
                  :value="col.name"
                />
              </el-select>
              <span style="margin: 0 6px">=</span>
              <el-select v-model="join.rightCol" placeholder="右表字段" style="width: 140px" filterable>
                <el-option
                  v-for="col in getJoinTableColumns(join.rightTableName)"
                  :key="col.name"
                  :label="join.rightAlias + '.' + col.name"
                  :value="col.name"
                />
              </el-select>
              <el-button type="danger" link style="margin-left: 8px" @click="removeJoin(idx)">删除</el-button>
            </div>
            <el-button plain size="small" @click="addJoin">+ 添加关联表</el-button>
          </el-form-item>
        </el-form>

        <div style="text-align: right">
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>
    </div>

    <!-- 步骤2：选择返回字段 -->
    <div v-show="step === 1">
      <el-card>
        <template #header>Step 2 · 选择返回字段</template>

        <el-table :data="allColumns" border size="small">
          <el-table-column type="selection" width="50" />
          <el-table-column label="表" width="100">
            <template #default="{ row }">{{ row.tableAlias }}</template>
          </el-table-column>
          <el-table-column label="列名" prop="name" />
          <el-table-column label="类型" prop="type" width="100" />
          <el-table-column label="选中" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.selected" />
            </template>
          </el-table-column>
          <el-table-column label="输出别名" min-width="140">
            <template #default="{ row }">
              <el-input
                v-if="row.selected"
                v-model="row.alias"
                placeholder="可自定义列名"
                size="small"
              />
              <span v-else style="color: #999">—</span>
            </template>
          </el-table-column>
          <!-- v0.3.5 · 字典键下拉(可选 · 复用 FN-12 scope=2 · 接口开发侧) -->
          <el-table-column label="字典键" min-width="180">
            <template #default="{ row }">
              <el-select
                v-if="row.selected"
                v-model="row.dictKey"
                placeholder="不使用字典"
                size="small"
                clearable
                filterable
                style="width:100%"
              >
                <el-option-group v-for="sys in dictSystems" :key="sys.systemCode" :label="sys.systemCode">
                  <el-option
                    v-for="k in sys.dictKeys"
                    :key="k"
                    :label="k"
                    :value="sys.systemCode + ':' + k"
                  />
                </el-option-group>
              </el-select>
              <span v-else style="color:#999">—</span>
            </template>
          </el-table-column>
        </el-table>

        <div style="text-align: right; margin-top: 16px">
          <el-button @click="step--">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>
    </div>

    <!-- v0.3.5 · 步骤3:字段加工(可选) -->
    <div v-show="step === 2">
      <el-card>
        <template #header>Step 3 · 字段加工(可选)</template>
        <el-alert type="info" :closable="false" style="margin-bottom:12px">
          字段加工规则(截位/补位/大小写/字典映射等)· 复用 M1-3 FieldProcessor · 详见
          <el-button type="primary" link @click="openFieldProcessPage">
            独立编辑器(/convert/field-process)
          </el-button>
        </el-alert>
        <el-form label-width="120px">
          <el-form-item label="processRules JSON">
            <el-input v-model="processRulesText" type="textarea" :rows="8"
              placeholder='示例:[{"field":"amount","type":"PAD_LEFT","params":{"len":"12","char":"0"}}]' />
          </el-form-item>
          <el-form-item label="当前规则数">
            <el-tag>{{ processRulesCount }} 条</el-tag>
          </el-form-item>
        </el-form>
        <div style="text-align:right; margin-top:16px">
          <el-button @click="step--">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>
    </div>

    <!-- v0.3.5 · 步骤4:分库分表(可选) -->
    <div v-show="step === 3">
      <el-card>
        <template #header>Step 4 · 分库分表(可选)</template>
        <el-alert type="info" :closable="false" style="margin-bottom:12px">
          M2-8 分片配置 · 未配则不启用分片。
          <el-button type="primary" link @click="openShardConfigPage">新建分片(/interface/shard)</el-button>
        </el-alert>
        <el-form label-width="140px">
          <el-form-item label="分片配置">
            <el-select v-model="form.shardConfigId" placeholder="不启用分片" clearable filterable style="width:400px">
              <el-option v-for="sc in shardConfigList" :key="sc.id"
                :label="sc.name + ' · ' + (sc.moduleName || '')" :value="sc.id" />
            </el-select>
            <el-button link @click="loadShardConfigs" style="margin-left:8px">刷新</el-button>
          </el-form-item>
        </el-form>
        <div style="text-align:right; margin-top:16px">
          <el-button @click="step--">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>
    </div>

    <!-- 步骤5：配置查询条件 -->
    <div v-show="step === 4">
      <el-card>
        <template #header>Step 5 · 配置查询条件</template>

        <ConditionBuilder
          v-model="conditions"
          :field-options="conditionFieldOptions"
        />

        <div style="text-align: right; margin-top: 16px">
          <el-button @click="step--">上一步</el-button>
          <el-button type="primary" @click="nextStep">下一步</el-button>
        </div>
      </el-card>
    </div>

    <!-- 步骤6：预览与保存 -->
    <div v-show="step === 5">
      <el-card>
        <template #header>Step 6 · 预览与保存</template>

        <!-- 预览参数输入 -->
        <div v-if="conditions.length > 0">
          <p style="font-weight: 500; margin-bottom: 8px">输入预览参数</p>
          <el-form label-width="120px" size="small">
            <el-form-item
              v-for="cond in conditions"
              :key="cond.paramKey"
              :label="cond.paramKey || '参数'"
            >
              <el-input
                v-model="previewParams[cond.paramKey]"
                :placeholder="`${cond.field} ${cond.op}`"
                style="width: 260px"
              />
            </el-form-item>
          </el-form>
        </div>

        <el-button type="default" :loading="previewing" @click="doPreview">
          执行预览（前10条）
        </el-button>

        <!-- 预览结果表格 -->
        <div v-if="previewResult.length > 0" style="margin-top: 16px">
          <p style="font-weight: 500">预览结果（{{ previewResult.length }} 条）</p>
          <el-table :data="previewResult" border size="small" max-height="300">
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

        <div style="text-align: right; margin-top: 24px">
          <el-button @click="step--">上一步</el-button>
          <el-button type="primary" :loading="saving" @click="doSave">保存配置</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { saveInterface, previewInterface, getInterface } from '@/api/interface'
import { listShardConfigs } from '@/api/shardConfig'
import { listDictMappings } from '@/api/dictMapping'
import ConditionBuilder from '@/components/ConditionBuilder.vue'
import ResponseHeadersEditor from '@/components/interface/ResponseHeadersEditor.vue'

const router = useRouter()
const route = useRoute()

// ─── 步骤状态 ──────────────────────────────────────────────────────────────────
const step = ref(0)

// ─── 表单数据（持久化到 localStorage 防丢失） ──────────────────────────────────
const STORAGE_KEY = 'query_config_draft'

const form = reactive({ name: '', dbConnectionId: null, responseFormat: 'JSON', responseHeaders: '', shardConfigId: null })
const mainTable = reactive({ name: '', alias: 'a' })
const joinConfigs = ref([])  // [{ rightTableName, rightAlias, type, leftCol, rightCol }]
const conditions = ref([])
const allColumns = ref([])   // [{ tableAlias, name, type, selected, alias }]
const previewParams = reactive({})
const previewResult = ref([])
const previewDone = ref(false)

// ─── 远程数据 ─────────────────────────────────────────────────────────────────
const dbList = ref([])
const tableList = ref([])     // 当前连接的所有表
const tableColumns = ref({})  // { tableName: [ColumnMeta] }

// ─── 状态标志 ─────────────────────────────────────────────────────────────────
const saving = ref(false)
const previewing = ref(false)
const savedId = ref(route.query.id ? Number(route.query.id) : null)

// v0.3.5 · edit 回填 + header + 字典键 + processRules + shardConfigId
const editMode = computed(() => !!savedId.value)
const pageTitle = computed(() => editMode.value
    ? `编辑查询接口 #${savedId.value} · ${form.name || '(未命名)'}`
    : '查询接口配置')
const processRules = ref([])
const processRulesText = computed({
  get: () => JSON.stringify(processRules.value, null, 2),
  set: (v) => {
    try { processRules.value = JSON.parse(v) } catch { /* 允许用户中间态无效 · 保存前 verify */ }
  }
})
const processRulesCount = computed(() => processRules.value.length)
const dictSystems = ref([])          // [{ systemCode, dictKeys: [] }]
const shardConfigList = ref([])
// form 加 shardConfigId

// ─── 计算属性 ─────────────────────────────────────────────────────────────────

/** 主表所有列 */
const mainTableColumns = computed(() =>
  mainTable.name ? (tableColumns.value[mainTable.name] || []) : []
)

/** 条件配置中的字段下拉选项 */
const conditionFieldOptions = computed(() => {
  const opts = []
  // 主表
  if (mainTable.alias && mainTable.name) {
    for (const col of (tableColumns.value[mainTable.name] || [])) {
      opts.push({
        label: `${mainTable.alias}.${col.name} (${col.type})`,
        value: `${mainTable.alias}.${col.name}`
      })
    }
  }
  // 关联表
  for (const join of joinConfigs.value) {
    if (!join.rightAlias || !join.rightTableName) continue
    for (const col of (tableColumns.value[join.rightTableName] || [])) {
      opts.push({
        label: `${join.rightAlias}.${col.name} (${col.type})`,
        value: `${join.rightAlias}.${col.name}`
      })
    }
  }
  return opts
})

/** 预览结果的列名 */
const previewColumns = computed(() =>
  previewResult.value.length ? Object.keys(previewResult.value[0]) : []
)

// ─── 生命周期 ─────────────────────────────────────────────────────────────────

onMounted(async () => {
  try {
    const data = await listConnections()
    dbList.value = data || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
  // v0.3.5 · 预加载字典系统 + 分片列表(供字段区 + 分片 step 用)
  loadDictSystems()
  loadShardConfigs()
  // v0.3.5 · edit 分支优先 draft
  if (savedId.value) {
    await loadForEdit(savedId.value)
  } else {
    loadDraft()
  }
})

// v0.3.5 · edit 回填:拉接口 → 反解 form + mainTable + joinConfigs + allColumns + conditions + processRules + shardConfigId
async function loadForEdit(id) {
  try {
    const cfg = await getInterface(id)
    form.name = cfg.name || ''
    form.dbConnectionId = cfg.dbConnectionId || null
    form.responseFormat = cfg.responseFormat || 'JSON'
    form.responseHeaders = cfg.responseHeaders || ''
    form.shardConfigId = cfg.shardConfigId || null
    if (form.dbConnectionId) await onDbChange(form.dbConnectionId)
    if (cfg.configJson) {
      try {
        const j = JSON.parse(cfg.configJson)
        if (j.tables && j.tables.length) {
          mainTable.name = j.tables[0].name
          mainTable.alias = j.tables[0].alias || 'a'
        }
        if (j.joins) {
          joinConfigs.value = j.joins.map((jn, idx) => ({
            rightTableName: (j.tables[idx + 1] || {}).name || '',
            rightAlias: jn.rightTable || '',
            type: jn.type || 'LEFT',
            leftCol: jn.leftCol || '',
            rightCol: jn.rightCol || ''
          }))
        }
        rebuildAllColumns()
        if (j.fields) {
          for (const f of j.fields) {
            const col = allColumns.value.find(c => c.tableAlias === f.table && c.name === f.column)
            if (col) { col.selected = true; col.alias = f.alias; col.dictKey = f.dictKey || '' }
          }
        }
        if (j.conditions) conditions.value = j.conditions
        if (j.processRules) processRules.value = j.processRules
      } catch (e) {
        ElMessage.warning('configJson 解析失败:' + e.message)
      }
    }
  } catch {
    // request.js 已提示
  }
}

async function loadDictSystems() {
  try {
    // v0.3.5 · 拉 scope=2(接口开发侧)所有字典条目 · 前端聚合 systemCode → dictKeys · 避免多次网络调用
    const res = await listDictMappings({ scope: 2, page: 1, size: 500 })
    const records = res?.records || res || []
    const bySystem = new Map()
    for (const r of records) {
      const sys = r.systemCode || 'default'
      if (!bySystem.has(sys)) bySystem.set(sys, new Set())
      bySystem.get(sys).add(r.dictKey)
    }
    dictSystems.value = Array.from(bySystem.entries()).map(([systemCode, keys]) => ({
      systemCode,
      dictKeys: Array.from(keys).sort()
    }))
  } catch { /* ignore · 无字典系统不影响主流程 */ }
}

async function loadShardConfigs() {
  try {
    const res = await listShardConfigs('', 1, 200)
    shardConfigList.value = res?.records || res || []
  } catch { /* ignore */ }
}

function openFieldProcessPage() {
  window.open('/convert/field-process', '_blank')
}

function openShardConfigPage() {
  window.open('/interface/shard', '_blank')
}

// 状态变化时自动保存草稿
watch([form, mainTable, joinConfigs, conditions, allColumns], saveDraft, { deep: true })

// ─── 数据库/表联动 ─────────────────────────────────────────────────────────────

async function onDbChange(dbId) {
  if (!dbId) return
  tableList.value = []
  tableColumns.value = {}
  allColumns.value = []
  try {
    const tables = await getTableStructure(dbId)
    tableList.value = tables || []
    for (const t of tableList.value) {
      tableColumns.value[t.tableName] = t.columns || []
    }
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

function onMainTableChange(tableName) {
  mainTable.alias = tableName.substring(0, 1).toLowerCase()
  rebuildAllColumns()
}

function onJoinTableChange(idx, tableName) {
  joinConfigs.value[idx].rightAlias = tableName.substring(0, 1).toLowerCase() + idx
  rebuildAllColumns()
}

/** 获取关联表列 */
function getJoinTableColumns(tableName) {
  return tableName ? (tableColumns.value[tableName] || []) : []
}

/** 重新构建「所有列」列表（步骤2用） */
function rebuildAllColumns() {
  const prev = allColumns.value
  const newCols = []

  const addTable = (tableName, alias) => {
    for (const col of (tableColumns.value[tableName] || [])) {
      const existing = prev.find(p => p.tableAlias === alias && p.name === col.name)
      newCols.push({
        tableAlias: alias,
        name: col.name,
        type: col.type,
        selected: existing ? existing.selected : true,
        alias: existing ? existing.alias : col.name,
        dictKey: existing ? existing.dictKey : ''   // v0.3.5 · 字典键 · 空表示不使用
      })
    }
  }

  if (mainTable.name && mainTable.alias) addTable(mainTable.name, mainTable.alias)
  for (const join of joinConfigs.value) {
    if (join.rightTableName && join.rightAlias) addTable(join.rightTableName, join.rightAlias)
  }

  allColumns.value = newCols
}

// ─── JOIN 操作 ────────────────────────────────────────────────────────────────

function addJoin() {
  joinConfigs.value.push({
    rightTableName: '',
    rightAlias: '',
    type: 'LEFT',
    leftCol: '',
    rightCol: ''
  })
}

function removeJoin(idx) {
  joinConfigs.value.splice(idx, 1)
  rebuildAllColumns()
}

// ─── 步骤导航 ─────────────────────────────────────────────────────────────────

function nextStep() {
  if (step.value === 0) {
    if (!form.name.trim()) return ElMessage.warning('请填写接口名称')
    if (!form.dbConnectionId) return ElMessage.warning('请选择数据库连接')
    if (!mainTable.name) return ElMessage.warning('请选择主表')
    rebuildAllColumns()
  }
  if (step.value === 1) {
    const selected = allColumns.value.filter(c => c.selected)
    if (selected.length === 0) return ElMessage.warning('请至少勾选一个返回字段')
  }
  step.value++
}

function goList() {
  router.push('/interface/dev')
}

// ─── 预览 ─────────────────────────────────────────────────────────────────────

async function doPreview() {
  if (!savedId.value) {
    // 先保存再预览
    const ok = await doSave(true)
    if (!ok) return
  }
  previewing.value = true
  previewDone.value = false
  try {
    const result = await previewInterface(savedId.value, previewParams)
    previewResult.value = result || []
    previewDone.value = true
  } catch {
    // error已由 request.js 拦截器统一提示
  } finally {
    previewing.value = false
  }
}

// ─── 保存 ─────────────────────────────────────────────────────────────────────

async function doSave(silent = false) {
  saving.value = true
  try {
    const payload = buildPayload()
    const id = await saveInterface(payload)
    savedId.value = id
    clearDraft()
    if (!silent) {
      ElMessage.success('保存成功')
    }
    return true
  } catch {
    return false
  } finally {
    saving.value = false
  }
}

function buildPayload() {
  const tables = [{ name: mainTable.name, alias: mainTable.alias }]
  for (const join of joinConfigs.value) {
    if (join.rightTableName) tables.push({ name: join.rightTableName, alias: join.rightAlias })
  }

  const joins = joinConfigs.value
    .filter(j => j.rightTableName && j.leftCol && j.rightCol)
    .map(j => ({
      leftTable: mainTable.alias,
      leftCol: j.leftCol,
      rightTable: j.rightAlias,
      rightCol: j.rightCol,
      type: j.type
    }))

  const fields = allColumns.value
    .filter(c => c.selected)
    .map(c => ({
      table: c.tableAlias,
      column: c.name,
      alias: c.alias || c.name,
      dictKey: c.dictKey || undefined   // v0.3.5 · 字典键 · 空则忽略
    }))

  const configJson = JSON.stringify({
    tables,
    joins,
    fields,
    conditions: conditions.value,
    processRules: processRules.value   // v0.3.5 · 从 state 取
  })

  return {
    id: savedId.value || undefined,
    name: form.name,
    dbConnectionId: form.dbConnectionId,
    type: 'SELECT',
    configJson,
    responseFormat: form.responseFormat,
    responseHeaders: form.responseHeaders,
    shardConfigId: form.shardConfigId || undefined   // v0.3.5 · 分片配置
  }
}

// ─── 草稿持久化 ───────────────────────────────────────────────────────────────

function saveDraft() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      form: { ...form },
      mainTable: { ...mainTable },
      joinConfigs: joinConfigs.value,
      conditions: conditions.value
    }))
  } catch {
    // ignore
  }
}

function loadDraft() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const draft = JSON.parse(raw)
    if (draft.form) Object.assign(form, draft.form)
    if (draft.mainTable) Object.assign(mainTable, draft.mainTable)
    if (draft.joinConfigs) joinConfigs.value = draft.joinConfigs
    if (draft.conditions) conditions.value = draft.conditions
    if (form.dbConnectionId) onDbChange(form.dbConnectionId)
  } catch {
    // ignore corrupt draft
  }
}

function clearDraft() {
  localStorage.removeItem(STORAGE_KEY)
}
</script>

<style scoped>
.query-config-page {
  padding: 20px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
}
.join-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
