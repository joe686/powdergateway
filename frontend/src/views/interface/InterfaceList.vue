<template>
  <div class="interface-list">
    <div class="toolbar">
      <el-input
        v-model="searchName"
        placeholder="搜索接口名称"
        clearable
        style="width: 260px"
        @keyup.enter="loadList"
        @clear="loadList"
      />
      <el-button type="primary" @click="loadList">查询</el-button>
      <el-button type="success" @click="goWizard">向导新建</el-button>
      <el-button type="default" @click="handleExportReport">
        <el-icon><Download /></el-icon>导出报表
      </el-button>
    </div>

    <el-table :data="list" stripe border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="name" label="接口名称" min-width="160" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="typeTagType(row.type)" size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="访问路径" min-width="200">
        <template #default="{ row }">
          <span v-if="row.status === 'published'" class="path-text">{{ row.path }}</span>
          <span v-else style="color:var(--pg-text-secondary)">—</span>
        </template>
      </el-table-column>
      <el-table-column label="响应格式" width="110">
        <template #default="{ row }">
          <el-tag size="small">{{ row.responseFormat || 'JSON' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'published'"
            type="success"
            size="small"
            @click="handlePublish(row)"
          >发布</el-button>
          <el-button
            v-if="row.status === 'published'"
            type="warning"
            size="small"
            @click="handleDisable(row)"
          >禁用</el-button>
          <el-button
            v-if="row.type === 'SELECT'"
            size="small"
            @click="$router.push('/interface/cache')"
          >缓存</el-button>
          <el-button size="small" @click="openShardDialog(row)">分片</el-button>
          <el-button size="small" @click="handleExportFields(row)">导出字段</el-button>
          <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-popconfirm
            :title="row.status === 'published' ? '已发布接口请先禁用再删除' : '确认删除该接口？'"
            :disabled="row.status === 'published'"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button type="danger" size="small" :disabled="row.status === 'published'">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; text-align: right"
      @current-change="loadList"
    />

  <!-- 绑定分片配置弹窗 -->
  <el-dialog v-model="shardDialogVisible" title="绑定分库分表配置" width="420px">
    <el-form label-width="100px">
      <el-form-item label="分片配置">
        <el-select v-model="shardForm.shardConfigId" clearable placeholder="不启用分片路由"
          style="width:100%" @visible-change="onShardSelectOpen">
          <el-option v-for="s in shardList" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="shardDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="shardSaving" @click="handleBindShard">保存</el-button>
    </template>
  </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  listInterfaces,
  deleteInterface,
  publishInterface,
  disableInterface,
  bindShardConfig,
  exportInterfaceList
} from '@/api/interface'
import { listShardConfigs } from '@/api/shardConfig'
import { useWizardStore } from '@/store/wizard'
import { exportFieldSchema } from '@/api/interfaceFieldSchema'
import { downloadBlob } from '@/utils/download'

const router = useRouter()
const wizardStore = useWizardStore()
const list = ref([])
const loading = ref(false)
const searchName = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const shardDialogVisible = ref(false)
const shardSaving = ref(false)
const shardList = ref([])
const shardForm = ref({ interfaceId: null, shardConfigId: null })

const TYPE_ROUTE = {
  SELECT: '/interface/dev',
  INSERT: '/interface/insert',
  UPDATE: '/interface/update',
  DELETE: '/interface/delete'
}

function typeTagType(type) {
  return { SELECT: '', INSERT: 'success', UPDATE: 'warning', DELETE: 'danger' }[type] ?? 'info'
}

function statusTagType(status) {
  return { published: 'success', draft: 'info', disabled: 'danger' }[status] ?? 'info'
}

function statusLabel(status) {
  return { published: '已发布', draft: '草稿', disabled: '已禁用' }[status] ?? status
}

async function loadList() {
  loading.value = true
  try {
    const res = await listInterfaces(searchName.value || undefined, page.value, pageSize.value)
    list.value = res ?? []
    total.value = list.value.length
  } finally {
    loading.value = false
  }
}

function goWizard() {
  wizardStore.reset()
  router.push('/interface/wizard')
}

async function handlePublish(row) {
  await publishInterface(row.id)
  ElMessage.success('发布成功')
  await loadList()
}

async function handleDisable(row) {
  await disableInterface(row.id)
  ElMessage.success('已禁用')
  await loadList()
}

function handleEdit(row) {
  const path = TYPE_ROUTE[row.type]
  if (path) {
    router.push({ path, query: { id: row.id } })
  } else {
    ElMessage.warning('未知接口类型')
  }
}

async function handleExportFields(row) {
  try {
    const blob = await exportFieldSchema(row.id)
    downloadBlob(blob, `${row.name}_字段清单.xlsx`)
  } catch {
    ElMessage.error('导出字段清单失败')
  }
}

async function handleExportReport() {
  try {
    const blob = await exportInterfaceList(searchName.value || undefined)
    downloadBlob(blob, `接口列表_${Date.now()}.xlsx`)
  } catch {
    ElMessage.error('导出报表失败')
  }
}

async function handleDelete(row) {
  await deleteInterface(row.id)
  ElMessage.success('删除成功')
  await loadList()
}

function openShardDialog(row) {
  shardForm.value = { interfaceId: row.id, shardConfigId: row.shardConfigId ?? null }
  shardDialogVisible.value = true
}

async function onShardSelectOpen(visible) {
  if (visible && shardList.value.length === 0) {
    try {
      shardList.value = await listShardConfigs() || []
    } catch (e) {}
  }
}

async function handleBindShard() {
  shardSaving.value = true
  try {
    await bindShardConfig(shardForm.value.interfaceId, shardForm.value.shardConfigId)
    ElMessage.success('绑定成功')
    shardDialogVisible.value = false
    await loadList()
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示，此处无需重复处理
  } finally {
    shardSaving.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.interface-list { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
.path-text { font-family: monospace; font-size: 12px; color: var(--pg-primary); }
</style>
