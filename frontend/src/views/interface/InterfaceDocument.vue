<template>
  <div class="interface-document">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ─── 转换接口文档 ─────────────────────────────────────────────── -->
      <el-tab-pane label="转换接口文档" name="transform">
        <div class="toolbar">
          <el-button
            type="primary"
            :loading="exportingTransform"
            @click="handleExportTransformZip"
          >
            <el-icon><Download /></el-icon>
            全量导出 zip
          </el-button>
        </div>

        <el-table
          v-loading="loadingTransform"
          :data="transformList"
          border
          stripe
          style="width: 100%; margin-top: 12px"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="模板名称" min-width="160" />
          <el-table-column prop="srcFormat" label="源格式" width="120" />
          <el-table-column prop="targetFormat" label="目标格式" width="120" />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                @click="handleDownloadTransform(row, 'md')"
              >下载 Markdown</el-button>
              <el-button
                size="small"
                type="primary"
                @click="handleDownloadTransform(row, 'html')"
              >下载 HTML</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ─── 可视化接口文档 ───────────────────────────────────────────── -->
      <el-tab-pane label="可视化接口文档" name="visual">
        <div class="toolbar">
          <el-button
            type="primary"
            :loading="exportingVisual"
            @click="handleExportVisualZip"
          >
            <el-icon><Download /></el-icon>
            全量导出 zip
          </el-button>
        </div>

        <el-table
          v-loading="loadingVisual"
          :data="visualList"
          border
          stripe
          style="width: 100%; margin-top: 12px"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="接口名称" min-width="160" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="row.status === 'published' ? 'success' : row.status === 'disabled' ? 'danger' : 'info'"
                size="small"
              >
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                @click="handleDownloadVisual(row, 'md')"
              >下载 Markdown</el-button>
              <el-button
                size="small"
                type="primary"
                @click="handleDownloadVisual(row, 'html')"
              >下载 HTML</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import {
  listTransformDocs,
  listVisualDocs,
  downloadTransformDoc,
  downloadVisualDoc,
  exportTransformZip,
  exportVisualZip
} from '@/api/interfaceDoc'
import { downloadBlob } from '@/utils/download'

const activeTab = ref('transform')

// ─── 转换接口 ──────────────────────────────────────────────────────────────
const transformList = ref([])
const loadingTransform = ref(false)
const exportingTransform = ref(false)

async function fetchTransformList() {
  loadingTransform.value = true
  try {
    transformList.value = await listTransformDocs()
  } finally {
    loadingTransform.value = false
  }
}

async function handleDownloadTransform(row, format) {
  try {
    const blob = await downloadTransformDoc(row.id, format)
    downloadBlob(blob, `${row.name}_文档.${format}`)
  } catch {
    ElMessage.error('下载失败，请重试')
  }
}

async function handleExportTransformZip() {
  exportingTransform.value = true
  try {
    const blob = await exportTransformZip()
    downloadBlob(blob, '转换接口文档.zip')
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exportingTransform.value = false
  }
}

// ─── 可视化接口 ────────────────────────────────────────────────────────────
const visualList = ref([])
const loadingVisual = ref(false)
const exportingVisual = ref(false)

async function fetchVisualList() {
  loadingVisual.value = true
  try {
    visualList.value = await listVisualDocs()
  } finally {
    loadingVisual.value = false
  }
}

async function handleDownloadVisual(row, format) {
  try {
    const blob = await downloadVisualDoc(row.id, format)
    downloadBlob(blob, `${row.name}_文档.${format}`)
  } catch {
    ElMessage.error('下载失败，请重试')
  }
}

async function handleExportVisualZip() {
  exportingVisual.value = true
  try {
    const blob = await exportVisualZip()
    downloadBlob(blob, '可视化接口文档.zip')
  } catch {
    ElMessage.error('导出失败，请重试')
  } finally {
    exportingVisual.value = false
  }
}

function statusLabel(status) {
  const map = { published: '已发布', draft: '草稿', disabled: '已禁用' }
  return map[status] || status
}

// ─── 初始化 ────────────────────────────────────────────────────────────────
onMounted(() => {
  fetchTransformList()
})

watch(activeTab, (tab) => {
  if (tab === 'visual' && visualList.value.length === 0) {
    fetchVisualList()
  }
})
</script>

<style scoped>
.interface-document {
  padding: 16px;
}
.toolbar {
  display: flex;
  justify-content: flex-end;
}
</style>
