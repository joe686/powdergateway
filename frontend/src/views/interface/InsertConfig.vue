<template>
  <div class="insert-config-page">
    <div class="page-header">
      <h2>插入接口配置</h2>
      <el-button @click="router.push('/interface/dev')">返回列表</el-button>
    </div>

    <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="基本信息" />
      <el-step title="配置插入表" />
      <el-step title="保存配置" />
    </el-steps>

    <!-- ─── Step 1：基本信息 ─────────────────────────────── -->
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
        </el-form>
        <div class="step-footer">
          <el-button type="primary" :disabled="!form.name || !form.dbConnectionId" @click="step = 1">
            下一步
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- ─── Step 2：配置插入表 ────────────────────────────── -->
    <div v-show="step === 1">
      <el-card style="margin-bottom: 16px">
        <template #header>
          <span>Step 2 · 配置插入表</span>
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
              @change="(v) => onTableSelect(tIdx, v)"
            >
              <el-option
                v-for="t in tableList"
                :key="t.tableName"
                :label="t.tableName + (t.comment ? ` (${t.comment})` : '')"
                :value="t.tableName"
              />
            </el-select>
            <el-button
              v-if="tables.length > 1"
              size="small"
              type="danger"
              plain
              @click="removeTable(tIdx)"
            >
              删除
            </el-button>
          </div>

          <!-- 字段配置表 -->
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
                  v-else-if="row.sourceType === 'CALC'"
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
                  @click="removeField(tIdx, $index)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-button size="small" style="margin-top: 8px" @click="addField(tIdx)">
            + 添加字段
          </el-button>
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :disabled="!canProceedStep2" @click="step = 2">下一步</el-button>
      </div>
    </div>

    <!-- ─── Step 3：保存配置 ───────────────────────────────── -->
    <div v-show="step === 2">
      <el-card>
        <template #header>Step 3 · 保存配置</template>
        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="接口名称">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="接口类型">INSERT（插入）</el-descriptions-item>
          <el-descriptions-item label="插入表数量">{{ tables.length }} 张</el-descriptions-item>
          <el-descriptions-item
            v-for="(tbl, i) in tables"
            :key="i"
            :label="`表 ${i + 1}`"
          >
            {{ tbl.tableName }}（{{ tbl.fields.length }} 个字段）
          </el-descriptions-item>
        </el-descriptions>

        <!-- 配置 JSON 预览（折叠） -->
        <el-collapse style="margin-bottom: 16px">
          <el-collapse-item title="查看 config_json">
            <pre style="background:#f5f5f5; padding:12px; border-radius:4px; font-size:12px; overflow:auto">{{ configJsonPreview }}</pre>
          </el-collapse-item>
        </el-collapse>

        <div class="step-footer">
          <el-button @click="step = 1">上一步</el-button>
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
import { QuestionFilled } from '@element-plus/icons-vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { saveInterface } from '@/api/interface'
import ResponseHeadersEditor from '@/components/interface/ResponseHeadersEditor.vue'

const router = useRouter()
const step  = ref(0)

// ─── 基础数据 ─────────────────────────────────────────────────
const dbList    = ref([])
const tableList = ref([])
const tableColumns = ref({})  // tableName → ColumnMeta[]

// ─── 表单 ────────────────────────────────────────────────────
const form = ref({ name: '', dbConnectionId: null, responseFormat: 'JSON', responseHeaders: '' })

// ─── 多表配置 ─────────────────────────────────────────────────
const tables = ref([
  { tableName: '', fields: [newField()] }
])

function newField() {
  return { column: '', columnType: '', sourceType: 'REQUEST', paramKey: '', constValue: '', expression: '' }
}

function addTable() {
  if (tables.value.length < 3) {
    tables.value.push({ tableName: '', fields: [newField()] })
  }
}

function removeTable(idx) {
  tables.value.splice(idx, 1)
}

function addField(tIdx) {
  tables.value[tIdx].fields.push(newField())
}

function removeField(tIdx, fIdx) {
  tables.value[tIdx].fields.splice(fIdx, 1)
}

// ─── 数据库/表切换 ────────────────────────────────────────────
async function onDbChange(dbId) {
  tableList.value = []
  tableColumns.value = {}
  tables.value.forEach(t => { t.tableName = ''; t.fields = [newField()] })
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    tableList.value = list || []
    // 预加载所有表的列信息
    tableList.value.forEach(t => {
      tableColumns.value[t.tableName] = t.columns || []
    })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

function onTableSelect(tIdx, tableName) {
  // 重置字段列表
  tables.value[tIdx].fields = [newField()]
}

function onColumnSelect(tIdx, row, colName) {
  const tableName = tables.value[tIdx].tableName
  const cols = tableColumns.value[tableName] || []
  const meta = cols.find(c => c.name === colName)
  row.columnType = meta ? meta.type : ''
}

// ─── 校验 ─────────────────────────────────────────────────────
const canProceedStep2 = computed(() => {
  return tables.value.every(t => t.tableName && t.fields.length > 0 &&
    t.fields.every(f => f.column && f.sourceType))
})

// ─── 生成 config_json ─────────────────────────────────────────
const configJsonPreview = computed(() => {
  const config = buildConfigJson()
  return JSON.stringify(config, null, 2)
})

function buildConfigJson() {
  return {
    tables: tables.value.map(t => ({
      tableName: t.tableName,
      fields: t.fields
        .filter(f => f.column)
        .map(f => {
          const field = { column: f.column, sourceType: f.sourceType }
          if (f.sourceType === 'REQUEST') field.paramKey   = f.paramKey
          if (f.sourceType === 'CONST')   field.constValue = f.constValue
          if (f.sourceType === 'CALC')    field.expression = f.expression
          return field
        })
    }))
  }
}

// ─── 保存 ─────────────────────────────────────────────────────
const saving = ref(false)

async function saveConfig() {
  saving.value = true
  try {
    const data = {
      name:           form.value.name,
      dbConnectionId: form.value.dbConnectionId,
      type:           'INSERT',
      configJson:     JSON.stringify(buildConfigJson()),
      responseFormat: form.value.responseFormat,
      responseHeaders: form.value.responseHeaders
    }
    await saveInterface(data)
    ElMessage.success('保存成功')
    router.push('/interface/dev')
  } catch {
    // request.js 拦截器已处理报错提示
  } finally {
    saving.value = false
  }
}

// ─── 初始化 ───────────────────────────────────────────────────
onMounted(async () => {
  try {
    const res = await listConnections()
    dbList.value = res || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
})
</script>

<style scoped>
.insert-config-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h2 {
  margin: 0;
}
.table-block {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  margin-bottom: 16px;
}
.table-block-header {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}
.table-block-title {
  font-weight: 600;
  color: #303133;
}
.step-footer {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>
