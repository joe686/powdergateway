<template>
  <div class="template-list-page">
    <el-card class="page-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">转换模板管理</span>
          <el-button type="primary" @click="handleCreate">新建模板</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="模板名称">
          <el-input
            v-model="query.keyword"
            placeholder="输入模板名称搜索"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="default" @click="handleExportReport">
            <el-icon><Download /></el-icon>导出报表
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        empty-text="暂无数据"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="模板名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="格式" width="160">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.srcFormat }}</el-tag>
            <span class="format-arrow">→</span>
            <el-tag type="success" size="small">{{ row.targetFormat }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="functionCode" label="功能号" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.functionCode || '—' }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" align="center" />
        <el-table-column prop="creator" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click="handleEdit(row)"
            >编辑</el-button>
            <el-button
              type="warning"
              link
              size="small"
              @click="handleCopy(row)"
            >复制</el-button>
            <el-popconfirm
              title="确定要删除该模板吗？删除后不可恢复。"
              confirm-button-text="确定删除"
              cancel-button-text="取消"
              @confirm="handleDelete(row)"
            >
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { listTemplates, deleteTemplate, copyTemplate, exportTemplateList } from '@/api/template'
import { downloadBlob } from '@/utils/download'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  latestOnly: true
})

async function loadData() {
  loading.value = true
  try {
    const res = await listTemplates(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch {
    // 错误已由 request.js 拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadData()
}

function handleReset() {
  query.keyword = ''
  query.page = 1
  loadData()
}

function handleCreate() {
  router.push('/convert/field-mapping')
}

function handleEdit(row) {
  router.push({ path: '/convert/field-mapping', query: { templateId: row.id } })
}

async function handleCopy(row) {
  try {
    await copyTemplate(row.id)
    ElMessage.success(`已复制模板：${row.name}_copy`)
    loadData()
  } catch {
    // 错误已由拦截器处理
  }
}

async function handleDelete(row) {
  try {
    await deleteTemplate(row.id)
    ElMessage.success('删除成功')
    // 当前页最后一条被删时自动翻到上一页
    if (tableData.value.length === 1 && query.page > 1) {
      query.page--
    }
    loadData()
  } catch {
    // 错误已由拦截器处理
  }
}

async function handleExportReport() {
  try {
    const blob = await exportTemplateList(query.keyword || undefined)
    downloadBlob(blob, `转换模板_${Date.now()}.xlsx`)
  } catch {
    ElMessage.error('导出报表失败')
  }
}

function formatTime(val) {
  if (!val) return '—'
  return val.replace('T', ' ').substring(0, 19)
}

onMounted(loadData)
</script>

<style scoped>
.template-list-page {
  padding: 16px;
}
.page-card {
  min-height: calc(100vh - 120px);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
}
.search-form {
  margin-bottom: 16px;
}
.format-arrow {
  margin: 0 4px;
  color: #909399;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
