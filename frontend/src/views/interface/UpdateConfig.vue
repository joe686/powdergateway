<template>
  <div class="update-config-page">
    <div class="page-header">
      <h2>修改接口配置</h2>
      <el-button @click="router.push('/interface/dev')">返回列表</el-button>
    </div>

    <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="基本信息" />
      <el-step title="修改字段配置" />
      <el-step title="修改条件配置" />
      <el-step title="保存配置" />
    </el-steps>

    <!-- Step 1：基本信息 -->
    <div v-show="step === 0">
      <el-card>
        <template #header>Step 1 · 基本信息</template>
        <el-form label-width="120px" style="max-width: 600px">
          <el-form-item label="接口名称" required>
            <el-input v-model="form.name" placeholder="请输入接口名称" />
          </el-form-item>
          <el-form-item label="数据库连接" required>
            <el-select
              v-model="form.dbConnectionId"
              placeholder="请选择数据库连接"
              style="width: 100%"
              @change="onDbChange"
            >
              <el-option v-for="db in dbList" :key="db.id" :label="db.name" :value="db.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="step-footer">
          <el-button type="primary" :disabled="!form.name || !form.dbConnectionId" @click="step = 1">
            下一步
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Step 2：修改字段配置 -->
    <div v-show="step === 1">
      <el-card style="margin-bottom: 16px">
        <template #header>
          <span>Step 2 · 修改字段配置</span>
          <el-button
            style="float: right"
            size="small"
            type="primary"
            :disabled="tables.length >= 3"
            @click="addTable"
          >
            + 添加表（最多3张）
          </el-button>
        </template>

        <div v-for="(tbl, tIdx) in tables" :key="tIdx" class="table-block">
          <div class="table-block-header">
            <span class="table-block-title">表 {{ tIdx + 1 }}</span>
            <el-select
              v-model="tbl.tableName"
              placeholder="请选择目标表"
              filterable
              style="width: 260px; margin: 0 12px"
              @change="() => onTableSelect(tIdx)"
            >
              <el-option
                v-for="t in tableList"
                :key="t.tableName"
                :label="t.tableName + (t.comment ? ` (${t.comment})` : '')"
                :value="t.tableName"
              />
            </el-select>
            <el-button v-if="tables.length > 1" size="small" type="danger" plain @click="removeTable(tIdx)">
              删除
            </el-button>
          </div>

          <el-table :data="tbl.fields" border size="small" style="margin-top: 10px">
            <el-table-column label="字段名" width="200">
              <template #default="{ row }">
                <el-select
                  v-model="row.column"
                  filterable
                  allow-create
                  placeholder="选择或输入字段名"
                  size="small"
                  style="width: 100%"
                  @change="(v) => onColumnSelect(tIdx, row, v)"
                >
                  <el-option
                    v-for="col in (tableColumns[tbl.tableName] || [])"
                    :key="col.name"
                    :label="col.name"
                    :value="col.name"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">
                <span style="color: #909399; font-size: 12px">{{ row.columnType || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数据来源" width="150">
              <template #default="{ row }">
                <el-select v-model="row.sourceType" size="small" style="width: 100%">
                  <el-option label="请求字段" value="REQUEST" />
                  <el-option label="固定值" value="CONST" />
                  <el-option label="运算表达式" value="CALC" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="值配置">
              <template #default="{ row }">
                <el-input v-if="row.sourceType === 'REQUEST'" v-model="row.paramKey" size="small" placeholder="请求参数名" />
                <el-input v-else-if="row.sourceType === 'CONST'" v-model="row.constValue" size="small" placeholder="固定值" />
                <el-input v-else-if="row.sourceType === 'CALC'" v-model="row.expression" size="small" placeholder="四则运算表达式" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button size="small" type="danger" link @click="removeField(tIdx, $index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-button size="small" style="margin-top: 8px" @click="addField(tIdx)">+ 添加字段</el-button>
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :disabled="!canProceedStep2" @click="step = 2">下一步</el-button>
      </div>
    </div>

    <!-- Step 3：修改条件配置 -->
    <div v-show="step === 2">
      <el-card>
        <template #header>
          Step 3 · 修改条件配置
          <span style="font-size: 12px; color: #909399; margin-left: 8px">★ = 主键或唯一索引，至少需要一个</span>
        </template>

        <ConditionBuilder
          v-model="conditions"
          :field-options="allFieldOptions"
          :highlight-primary-keys="true"
          :show-table-column="true"
          :table-options="selectedTableNames"
        />

        <div v-if="conditionError" style="color: #f56c6c; margin-top: 8px; font-size: 13px">
          {{ conditionError }}
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 1">上一步</el-button>
        <el-button type="primary" :disabled="conditions.length === 0" @click="validateAndNext">下一步</el-button>
      </div>
    </div>

    <!-- Step 4：保存配置 -->
    <div v-show="step === 3">
      <el-card>
        <template #header>Step 4 · 保存配置</template>
        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="接口名称">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="接口类型">UPDATE（修改）</el-descriptions-item>
          <el-descriptions-item label="修改表数量">{{ tables.length }} 张</el-descriptions-item>
          <el-descriptions-item label="条件数量">{{ conditions.length }} 个</el-descriptions-item>
        </el-descriptions>

        <el-collapse style="margin-bottom: 16px">
          <el-collapse-item title="查看 config_json">
            <pre style="background:#f5f5f5;padding:12px;border-radius:4px;font-size:12px;overflow:auto">{{ configJsonPreview }}</pre>
          </el-collapse-item>
        </el-collapse>

        <div class="step-footer">
          <el-button @click="step = 2">上一步</el-button>
          <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ConditionBuilder from '@/components/ConditionBuilder.vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { saveInterface } from '@/api/interface'

const router = useRouter()
const step = ref(0)

const dbList       = ref([])
const tableList    = ref([])
const tableColumns = ref({})

const form       = ref({ name: '', dbConnectionId: null })
const tables     = ref([{ tableName: '', fields: [newField()] }])
const conditions = ref([])
const conditionError = ref('')
const saving     = ref(false)

function newField() {
  return { column: '', columnType: '', sourceType: 'REQUEST', paramKey: '', constValue: '', expression: '' }
}

function addTable()           { if (tables.value.length < 3) tables.value.push({ tableName: '', fields: [newField()] }) }
function removeTable(idx)     { tables.value.splice(idx, 1) }
function addField(tIdx)       { tables.value[tIdx].fields.push(newField()) }
function removeField(tIdx, i) { tables.value[tIdx].fields.splice(i, 1) }

const selectedTableNames = computed(() => tables.value.map(t => t.tableName).filter(Boolean))

const allFieldOptions = computed(() => {
  const opts = []
  for (const tbl of tables.value) {
    if (!tbl.tableName) continue
    for (const col of (tableColumns.value[tbl.tableName] || [])) {
      opts.push({ label: col.name, value: col.name, isPrimary: col.isPrimary, isUnique: col.isUnique })
    }
  }
  return opts
})

async function onDbChange(dbId) {
  tableList.value = []
  tableColumns.value = {}
  tables.value.forEach(t => { t.tableName = ''; t.fields = [newField()] })
  conditions.value = []
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    tableList.value = list || []
    tableList.value.forEach(t => { tableColumns.value[t.tableName] = t.columns || [] })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

function onTableSelect(tIdx) { tables.value[tIdx].fields = [newField()] }

function onColumnSelect(tIdx, row, colName) {
  const cols = tableColumns.value[tables.value[tIdx].tableName] || []
  const meta = cols.find(c => c.name === colName)
  row.columnType = meta ? meta.type : ''
}

const canProceedStep2 = computed(() =>
  tables.value.every(t => t.tableName && t.fields.length > 0 && t.fields.every(f => f.column && f.sourceType))
)

function validateAndNext() {
  conditionError.value = ''
  if (conditions.value.length === 0) {
    conditionError.value = '请至少添加一个 WHERE 条件'
    return
  }
  const hasUniqueKey = conditions.value.some(cond => {
    const cols = tableColumns.value[cond.tableName] || []
    const col = cols.find(c => c.name === cond.field)
    return col && (col.isPrimary || col.isUnique)
  })
  if (!hasUniqueKey) {
    conditionError.value = '条件中必须包含至少一个主键（★）或唯一索引（★）字段'
    return
  }
  step.value = 3
}

const configJsonPreview = computed(() => JSON.stringify(buildConfigJson(), null, 2))

function buildConfigJson() {
  return {
    tables: tables.value.map(t => ({
      tableName: t.tableName,
      fields: t.fields.filter(f => f.column).map(f => {
        const field = { column: f.column, sourceType: f.sourceType }
        if (f.sourceType === 'REQUEST') field.paramKey   = f.paramKey
        if (f.sourceType === 'CONST')   field.constValue = f.constValue
        if (f.sourceType === 'CALC')    field.expression = f.expression
        return field
      })
    })),
    conditions: conditions.value.map(c => ({
      tableName: c.tableName, field: c.field, op: c.op, paramKey: c.paramKey
    }))
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await saveInterface({
      name: form.value.name,
      dbConnectionId: form.value.dbConnectionId,
      type: 'UPDATE',
      configJson: JSON.stringify(buildConfigJson())
    })
    ElMessage.success('保存成功')
    router.push('/interface/dev')
  } catch {
    // request.js 拦截器已处理报错提示
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try { dbList.value = (await listConnections()) || [] } catch { ElMessage.error('加载数据库连接失败') }
})
</script>

<style scoped>
.update-config-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.table-block { padding: 12px; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 16px; }
.table-block-header { display: flex; align-items: center; margin-bottom: 6px; }
.table-block-title { font-weight: 600; color: #303133; }
.step-footer { margin-top: 20px; display: flex; gap: 12px; }
</style>
