<template>
  <div class="table-structure">
    <el-row :gutter="16" style="height: 100%">
      <!-- 左侧：连接选择 + 表树 -->
      <el-col :span="7" style="height: 100%">
        <el-card style="height: 100%">
          <template #header>
            <div class="card-header">
              <span>数据源</span>
              <div>
                <el-button size="small" :loading="refreshing" @click="handleRefresh">
                  刷新缓存
                </el-button>
                <el-button size="small" type="primary" :loading="exporting" @click="handleExport">
                  导出 Excel
                </el-button>
              </div>
            </div>
          </template>

          <!-- 连接选择 -->
          <el-select
            v-model="selectedDbId"
            placeholder="选择数据库连接"
            style="width: 100%; margin-bottom: 12px"
            @change="loadTables"
          >
            <el-option
              v-for="conn in connections"
              :key="conn.id"
              :label="`${conn.name}（${conn.dbType}/${conn.env}）`"
              :value="conn.id"
            />
          </el-select>

          <!-- 搜索框 -->
          <el-input
            v-model="filterText"
            placeholder="搜索表名"
            clearable
            style="margin-bottom: 12px"
            :prefix-icon="Search"
          />

          <!-- 表树 -->
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            :filter-node-method="filterNode"
            node-key="id"
            highlight-current
            v-loading="tableLoading"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span>
                <el-icon v-if="data.type === 'table'" style="color: #409eff"><Grid /></el-icon>
                <el-icon v-else style="color: #67c23a"><Document /></el-icon>
                {{ node.label }}
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧：字段列表 -->
      <el-col :span="17" style="height: 100%">
        <el-card style="height: 100%">
          <template #header>
            <span>{{ selectedTable ? `${selectedTable} — 字段详情` : '选择左侧表查看字段' }}</span>
          </template>

          <el-table :data="columnData" stripe style="width: 100%">
            <el-table-column prop="name" label="列名" width="200" />
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column label="主键" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.isPrimary" type="danger" size="small">PK</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="可空" width="80">
              <template #default="{ row }">
                <el-tag :type="row.nullable ? 'info' : 'success'" size="small">
                  {{ row.nullable ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remarks" label="备注" min-width="150" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Grid, Document } from '@element-plus/icons-vue'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure, refreshTableCache, exportTableExcel } from '@/api/tableStructure'

const connections = ref([])
const selectedDbId = ref(null)
const tableLoading = ref(false)
const refreshing = ref(false)
const exporting = ref(false)
const filterText = ref('')
const treeRef = ref(null)
const selectedTable = ref('')
const columnData = ref([])
const tableMetaList = ref([])

const treeProps = { label: 'label', children: 'children' }
const treeData = ref([])

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

function filterNode(value, data) {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

function buildTree(tables) {
  return tables.map((t, i) => ({
    id: `table_${i}`,
    label: t.tableName + (t.comment ? `（${t.comment}）` : ''),
    type: 'table',
    tableName: t.tableName,
    children: t.columns.map((c, j) => ({
      id: `col_${i}_${j}`,
      label: `${c.name}  [${c.type}]${c.isPrimary ? ' PK' : ''}`,
      type: 'column'
    }))
  }))
}

function handleNodeClick(data) {
  if (data.type !== 'table') return
  selectedTable.value = data.tableName
  const found = tableMetaList.value.find(
    t => t.tableName.toLowerCase() === data.tableName.toLowerCase()
  )
  columnData.value = found ? found.columns : []
}

async function loadConnections() {
  connections.value = await listConnections()
}

async function loadTables() {
  if (!selectedDbId.value) return
  tableLoading.value = true
  selectedTable.value = ''
  columnData.value = []
  try {
    tableMetaList.value = await getTableStructure(selectedDbId.value)
    treeData.value = buildTree(tableMetaList.value)
  } finally {
    tableLoading.value = false
  }
}

async function handleRefresh() {
  if (!selectedDbId.value) return ElMessage.warning('请先选择数据库连接')
  refreshing.value = true
  try {
    tableMetaList.value = await refreshTableCache(selectedDbId.value)
    treeData.value = buildTree(tableMetaList.value)
    ElMessage.success('缓存已刷新')
  } finally {
    refreshing.value = false
  }
}

async function handleExport() {
  if (!selectedDbId.value) return ElMessage.warning('请先选择数据库连接')
  exporting.value = true
  try {
    const blob = await exportTableExcel(selectedDbId.value)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `table_structure_${selectedDbId.value}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

onMounted(loadConnections)
</script>

<style scoped>
.table-structure {
  padding: 16px;
  height: calc(100vh - 120px);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
