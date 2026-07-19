<template>
  <div>
    <!-- ① 接口类型 -->
    <div v-show="props.isActive('type')">
      <el-radio-group
        v-model="wizard.interfaceType"
        size="large"
        style="display:flex;gap:20px;flex-wrap:wrap"
        @change="onTypeChange"
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
    <div v-show="props.isActive('db')">
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

    <!-- ③ 选表结构 -->
    <div v-show="props.isActive('tables')">
      <template v-if="wizard.interfaceType === 'SELECT'">
        <el-form label-width="80px">
          <el-form-item label="主表" required>
            <el-select v-model="wizard.mainTable.name" placeholder="请选择主表" filterable style="width:220px" @change="onMainTableChange">
              <el-option v-for="t in tableList" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
            </el-select>
            <span style="margin:0 8px;color:#606266">别名</span>
            <el-input v-model="wizard.mainTable.alias" style="width:80px" placeholder="如 a" />
          </el-form-item>
          <el-form-item label="关联表">
            <div v-for="(join, idx) in wizard.joinConfigs" :key="idx" class="join-row">
              <el-select v-model="join.rightTableName" placeholder="关联表" filterable style="width:160px" @change="v => onJoinTableChange(idx, v)">
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
                <el-option v-for="col in (wizard.tableColumns[wizard.mainTable.name] || [])" :key="col.name" :label="wizard.mainTable.alias + '.' + col.name" :value="col.name" />
              </el-select>
              <span>=</span>
              <el-select v-model="join.rightCol" placeholder="右表字段" filterable style="width:140px;margin:0 6px">
                <el-option v-for="col in (wizard.tableColumns[join.rightTableName] || [])" :key="col.name" :label="join.rightAlias + '.' + col.name" :value="col.name" />
              </el-select>
              <el-button type="danger" link @click="removeJoin(idx)">删除</el-button>
            </div>
            <el-button plain size="small" @click="addJoin">+ 添加关联表</el-button>
          </el-form-item>
        </el-form>
      </template>
      <template v-else-if="wizard.interfaceType === 'INSERT'">
        <div v-for="(tbl, idx) in wizard.tables" :key="idx" class="table-block" style="margin-bottom:12px">
          <div style="display:flex;align-items:center;margin-bottom:8px">
            <span style="font-weight:600;margin-right:12px">表 {{ idx + 1 }}</span>
            <el-select v-model="tbl.tableName" placeholder="请选择目标表" filterable style="width:260px" @change="v => onTargetTableChange(idx, v)">
              <el-option v-for="t in tableList" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
            </el-select>
            <el-button v-if="wizard.tables.length > 1" type="danger" plain size="small" style="margin-left:8px" @click="removeTargetTable(idx)">删除</el-button>
          </div>
        </div>
        <el-button :disabled="wizard.tables.length >= 3" size="small" @click="addTargetTable">+ 添加表（最多3张）</el-button>
      </template>
      <template v-else>
        <el-form label-width="100px" style="max-width:500px">
          <!-- CHG-011 E2E-6 铁律：v-if="wizard.tables[0]" 保护 + ?.tableName 双重保障 -->
          <el-form-item v-if="wizard.tables[0]" label="目标表" required>
            <el-select v-model="wizard.tables[0].tableName" placeholder="请选择目标表" filterable style="width:260px" @change="v => onTargetTableChange(0, v)">
              <el-option v-for="t in tableList" :key="t.tableName" :label="t.tableName" :value="t.tableName" />
            </el-select>
          </el-form-item>
          <div v-else style="color:#E6A23C">请选择目标表</div>
        </el-form>
      </template>
    </div>

    <!-- ④ 字段配置（DELETE 不显示） -->
    <div v-show="props.isActive('fields')">
      <template v-if="wizard.interfaceType === 'SELECT'">
        <el-table :data="wizard.selectedColumns" border size="small">
          <el-table-column label="表别名" width="100" prop="tableAlias" />
          <el-table-column label="字段名" prop="name" />
          <el-table-column label="类型" prop="type" width="100" />
          <el-table-column label="选中" width="70" align="center">
            <template #default="{ row }"><el-checkbox v-model="row.selected" /></template>
          </el-table-column>
          <el-table-column label="输出别名" min-width="160">
            <template #default="{ row }">
              <el-input v-if="row.selected" v-model="row.alias" size="small" placeholder="自定义列名" />
              <span v-else style="color:#999">—</span>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="wizard.selectedColumns.length === 0" style="color:#E6A23C;margin-top:8px">请先在步骤③中选择主表</div>
      </template>
      <template v-else>
        <div v-for="(tbl, tIdx) in wizard.fieldTables" :key="tIdx" style="margin-bottom:20px">
          <div style="font-weight:600;margin-bottom:8px">表：{{ tbl.tableName || '（未选表）' }}</div>
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
                <el-input v-if="row.sourceType === 'REQUEST'" v-model="row.paramKey" size="small" placeholder="请求参数名，如 userId" />
                <el-input v-else-if="row.sourceType === 'CONST'" v-model="row.constValue" size="small" placeholder="固定值，如 active" />
                <el-input v-else v-model="row.expression" size="small" placeholder="四则运算，如 price * qty" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button size="small" type="danger" link @click="tbl.fields.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" style="margin-top:8px" @click="tbl.fields.push({ column: '', columnType: '', sourceType: 'REQUEST', paramKey: '', constValue: '', expression: '' })">+ 添加字段</el-button>
        </div>
        <div v-if="wizard.fieldTables.length === 0" style="color:#E6A23C">请先在步骤③中选择目标表</div>
      </template>
    </div>

    <!-- ⑤ 分库分表（可选） -->
    <div v-show="props.isActive('shard')">
      <p style="color:#606266;margin-bottom:16px">如果此接口需要分库分表路由，请选择对应的分片规则；否则可直接跳过。</p>
      <el-form label-width="120px" style="max-width:500px">
        <el-form-item label="分片规则">
          <el-select v-model="wizard.shardConfigId" placeholder="选择分片规则（可为空）" clearable style="width:280px">
            <el-option v-for="s in shardList" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-button type="primary" plain @click="skipStep" style="margin-top:8px">跳过，直接下一步</el-button>
    </div>

    <!-- ⑥ 字段加工（可选，DELETE 不显示） -->
    <div v-show="props.isActive('process')">
      <p style="color:#606266;margin-bottom:16px">配置字段加工规则（如截位、补位、大小写转换），不需要可直接跳过。</p>
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
          <template #default="{ row }"><el-input v-model="row.field" size="small" placeholder="字段名" /></template>
        </el-table-column>
        <el-table-column label="参数" min-width="200">
          <template #default="{ row }"><el-input v-model="row.params" size="small" placeholder="如：length=10,padChar=0" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button size="small" type="danger" link @click="wizard.processRules.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;gap:12px">
        <el-button size="small" @click="wizard.processRules.push({ type: 'TRIM', field: '', params: '' })">+ 添加规则</el-button>
        <el-button type="primary" plain @click="skipStep">跳过，直接下一步</el-button>
      </div>
    </div>

    <!-- ⑦ 条件配置（INSERT 不显示） -->
    <div v-show="props.isActive('cond')">
      <p v-if="wizard.interfaceType === 'SELECT'" style="color:#909399;margin-bottom:12px">可选：配置查询条件，不需要可直接跳过</p>
      <p v-else style="color:#E6A23C;margin-bottom:12px">必填：UPDATE/DELETE 接口必须配置至少一个条件</p>
      <ConditionBuilder v-model="wizard.conditions" :field-options="conditionFieldOptions" />
      <div v-if="wizard.interfaceType === 'SELECT'" style="margin-top:16px">
        <el-button type="primary" plain @click="skipStep">跳过，直接下一步</el-button>
      </div>
    </div>

    <!-- ⑧ 日志开关 -->
    <div v-show="props.isActive('log')">
      <el-form label-width="120px" style="max-width:500px">
        <el-form-item label="操作日志">
          <el-switch v-model="wizard.logEnabled" active-text="开启" inactive-text="关闭" />
          <div style="color:#909399;font-size:12px;margin-top:6px">开启后，每次接口调用都将记录到操作日志（审计用）</div>
        </el-form-item>
      </el-form>
    </div>

    <!-- ⑨ 预览测试 -->
    <div v-show="props.isActive('preview')">
      <el-alert
        v-if="wizard.interfaceType === 'DELETE'"
        title="以下将预览待删除数据，执行预览不会真正删除"
        type="warning"
        :closable="false"
        style="margin-bottom:16px"
      />
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

    <!-- ⑩ 保存发布 -->
    <div v-show="props.isActive('publish')">
      <el-form label-width="120px" style="max-width:600px">
        <el-form-item label="接口名称" required>
          <el-input v-model="wizard.interfaceName" placeholder="请输入接口名称" style="width:300px" />
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
        <el-button type="primary" :loading="publishing" @click="doPublish">保存并发布</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useWizardStore } from '@/store/wizard'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { listShardConfigs } from '@/api/shardConfig'
import { saveInterface, previewInterface, deletePreview, publishInterface } from '@/api/interface'
import ConditionBuilder from '@/components/ConditionBuilder.vue'

const props = defineProps({
  isActive: { type: Function, required: true }
})

const router = useRouter()
const wizard = useWizardStore()
const dbList = ref([])
const shardList = ref([])
const previewing = ref(false)
const previewDone = ref(false)
const saving = ref(false)
const publishing = ref(false)

const previewColumns = computed(() =>
  wizard.previewResult.length ? Object.keys(wizard.previewResult[0]) : []
)

// ─── 步骤导航辅助（步骤推进由父容器 WizardShell 负责） ──────────────────
const visibleStepsDummy = computed(() => []) // 占位，skipStep 用 wizard.currentStep

function skipStep() {
  wizard.currentStep++
}

// ─── 步骤校验（供 WizardShell.validateNext 回调） ─────────────────────────
function validateStep(key) {
  if (key === 'type') {
    if (!wizard.interfaceType) return '请选择接口类型'
  } else if (key === 'db') {
    if (!wizard.interfaceName.trim()) return '请填写接口名称'
    if (!wizard.dbConnectionId) return '请选择数据库连接'
  } else if (key === 'tables') {
    if (wizard.interfaceType === 'SELECT') {
      if (!wizard.mainTable.name) return '请选择主表'
    } else {
      // CHG-011 E2E-6：?.tableName 防空引用
      if (!wizard.tables[0]?.tableName) return '请选择目标表'
    }
  } else if (key === 'fields') {
    if (wizard.interfaceType === 'SELECT') {
      if (!wizard.selectedColumns.some(c => c.selected)) return '请至少勾选一个返回字段'
    } else {
      for (const tbl of wizard.fieldTables) {
        if (tbl.fields.length === 0) return `表 ${tbl.tableName} 尚未配置任何字段`
        for (const f of tbl.fields) {
          if (!f.column) return `表 ${tbl.tableName} 有字段名为空`
        }
      }
    }
  } else if (key === 'cond') {
    if (wizard.interfaceType === 'UPDATE' || wizard.interfaceType === 'DELETE') {
      if (wizard.conditions.length === 0) return 'UPDATE/DELETE 必须配置至少一个条件'
    }
  }
  return true
}

// ─── 初始化（不含草稿恢复，由父容器 InterfaceWizard.vue 的 useDraft 处理） ──
onMounted(async () => {
  try {
    dbList.value = await listConnections() || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
  try {
    const res = await listShardConfigs()
    shardList.value = res?.records || (Array.isArray(res) ? res : [])
  } catch {}
})

// ─── 类型切换 ──────────────────────────────────────────────────────────────
function onTypeChange(type) {
  wizard.currentStep = 0
  wizard.mainTable = { name: '', alias: '' }
  wizard.joinConfigs = []
  wizard.tables = []
  wizard.selectedColumns = []
  wizard.fieldTables = []
  wizard.conditions = []
  wizard.shardConfigId = null
  wizard.processRules = []
  if (type === 'DELETE') {
    wizard.tables = [{ tableName: '' }]
  }
  if (type === 'INSERT' || type === 'UPDATE') {
    wizard.tables = [{ tableName: '' }]
    wizard.fieldTables = [{ tableName: '', fields: [] }]
  }
}

// ─── 数据库切换 ────────────────────────────────────────────────────────────
async function onDbChange(dbId) {
  wizard.tableColumns = {}
  wizard.mainTable = { name: '', alias: '' }
  wizard.joinConfigs = []
  if (wizard.interfaceType === 'SELECT') {
    wizard.tables = []
  } else {
    wizard.tables = [{ tableName: '' }]
  }
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

// ─── 步骤③ 辅助函数 ───────────────────────────────────────────────────────
const tableList = computed(() =>
  Object.keys(wizard.tableColumns).map(name => ({ tableName: name }))
)

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

function onTargetTableChange(idx, tableName) {
  if (wizard.interfaceType === 'INSERT' || wizard.interfaceType === 'UPDATE') {
    const cols = wizard.tableColumns[tableName] || []
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

// ─── 步骤⑦ 条件字段选项 ───────────────────────────────────────────────────
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
    // CHG-011 E2E-6：?.tableName 防空引用
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

// ─── 步骤⑨⑩ 预览 / 保存 / 发布 ─────────────────────────────────────────
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

async function onSubmit() {
  await doPublish()
}

defineExpose({ validateStep, buildPayload, onSubmit })
</script>

<style scoped>
.join-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
