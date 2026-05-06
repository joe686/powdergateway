<template>
  <div class="cache-list">
    <div class="toolbar">
      <el-popconfirm title="确认清除所有接口缓存？" @confirm="handleEvictAll">
        <template #reference>
          <el-button type="danger">一键清除全部缓存</el-button>
        </template>
      </el-popconfirm>
      <el-button @click="loadList" style="margin-left: 8px">刷新统计</el-button>
    </div>

    <el-table :data="list" stripe border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="interfaceName" label="接口名称" min-width="160" />
      <el-table-column label="缓存状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.cacheEnabled === 1"
            @change="(val) => handleToggleCache(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="Key 模板" min-width="160">
        <template #default="{ row }">
          <span v-if="row.cacheKeyTemplate">{{ row.cacheKeyTemplate }}</span>
          <span v-else style="color: #999">（按参数自动排列）</span>
        </template>
      </el-table-column>
      <el-table-column prop="cacheTtlSeconds" label="TTL（秒）" width="100" />
      <el-table-column prop="hitCount" label="命中次数" width="100" />
      <el-table-column prop="missCount" label="未命中次数" width="110" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-popconfirm title="确认清除该接口缓存？" @confirm="handleEvict(row)">
            <template #reference>
              <el-button size="small" type="warning">清除</el-button>
            </template>
          </el-popconfirm>
          <el-button size="small" type="primary" @click="handleRefresh(row)">刷新</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑缓存配置弹窗 -->
    <el-dialog v-model="editVisible" title="编辑缓存配置" width="480px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="TTL（秒）">
          <el-input-number v-model="editForm.cacheTtlSeconds" :min="0" :max="86400" />
        </el-form-item>
        <el-form-item label="Key 模板">
          <el-input
            v-model="editForm.cacheKeyTemplate"
            placeholder="如：query:{userId}:{status}，不填则按参数自动排列"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleEditSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 刷新预热参数弹窗 -->
    <el-dialog v-model="refreshVisible" title="输入预热参数（JSON）" width="480px">
      <el-input
        v-model="refreshParams"
        type="textarea"
        :rows="4"
        placeholder='{"userId": 1, "status": "active"}'
      />
      <template #footer>
        <el-button @click="refreshVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRefreshConfirm">预热</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { cacheApi } from '@/api/cache'

const list = ref([])
const loading = ref(false)

const editVisible = ref(false)
const editForm = ref({ interfaceId: null, cacheTtlSeconds: 300, cacheKeyTemplate: '' })

const refreshVisible = ref(false)
const refreshRow = ref(null)
const refreshParams = ref('')

async function loadList() {
  loading.value = true
  try {
    list.value = await cacheApi.list()
  } finally {
    loading.value = false
  }
}

async function handleToggleCache(row, val) {
  await cacheApi.updateConfig(row.interfaceId, { cacheEnabled: val ? 1 : 0 })
  row.cacheEnabled = val ? 1 : 0
  ElMessage.success(val ? '缓存已开启' : '缓存已关闭')
}

function handleEdit(row) {
  editForm.value = {
    interfaceId: row.interfaceId,
    cacheTtlSeconds: row.cacheTtlSeconds ?? 300,
    cacheKeyTemplate: row.cacheKeyTemplate ?? ''
  }
  editVisible.value = true
}

async function handleEditSave() {
  await cacheApi.updateConfig(editForm.value.interfaceId, {
    cacheTtlSeconds: editForm.value.cacheTtlSeconds,
    cacheKeyTemplate: editForm.value.cacheKeyTemplate
  })
  ElMessage.success('缓存配置已保存，旧缓存已自动清除')
  editVisible.value = false
  await loadList()
}

async function handleEvict(row) {
  await cacheApi.evict(row.interfaceId)
  ElMessage.success('缓存已清除')
  await loadList()
}

async function handleEvictAll() {
  await cacheApi.evictAll()
  ElMessage.success('全部缓存已清除')
  await loadList()
}

function handleRefresh(row) {
  refreshRow.value = row
  refreshParams.value = ''
  refreshVisible.value = true
}

async function handleRefreshConfirm() {
  let params = {}
  if (refreshParams.value.trim()) {
    try {
      params = JSON.parse(refreshParams.value)
    } catch {
      ElMessage.error('参数 JSON 格式错误')
      return
    }
  }
  await cacheApi.refresh(refreshRow.value.interfaceId, params)
  ElMessage.success('缓存已刷新并完成预热')
  refreshVisible.value = false
  await loadList()
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; }
</style>
