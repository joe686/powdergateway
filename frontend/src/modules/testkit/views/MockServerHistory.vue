<template>
  <div class="testkit-mock-history">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Mock 请求历史</span>
          <div>
            <el-input v-model="pathFilter" placeholder="按 Path 过滤" style="width: 220px; margin-right:8px" clearable />
            <el-button :loading="loading" @click="loadHistory">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!reachable" type="warning" :closable="false" style="margin-bottom:12px">
        无法连接 pg-testkit（8081 端口），请先启动 pg-testkit 服务
      </el-alert>

      <el-table :data="records" size="small" v-loading="loading">
        <el-table-column prop="timestamp" label="时间" width="180" />
        <el-table-column prop="method" label="Method" width="90" />
        <el-table-column prop="path" label="Path" min-width="220" show-overflow-tooltip />
        <el-table-column prop="responseStatus" label="状态" width="90" />
        <el-table-column label="详情" width="110">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="请求详情" width="720px">
      <div v-if="detail">
        <p><b>时间：</b>{{ detail.timestamp }}</p>
        <p><b>Method：</b>{{ detail.method }}</p>
        <p><b>Path：</b>{{ detail.path }}</p>
        <p><b>命中规则：</b>{{ detail.matchedRule || '（未命中）' }}</p>
        <el-divider />
        <p><b>Headers</b></p>
        <pre style="background:#f5f5f5; padding:8px; font-size:12px">{{ JSON.stringify(detail.headers, null, 2) }}</pre>
        <p><b>Body</b></p>
        <pre style="background:#f5f5f5; padding:8px; font-size:12px">{{ detail.body }}</pre>
        <p><b>响应</b>：{{ detail.responseStatus }}</p>
        <pre style="background:#f5f5f5; padding:8px; font-size:12px">{{ detail.responseBody }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listMockRequests } from '../api/testkit'

const records = ref([])
const loading = ref(false)
const reachable = ref(true)
const pathFilter = ref('')
const detailVisible = ref(false)
const detail = ref(null)

let debounceTimer = null
watch(pathFilter, () => {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(loadHistory, 300)
})

onMounted(() => loadHistory())

async function loadHistory() {
  loading.value = true
  try {
    const list = await listMockRequests(pathFilter.value || undefined)
    records.value = list || []
    reachable.value = true
  } catch (e) {
    reachable.value = false
    records.value = []
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}
</script>

<style scoped>
.testkit-mock-history { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
