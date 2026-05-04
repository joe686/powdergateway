<template>
  <div class="port-route-page">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <el-input
        v-model="searchCode"
        placeholder="渠道编码搜索"
        clearable
        style="width: 220px"
        @clear="fetchList"
        @keyup.enter="fetchList"
      />
      <el-button type="primary" @click="fetchList">搜索</el-button>
      <el-button type="primary" style="margin-left: auto" @click="openDialog(null)">新增路由</el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="channelCode" label="渠道编码" min-width="130" />
      <el-table-column prop="portAddress" label="转发地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="portMethod" label="HTTP方法" width="100">
        <template #default="{ row }">
          <el-tag :type="methodTagType(row.portMethod)" size="small">{{ row.portMethod }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="timeout" label="超时(ms)" width="100" />
      <el-table-column prop="retryCount" label="重试次数" width="90" />
      <el-table-column label="请求模板(A→B)" min-width="130">
        <template #default="{ row }">
          <span>{{ templateNameById(row.requestTemplateId) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="应答模板(B→A)" min-width="130">
        <template #default="{ row }">
          <span>{{ templateNameById(row.responseTemplateId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button link type="success" :loading="testingId === row.id" @click="handleTest(row)">
            连通测试
          </el-button>
          <el-popconfirm
            title="确认删除该路由配置？"
            confirm-button-text="确认"
            cancel-button-text="取消"
            @confirm="handleDelete(row.id)"
          >
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchList"
        @current-change="fetchList"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑端口路由' : '新增端口路由'"
      width="580px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="渠道编码" prop="channelCode">
          <el-select
            v-model="form.channelCode"
            placeholder="请选择或输入渠道编码"
            filterable
            allow-create
            :disabled="!!form.id"
            style="width: 100%"
          >
            <el-option
              v-for="ch in channelOptions"
              :key="ch.channelCode"
              :label="`${ch.channelCode}（${ch.channelName || ''}）`"
              :value="ch.channelCode"
            />
          </el-select>
          <div class="field-tip">选择已有渠道，或直接输入新渠道编码；编辑时不可修改</div>
        </el-form-item>
        <el-form-item label="转发地址" prop="portAddress">
          <el-input
            v-model="form.portAddress"
            placeholder="如：http://api.partner.com:8080/v1/pay"
          />
          <div class="field-tip">目标系统完整 URL，含协议、主机、端口和路径</div>
        </el-form-item>
        <el-form-item label="HTTP方法" prop="portMethod">
          <el-select v-model="form.portMethod" style="width: 100%">
            <el-option label="POST" value="POST" />
            <el-option label="GET" value="GET" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时时间(ms)" prop="timeout">
          <el-input-number
            v-model="form.timeout"
            :min="500"
            :max="30000"
            :step="500"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="重试次数" prop="retryCount">
          <el-input-number
            v-model="form.retryCount"
            :min="0"
            :max="5"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="请求模板(A→B)">
          <el-select
            v-model="form.requestTemplateId"
            placeholder="不选则原样转发"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="tpl in templateOptions"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            />
          </el-select>
          <div class="field-tip">发往目标系统前对请求报文做转换（A系统格式 → B系统格式）</div>
        </el-form-item>
        <el-form-item label="应答模板(B→A)">
          <el-select
            v-model="form.responseTemplateId"
            placeholder="不选则透传原始应答"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="tpl in templateOptions"
              :key="tpl.id"
              :label="tpl.name"
              :value="tpl.id"
            />
          </el-select>
          <div class="field-tip">收到目标系统应答后对报文做转换（B系统格式 → A系统格式）</div>
        </el-form-item>
        <!-- 报文头配置（CHG-002） -->
        <el-alert
          type="info"
          :closable="false"
          style="margin:12px 0 4px 0"
          description="未填写的项将继承渠道默认值（渠道配置 < 路由配置优先级）"
        />
        <el-divider content-position="left" style="margin-top:8px;margin-bottom:8px">报文头配置（可选）</el-divider>
        <el-form-item label="Content-Type">
          <el-select v-model="form.headerConfig.contentType" placeholder="不设置则继承渠道配置" clearable style="width:100%">
            <el-option label="application/json" value="application/json" />
            <el-option label="application/xml" value="application/xml" />
            <el-option label="text/plain" value="text/plain" />
            <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
          </el-select>
        </el-form-item>
        <el-form-item label="字符集">
          <el-select v-model="form.headerConfig.charset" placeholder="不设置则继承渠道配置" clearable style="width:100%">
            <el-option label="UTF-8（默认）" value="UTF-8" />
            <el-option label="GBK" value="GBK" />
            <el-option label="ISO-8859-1" value="ISO-8859-1" />
          </el-select>
        </el-form-item>
        <el-form-item label="出向请求头">
          <div style="width:100%">
            <div
              v-for="(item, idx) in form.headerConfig.requestHeaders"
              :key="idx"
              style="display:flex;gap:8px;margin-bottom:6px"
            >
              <el-input v-model="item.key" placeholder="Header 名" style="flex:1" />
              <el-input v-model="item.value" placeholder="Header 值" style="flex:1" />
              <el-button type="danger" link @click="form.headerConfig.requestHeaders.splice(idx, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="form.headerConfig.requestHeaders.push({ key: '', value: '' })">+ 添加请求头</el-button>
          </div>
        </el-form-item>
        <el-form-item label="返回响应头">
          <div style="width:100%">
            <div
              v-for="(item, idx) in form.headerConfig.responseHeaders"
              :key="idx"
              style="display:flex;gap:8px;margin-bottom:6px"
            >
              <el-input v-model="item.key" placeholder="Header 名" style="flex:1" />
              <el-input v-model="item.value" placeholder="Header 值" style="flex:1" />
              <el-button type="danger" link @click="form.headerConfig.responseHeaders.splice(idx, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="form.headerConfig.responseHeaders.push({ key: '', value: '' })">+ 添加响应头</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listPortRoutes, savePortRoute, deletePortRoute, testPortConnectivity } from '@/api/portRoute'
import { listChannels } from '@/api/channel'
import { listTemplates } from '@/api/template'

// ─────────────────────── 列表数据 ───────────────────────
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const searchCode = ref('')

// ─────────────────────── 选项数据 ───────────────────────
const channelOptions = ref([])
const templateOptions = ref([])

// ─────────────────────── 连通测试 ───────────────────────
const testingId = ref(null)

// ─────────────────────── 弹窗 ───────────────────────
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const form = ref({
  id: null,
  channelCode: '',
  portAddress: '',
  portMethod: 'POST',
  timeout: 3000,
  retryCount: 3,
  requestTemplateId: null,
  responseTemplateId: null,
  headerConfig: {
    contentType: '',
    charset: '',
    requestHeaders: [],
    responseHeaders: []
  }
})

const rules = {
  channelCode: [{ required: true, message: '请选择或输入渠道编码', trigger: 'change' }],
  portAddress: [
    { required: true, message: '请输入转发地址', trigger: 'blur' },
    { pattern: /^https?:\/\/.+/, message: '请输入有效的 HTTP/HTTPS 地址', trigger: 'blur' }
  ],
  portMethod: [{ required: true, message: '请选择 HTTP 方法', trigger: 'change' }],
  timeout: [{ required: true, message: '请设置超时时间', trigger: 'change' }],
  retryCount: [{ required: true, message: '请设置重试次数', trigger: 'change' }]
}

// ─────────────────────── 计算 ───────────────────────
const templateMap = computed(() => {
  const map = {}
  templateOptions.value.forEach(t => { map[t.id] = t.name })
  return map
})

function templateNameById(id) {
  if (!id) return '—'
  return templateMap.value[id] || `模板#${id}`
}

function methodTagType(method) {
  const map = { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger' }
  return map[method] || 'info'
}

// ─────────────────────── 初始化 ─────────────────────
onMounted(() => {
  fetchList()
  fetchChannelOptions()
  fetchTemplateOptions()
})

async function fetchList() {
  loading.value = true
  try {
    const res = await listPortRoutes({
      page: pageNum.value,
      size: pageSize.value,
      channelCode: searchCode.value || undefined
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchChannelOptions() {
  try {
    channelOptions.value = await listChannels()
  } catch {
    // 渠道列表加载失败不影响主页面
  }
}

async function fetchTemplateOptions() {
  try {
    const page = await listTemplates({ page: 1, size: 200, latestOnly: true })
    templateOptions.value = page.records || []
  } catch {
    // 模板列表加载失败不影响主页面
  }
}

// ─────────────────────── 操作 ───────────────────────
function openDialog(row) {
  if (row) {
    // 回填 headerConfig（CHG-002）
    const hc = (row && row.headerConfig) ? row.headerConfig : {}
    form.value = {
      id: row.id,
      channelCode: row.channelCode,
      portAddress: row.portAddress,
      portMethod: row.portMethod || 'POST',
      timeout: row.timeout || 3000,
      retryCount: row.retryCount ?? 3,
      requestTemplateId: row.requestTemplateId || null,
      responseTemplateId: row.responseTemplateId || null,
      headerConfig: {
        contentType: hc.contentType || '',
        charset: hc.charset || '',
        requestHeaders: hc.requestHeaders
          ? Object.entries(hc.requestHeaders).map(function(entry) { return { key: entry[0], value: entry[1] } })
          : [],
        responseHeaders: hc.responseHeaders
          ? Object.entries(hc.responseHeaders).map(function(entry) { return { key: entry[0], value: entry[1] } })
          : []
      }
    }
  }
  dialogVisible.value = true
}

function resetForm() {
  form.value = {
    id: null,
    channelCode: '',
    portAddress: '',
    portMethod: 'POST',
    timeout: 3000,
    retryCount: 3,
    requestTemplateId: null,
    responseTemplateId: null,
    headerConfig: {
      contentType: '',
      charset: '',
      requestHeaders: [],
      responseHeaders: []
    }
  }
  formRef.value?.clearValidate()
}

function toMap(arr) {
  return arr.reduce(function(acc, item) {
    if (item.key && item.key.trim()) acc[item.key.trim()] = item.value
    return acc
  }, {})
}

async function handleSave() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const reqHeaders = toMap(form.value.headerConfig.requestHeaders)
    const respHeaders = toMap(form.value.headerConfig.responseHeaders)
    const headerConfig = {
      contentType: form.value.headerConfig.contentType || null,
      charset: form.value.headerConfig.charset || null,
      requestHeaders: Object.keys(reqHeaders).length ? reqHeaders : null,
      responseHeaders: Object.keys(respHeaders).length ? respHeaders : null
    }
    await savePortRoute({ ...form.value, headerConfig })
    ElMessage.success(form.value.id ? '更新成功' : '新增成功')
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deletePortRoute(id)
  ElMessage.success('删除成功')
  fetchList()
}

async function handleTest(row) {
  testingId.value = row.id
  try {
    const res = await testPortConnectivity(row.id)
    if (res.success) {
      ElMessage.success(`连通成功，HTTP ${res.httpStatus}`)
    } else {
      ElMessage.warning(`连通失败：${res.message || '目标地址不可达'}`)
    }
  } catch {
    ElMessage.error('连通测试请求失败')
  } finally {
    testingId.value = null
  }
}
</script>

<style scoped>
.port-route-page {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.field-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
