<template>
  <div class="testkit-mock-rules">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Mock 后端服务 · 端口 9999</span>
          <div>
            <el-button :loading="loading" @click="loadRules">刷新</el-button>
            <el-button type="danger" :loading="resetting" @click="confirmReset">清空规则</el-button>
            <el-button type="primary" @click="openCreate">+ 新增规则</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!reachable" type="warning" :closable="false" style="margin-bottom:12px">
        无法连接 pg-testkit（8081 端口），请先启动 pg-testkit 服务
      </el-alert>

      <el-table :data="rules" size="small" v-loading="loading">
        <el-table-column prop="method" label="Method" width="100" />
        <el-table-column prop="pathPattern" label="Path 匹配" min-width="220" />
        <el-table-column prop="responseStatus" label="响应状态" width="100" />
        <el-table-column prop="delayMs" label="延迟(ms)" width="100" />
        <el-table-column label="响应体" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <code style="font-size: 12px">{{ (row.responseBody || '').slice(0, 80) }}...</code>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="新增 Mock 规则" width="640px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="Method">
          <el-select v-model="form.method" style="width: 160px">
            <el-option label="POST" value="POST" />
            <el-option label="GET" value="GET" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Path 匹配">
          <el-input v-model="form.pathPattern" placeholder="如：/cbs/query" />
        </el-form-item>
        <el-form-item label="Body 包含">
          <el-input v-model="form.bodyContains" placeholder="可选：匹配 body 里的关键字（含则命中）" />
        </el-form-item>
        <el-form-item label="响应状态码">
          <el-input-number v-model="form.responseStatus" :min="100" :max="599" />
        </el-form-item>
        <el-form-item label="延迟(ms)">
          <el-input-number v-model="form.delayMs" :min="0" :max="60000" :step="100" />
        </el-form-item>
        <el-form-item label="Content-Type">
          <el-select v-model="form.responseContentType" style="width: 240px">
            <el-option value="application/json" label="application/json" />
            <el-option value="application/xml" label="application/xml" />
            <el-option value="text/plain" label="text/plain" />
          </el-select>
        </el-form-item>
        <el-form-item label="响应体">
          <el-input v-model="form.responseBody" type="textarea" :rows="6"
                    placeholder='如：{"code":0,"balance":12345}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMockRules, addMockRule, resetMockServer } from '../api/testkit'

const rules = ref([])
const loading = ref(false)
const reachable = ref(true)
const createVisible = ref(false)
const saving = ref(false)
const resetting = ref(false)

const form = reactive({
  method: 'POST',
  pathPattern: '',
  bodyContains: '',
  responseStatus: 200,
  delayMs: 0,
  responseContentType: 'application/json',
  responseBody: ''
})

onMounted(() => loadRules())

async function loadRules() {
  loading.value = true
  try {
    const list = await listMockRules()
    rules.value = list || []
    reachable.value = true
  } catch (e) {
    reachable.value = false
    rules.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    method: 'POST',
    pathPattern: '',
    bodyContains: '',
    responseStatus: 200,
    delayMs: 0,
    responseContentType: 'application/json',
    responseBody: ''
  })
  createVisible.value = true
}

async function submitForm() {
  if (!form.pathPattern) {
    ElMessage.warning('请填 Path 匹配')
    return
  }
  saving.value = true
  try {
    await addMockRule({ ...form })
    ElMessage.success('规则已添加')
    createVisible.value = false
    await loadRules()
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e))
  } finally {
    saving.value = false
  }
}

async function confirmReset() {
  try {
    await ElMessageBox.confirm('清空所有规则？', '确认', { type: 'warning' })
  } catch { return }
  resetting.value = true
  try {
    await resetMockServer()
    await loadRules()
    ElMessage.success('已清空')
  } catch (e) {
    ElMessage.error('清空失败：' + (e?.message || e))
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.testkit-mock-rules { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
