<template>
  <div class="delete-config-page">
    <div class="page-header">
      <h2>{{ pageTitle }}</h2>
      <el-button @click="router.push('/interface/dev')">返回列表</el-button>
    </div>

    <!-- v0.3.5 · 3 → 4 步 · 加分库分表(delete 无字段加工/字典键 · 场景无意义) -->
    <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="基本信息" />
      <el-step title="多表条件配置" />
      <el-step title="分库分表(可选)" />
      <el-step title="预览与保存" />
    </el-steps>

    <!-- Step 1：基本信息 -->
    <div v-show="step === 0">
      <el-card>
        <template #header>Step 1 · 基本信息</template>
        <el-form label-width="140px" style="max-width: 600px">
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
          <el-form-item label="允许批量删除">
            <el-switch v-model="form.allowBatchDelete" :active-value="1" :inactive-value="0" />
            <el-text v-if="form.allowBatchDelete === 0" type="warning" style="margin-left: 12px; font-size: 12px">
              关闭后单次删除超过1条将被拒绝
            </el-text>
            <el-text v-else type="danger" style="margin-left: 12px; font-size: 12px">
              ⚠ 已开启批量删除，请谨慎操作
            </el-text>
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
          <el-button
            type="primary"
            :disabled="!form.name || !form.dbConnectionId"
            @click="step = 1"
          >
            下一步
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- Step 2：多表条件配置 -->
    <div v-show="step === 1">
      <el-card>
        <template #header>
          <span>Step 2 · 多表条件配置</span>
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

          <div style="margin-top: 10px; padding-left: 8px">
            <ConditionBuilder
              v-model="tbl.conditions"
              :field-options="tableFieldOptions(tbl.tableName)"
            />
          </div>
        </div>
      </el-card>

      <div class="step-footer">
        <el-button @click="step = 0">上一步</el-button>
        <el-button type="primary" :disabled="!canProceedStep2" @click="step = 2">下一步</el-button>
      </div>
    </div>

    <!-- v0.3.5 · Step 3:分库分表(可选) -->
    <div v-show="step === 2">
      <el-card>
        <template #header>Step 3 · 分库分表(可选)</template>
        <el-alert type="info" :closable="false" style="margin-bottom:12px">
          M2-8 分片配置 · <el-button type="primary" link @click="openShardConfigPage">新建分片</el-button>
        </el-alert>
        <el-form label-width="140px">
          <el-form-item label="分片配置">
            <el-select v-model="form.shardConfigId" placeholder="不启用" clearable filterable style="width:400px">
              <el-option v-for="sc in shardConfigList" :key="sc.id" :label="sc.name + ' · ' + (sc.moduleName || '')" :value="sc.id" />
            </el-select>
            <el-button link @click="loadShardConfigs" style="margin-left:8px">刷新</el-button>
          </el-form-item>
        </el-form>
        <div class="step-footer">
          <el-button @click="step = 1">上一步</el-button>
          <el-button type="primary" @click="step = 3">下一步</el-button>
        </div>
      </el-card>
    </div>

    <!-- Step 4：预览与保存 -->
    <div v-show="step === 3">
      <el-card>
        <template #header>Step 4 · 预览与保存</template>

        <el-descriptions :column="1" border style="margin-bottom: 16px">
          <el-descriptions-item label="接口名称">{{ form.name }}</el-descriptions-item>
          <el-descriptions-item label="接口类型">DELETE（删除）</el-descriptions-item>
          <el-descriptions-item label="目标表数量">{{ tables.length }} 张</el-descriptions-item>
          <el-descriptions-item label="允许批量删除">
            <el-tag :type="form.allowBatchDelete ? 'danger' : 'success'">
              {{ form.allowBatchDelete ? '是（危险）' : '否（安全）' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse style="margin-bottom: 16px">
          <el-collapse-item title="查看 config_json">
            <pre style="background:#f5f5f5;padding:12px;border-radius:4px;font-size:12px;overflow:auto">{{ configJsonPreview }}</pre>
          </el-collapse-item>
        </el-collapse>

        <div style="margin-bottom: 16px">
          <el-button :loading="previewing" @click="doPreview">预览待删数据</el-button>
          <span style="font-size: 12px; color: #909399; margin-left: 8px">（需先保存接口才可预览）</span>
        </div>

        <div class="step-footer">
          <el-button @click="step = 2">上一步</el-button>
          <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
        </div>
      </el-card>
    </div>

    <!-- 预览弹窗 -->
    <el-dialog v-model="previewVisible" title="待删数据预览（前10条）" width="80%">
      <el-empty v-if="Object.keys(previewData).length === 0" description="暂无匹配数据" />
      <el-tabs v-else>
        <el-tab-pane
          v-for="(rows, tableName) in previewData"
          :key="tableName"
          :label="tableName + ' (' + rows.length + ' 条)'"
        >
          <el-table :data="rows" border size="small" max-height="400">
            <el-table-column
              v-for="col in tablePreviewColumns(rows)"
              :key="col"
              :label="col"
              :prop="col"
              min-width="120"
            />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import ConditionBuilder from '@/components/ConditionBuilder.vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import { saveInterface, deletePreview, getInterface } from '@/api/interface'
import { listShardConfigs } from '@/api/shardConfig'
import ResponseHeadersEditor from '@/components/interface/ResponseHeadersEditor.vue'

const router = useRouter()
const route  = useRoute()
const step = ref(0)

const dbList       = ref([])
const tableList    = ref([])
const tableColumns = ref({})

const form   = ref({ name: '', dbConnectionId: null, allowBatchDelete: 0, responseFormat: 'JSON', responseHeaders: '', shardConfigId: null })
const tables = ref([{ tableName: '', conditions: [] }])

const saving         = ref(false)
const previewing     = ref(false)
const previewVisible = ref(false)
const previewData    = ref({})
const savedId        = ref(route.query.id ? Number(route.query.id) : null)

// v0.3.5 · edit + 分片
const editMode = computed(() => !!savedId.value)
const pageTitle = computed(() => editMode.value
    ? `编辑删除接口 #${savedId.value} · ${form.value.name || '(未命名)'}`
    : '删除接口配置')
const shardConfigList = ref([])

function addTable()       { if (tables.value.length < 3) tables.value.push({ tableName: '', conditions: [] }) }
function removeTable(idx) { tables.value.splice(idx, 1) }

function tableFieldOptions(tableName) {
  return (tableColumns.value[tableName] || []).map(col => ({
    label: col.name,
    value: col.name,
    isPrimary: col.isPrimary,
    isUnique: col.isUnique
  }))
}

function tablePreviewColumns(rows) {
  if (!rows || rows.length === 0) return []
  return Object.keys(rows[0])
}

const canProceedStep2 = computed(() =>
  tables.value.every(t => t.tableName && t.conditions.length > 0)
)

async function onDbChange(dbId) {
  tableList.value = []
  tableColumns.value = {}
  tables.value.forEach(t => { t.tableName = ''; t.conditions = [] })
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    tableList.value = list || []
    tableList.value.forEach(t => { tableColumns.value[t.tableName] = t.columns || [] })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

const configJsonPreview = computed(() => JSON.stringify(buildConfigJson(), null, 2))

function buildConfigJson() {
  return {
    tables: tables.value.map(t => ({
      tableName: t.tableName,
      conditions: t.conditions.map(c => ({ field: c.field, op: c.op, paramKey: c.paramKey }))
    }))
  }
}

async function saveConfig() {
  saving.value = true
  try {
    const id = await saveInterface({
      id: savedId.value || undefined,
      name: form.value.name,
      dbConnectionId: form.value.dbConnectionId,
      type: 'DELETE',
      allowBatchDelete: form.value.allowBatchDelete,
      configJson: JSON.stringify(buildConfigJson()),
      responseFormat: form.value.responseFormat,
      responseHeaders: form.value.responseHeaders,
      shardConfigId: form.value.shardConfigId || undefined   // v0.3.5
    })
    savedId.value = id
    ElMessage.success('保存成功，可点击「预览待删数据」测试')
  } catch { /* request.js 已处理 */ } finally { saving.value = false }
}

// v0.3.5 · edit 回填
async function loadForEdit(id) {
  try {
    const cfg = await getInterface(id)
    form.value.name = cfg.name || ''
    form.value.dbConnectionId = cfg.dbConnectionId || null
    form.value.allowBatchDelete = cfg.allowBatchDelete || 0
    form.value.responseFormat = cfg.responseFormat || 'JSON'
    form.value.responseHeaders = cfg.responseHeaders || ''
    form.value.shardConfigId = cfg.shardConfigId || null
    if (form.value.dbConnectionId) await onDbChange(form.value.dbConnectionId)
    if (cfg.configJson) {
      try {
        const j = JSON.parse(cfg.configJson)
        if (j.tables && j.tables.length) {
          tables.value = j.tables.map(t => ({
            tableName: t.tableName,
            conditions: (t.conditions || []).map(c => ({
              field: c.field, op: c.op, paramKey: c.paramKey, tableName: t.tableName
            }))
          }))
        }
      } catch (e) { ElMessage.warning('configJson 解析失败:' + e.message) }
    }
  } catch { /* request.js 已提示 */ }
}

async function loadShardConfigs() {
  try { const res = await listShardConfigs('', 1, 200); shardConfigList.value = res?.records || res || [] }
  catch { /* ignore */ }
}

function openShardConfigPage() { window.open('/interface/shard', '_blank') }

async function doPreview() {
  if (!savedId.value) {
    ElMessage.warning('请先点击「保存」后再预览')
    return
  }
  previewing.value = true
  try {
    const params = {}
    tables.value.forEach(t => {
      t.conditions.forEach(c => { if (c.paramKey) params[c.paramKey] = null })
    })
    const data = await deletePreview(savedId.value, params)
    previewData.value = data || {}
    previewVisible.value = true
  } catch {
    ElMessage.error('预览失败')
  } finally {
    previewing.value = false
  }
}

onMounted(async () => {
  try { dbList.value = (await listConnections()) || [] } catch { ElMessage.error('加载数据库连接失败') }
  loadShardConfigs()
  if (savedId.value) await loadForEdit(savedId.value)
})
</script>

<style scoped>
.delete-config-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.table-block { padding: 12px; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 16px; }
.table-block-header { display: flex; align-items: center; margin-bottom: 6px; }
.table-block-title { font-weight: 600; color: #303133; }
.step-footer { margin-top: 20px; display: flex; gap: 12px; }
</style>
