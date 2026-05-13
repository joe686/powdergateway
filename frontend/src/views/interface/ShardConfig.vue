<template>
  <div class="shard-config">
    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input v-model="searchName" placeholder="搜索配置名称" clearable
        style="width: 240px" @keyup.enter="loadList" @clear="loadList" />
      <el-button type="primary" @click="loadList">查询</el-button>
      <el-button type="success" @click="openForm(null)">新建</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="list" stripe border v-loading="loading" style="margin-top:16px">
      <el-table-column prop="name" label="配置名称" min-width="160" />
      <el-table-column prop="requestField" label="路由字段" width="140" />
      <el-table-column label="路由算法" width="120">
        <template #default="{ row }">
          <el-tag :type="algoTag(row)" size="small">{{ algoLabel(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openForm(row)">编辑</el-button>
          <el-button size="small" type="primary" plain @click="openPreview(row.id)">路由预览</el-button>
          <el-popconfirm title="确认删除该分片配置？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑分片配置' : '新建分片配置'"
      width="760px" @close="resetForm">
      <el-form :model="form" label-width="110px" style="padding-right:16px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.name" placeholder="如：用户订单分片" />
        </el-form-item>
        <el-form-item label="路由字段" required>
          <el-input v-model="form.routingField" placeholder="请求参数中用于路由的字段名，如 userId" />
        </el-form-item>
        <el-form-item label="路由算法" required>
          <el-radio-group v-model="form.algorithmType">
            <el-radio value="MODULO">取模路由（MODULO）</el-radio>
            <el-radio value="RANGE">范围路由（RANGE）</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 取模路由专属 -->
        <template v-if="form.algorithmType === 'MODULO'">
          <el-form-item label="取模除数" required>
            <el-input-number v-model="form.divisor" :min="1" :max="10000" style="width:160px" />
            <span style="margin-left:8px;color:#999">分表总数</span>
          </el-form-item>
          <el-form-item label="库段配置">
            <div style="width:100%">
              <el-table :data="form.dbSegments" border size="small">
                <el-table-column label="数据源" min-width="160">
                  <template #default="{ row }">
                    <el-select v-model="row.dbConnectionId" placeholder="选择连接" style="width:100%">
                      <el-option v-for="c in dbList" :key="c.id" :label="c.name" :value="c.id" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="表前缀" width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.tablePrefix" placeholder="如 orders_" />
                  </template>
                </el-table-column>
                <el-table-column label="起始索引" width="90">
                  <template #default="{ row }">
                    <el-input-number v-model="row.indexStart" :min="0" style="width:80px" controls-position="right" />
                  </template>
                </el-table-column>
                <el-table-column label="结束索引" width="90">
                  <template #default="{ row }">
                    <el-input-number v-model="row.indexEnd" :min="0" style="width:80px" controls-position="right" />
                  </template>
                </el-table-column>
                <el-table-column label="补零位数" width="90">
                  <template #default="{ row }">
                    <el-input-number v-model="row.indexPadding" :min="0" :max="6"
                      style="width:80px" controls-position="right" />
                  </template>
                </el-table-column>
                <el-table-column label="" width="60">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" link @click="form.dbSegments.splice($index, 1)">删</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" style="margin-top:8px" @click="addSegment">+ 添加库段</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- 范围路由专属 -->
        <template v-if="form.algorithmType === 'RANGE'">
          <el-form-item label="范围分片">
            <div style="width:100%">
              <el-table :data="form.shards" border size="small">
                <el-table-column label="起始值" width="110">
                  <template #default="{ row }">
                    <el-input v-model="row.rangeStart" placeholder="如 1" />
                  </template>
                </el-table-column>
                <el-table-column label="结束值" width="110">
                  <template #default="{ row }">
                    <el-input v-model="row.rangeEnd" placeholder="如 999" />
                  </template>
                </el-table-column>
                <el-table-column label="数据源" min-width="140">
                  <template #default="{ row }">
                    <el-select v-model="row.dbConnectionId" style="width:100%">
                      <el-option v-for="c in dbList" :key="c.id" :label="c.name" :value="c.id" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="表名" width="130">
                  <template #default="{ row }">
                    <el-input v-model="row.tableName" placeholder="如 trade_001" />
                  </template>
                </el-table-column>
                <el-table-column label="" width="60">
                  <template #default="{ $index }">
                    <el-button size="small" type="danger" link @click="form.shards.splice($index, 1)">删</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button size="small" style="margin-top:8px" @click="addShard">+ 添加分片</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- 补查配置（折叠） -->
        <el-form-item label="补查配置">
          <el-collapse style="width:100%">
            <el-collapse-item title="若路由字段不在请求中，配置补查（可选）">
              <el-form label-width="100px">
                <el-form-item label="补查数据源">
                  <el-select v-model="form.lookup.dbConnectionId" clearable placeholder="选择连接" style="width:200px">
                    <el-option v-for="c in dbList" :key="c.id" :label="c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="查询表名">
                  <el-input v-model="form.lookup.table" placeholder="如 orders" style="width:200px" />
                </el-form-item>
                <el-form-item label="条件列名">
                  <el-input v-model="form.lookup.conditionColumn" placeholder="如 order_id" style="width:200px" />
                </el-form-item>
                <el-form-item label="请求参数key">
                  <el-input v-model="form.lookup.conditionParamKey" placeholder="如 orderId" style="width:200px" />
                </el-form-item>
                <el-form-item label="目标列名">
                  <el-input v-model="form.lookup.targetColumn" placeholder="如 user_id" style="width:200px" />
                </el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 路由预览弹窗 -->
    <el-dialog v-model="previewVisible" title="路由预览" width="480px">
      <div style="margin-bottom:12px">
        <div v-for="(item, idx) in previewParams" :key="idx" style="display:flex;gap:8px;margin-bottom:6px">
          <el-input v-model="item.key" placeholder="参数名" style="width:160px" />
          <el-input v-model="item.val" placeholder="参数值" style="width:160px" />
          <el-button size="small" type="danger" link @click="previewParams.splice(idx, 1)">删</el-button>
        </div>
        <el-button size="small" @click="previewParams.push({ key: '', val: '' })">+ 添加参数</el-button>
      </div>
      <el-button type="primary" :loading="previewing" @click="doPreview">执行预览</el-button>
      <div v-if="previewResult" style="margin-top:16px;padding:12px;background:#f5f7fa;border-radius:4px">
        <div>命中库：<b>{{ previewResult.dbName || previewResult.dbConnectionId }}</b></div>
        <div style="margin-top:4px">命中表：<b>{{ previewResult.tableName }}</b></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listShardConfigs, saveShardConfig, deleteShardConfig, previewShardRoute } from '@/api/shardConfig'
import { listConnections } from '@/api/dbConnection'

const list = ref([])
const loading = ref(false)
const searchName = ref('')
const dbList = ref([])

const formVisible = ref(false)
const saving = ref(false)
const form = ref(emptyForm())

const previewVisible = ref(false)
const previewing = ref(false)
const previewResult = ref(null)
const previewParams = ref([{ key: '', val: '' }])
let previewId = null

function emptyForm() {
  return {
    id: null,
    name: '',
    routingField: '',
    algorithmType: 'MODULO',
    divisor: 16,
    dbSegments: [],
    shards: [],
    lookup: { dbConnectionId: null, table: '', conditionColumn: '', conditionParamKey: '', targetColumn: '' }
  }
}

function algoTag(row) {
  try {
    const rule = JSON.parse(row.shardRule || '{}')
    return rule.algorithm && rule.algorithm.type === 'RANGE' ? 'warning' : ''
  } catch (e) { return 'info' }
}

function algoLabel(row) {
  try {
    const rule = JSON.parse(row.shardRule || '{}')
    return rule.algorithm && rule.algorithm.type === 'RANGE' ? 'RANGE' : 'MODULO'
  } catch (e) { return '—' }
}

async function loadList() {
  loading.value = true
  try {
    list.value = await listShardConfigs(searchName.value || undefined) || []
  } finally {
    loading.value = false
  }
}

async function loadDbList() {
  try {
    dbList.value = await listConnections() || []
  } catch (e) {}
}

function openForm(row) {
  form.value = emptyForm()
  if (row) {
    form.value.id = row.id
    form.value.name = row.name
    try {
      const rule = JSON.parse(row.shardRule || '{}')
      form.value.routingField = rule.routingField || ''
      form.value.algorithmType = (rule.algorithm && rule.algorithm.type) || 'MODULO'
      form.value.divisor = (rule.algorithm && rule.algorithm.divisor) || 16
      form.value.dbSegments = rule.dbSegments || []
      form.value.shards = rule.shards || []
      if (rule.fieldLookup) form.value.lookup = Object.assign({}, rule.fieldLookup)
    } catch (e) {}
  }
  formVisible.value = true
}

function resetForm() {
  form.value = emptyForm()
}

function addSegment() {
  form.value.dbSegments.push({ dbConnectionId: null, tablePrefix: '', indexStart: 0, indexEnd: 0, indexPadding: 0 })
}

function addShard() {
  form.value.shards.push({ rangeStart: '', rangeEnd: '', dbConnectionId: null, tableName: '' })
}

async function handleSave() {
  if (!form.value.name.trim()) { ElMessage.error('请填写配置名称'); return }
  if (!form.value.routingField.trim()) { ElMessage.error('请填写路由字段名'); return }

  const rule = {
    routingField: form.value.routingField,
    algorithm: { type: form.value.algorithmType }
  }
  if (form.value.algorithmType === 'MODULO') {
    rule.algorithm.divisor = form.value.divisor
    rule.dbSegments = form.value.dbSegments
  } else {
    rule.shards = form.value.shards.map(function(s) {
      return Object.assign({}, s, { rangeStart: Number(s.rangeStart), rangeEnd: Number(s.rangeEnd) })
    })
  }
  var lookup = form.value.lookup
  if (lookup.dbConnectionId) rule.fieldLookup = Object.assign({}, lookup)

  saving.value = true
  try {
    await saveShardConfig({ id: form.value.id, name: form.value.name, shardRule: JSON.stringify(rule) })
    ElMessage.success(form.value.id ? '更新成功' : '创建成功')
    formVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  await deleteShardConfig(row.id)
  ElMessage.success('删除成功')
  await loadList()
}

function openPreview(id) {
  previewId = id
  previewResult.value = null
  previewParams.value = [{ key: '', val: '' }]
  previewVisible.value = true
}

async function doPreview() {
  var params = {}
  previewParams.value.forEach(function(item) { if (item.key) params[item.key] = item.val })
  previewing.value = true
  try {
    previewResult.value = await previewShardRoute(previewId, params)
  } finally {
    previewing.value = false
  }
}

onMounted(function() {
  loadList()
  loadDbList()
})
</script>

<style scoped>
.shard-config { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
</style>
