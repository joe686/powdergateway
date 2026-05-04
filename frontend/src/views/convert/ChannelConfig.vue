<template>
  <div class="channel-config-page">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="openDialog(null)">新增渠道</el-button>
    </div>

    <!-- 渠道列表 -->
    <el-table :data="tableData" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="channelCode" label="渠道编码" min-width="140" />
      <el-table-column prop="channelName" label="渠道名称" min-width="140" />
      <el-table-column prop="identifyField" label="识别字段" min-width="130" />
      <el-table-column label="关联模板" min-width="160">
        <template #default="{ row }">
          <span>{{ templateNameById(row.templateId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm
            title="确认删除该渠道配置？"
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑渠道配置' : '新增渠道配置'"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="渠道编码" prop="channelCode">
          <el-input
            v-model="form.channelCode"
            placeholder="唯一标识，如 WECHAT_PAY"
            :disabled="!!form.id"
          />
        </el-form-item>
        <el-form-item label="渠道名称" prop="channelName">
          <el-input v-model="form.channelName" placeholder="如：微信支付渠道" />
        </el-form-item>
        <el-form-item label="识别字段" prop="identifyField">
          <el-input
            v-model="form.identifyField"
            placeholder="报文中的字段名，如 channelCode"
          />
          <div class="field-tip">
            运行时从报文提取该字段值，与渠道编码匹配后路由至对应模板
          </div>
        </el-form-item>
        <el-form-item label="关联模板" prop="templateId">
          <el-select
            v-model="form.templateId"
            placeholder="请选择转换模板"
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
        </el-form-item>
        <!-- 报文头配置（CHG-002） -->
        <el-divider content-position="left" style="margin-top:16px;margin-bottom:8px">报文头配置（可选）</el-divider>
        <el-form-item label="Content-Type">
          <el-select v-model="form.headerConfig.contentType" placeholder="不设置则使用默认" clearable style="width:100%">
            <el-option label="application/json" value="application/json" />
            <el-option label="application/xml" value="application/xml" />
            <el-option label="text/plain" value="text/plain" />
            <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
          </el-select>
        </el-form-item>
        <el-form-item label="字符集">
          <el-select v-model="form.headerConfig.charset" placeholder="不设置则 UTF-8（不转码）" clearable style="width:100%">
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
import { listChannels, saveChannel, deleteChannel } from '@/api/channel'
import { listTemplates } from '@/api/template'

// ─────────────────────── 数据 ───────────────────────
const loading = ref(false)
const tableData = ref([])
const templateOptions = ref([])

// ─────────────────────── 弹窗 ───────────────────────
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const form = ref({
  id: null,
  channelCode: '',
  channelName: '',
  identifyField: '',
  templateId: null,
  headerConfig: {
    contentType: '',
    charset: '',
    requestHeaders: [],
    responseHeaders: []
  }
})

const rules = {
  channelCode: [{ required: true, message: '请输入渠道编码', trigger: 'blur' }],
  identifyField: [{ required: true, message: '请输入识别字段名', trigger: 'blur' }],
  templateId: [{ required: true, message: '请选择关联模板', trigger: 'change' }]
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

// ─────────────────────── 初始化 ─────────────────────
onMounted(() => {
  fetchChannels()
  fetchTemplateOptions()
})

async function fetchChannels() {
  loading.value = true
  try {
    tableData.value = await listChannels()
  } finally {
    loading.value = false
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
      channelName: row.channelName || '',
      identifyField: row.identifyField || '',
      templateId: row.templateId,
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
    channelName: '',
    identifyField: '',
    templateId: null,
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
    await saveChannel({ ...form.value, headerConfig })
    ElMessage.success(form.value.id ? '更新成功' : '新增成功')
    dialogVisible.value = false
    fetchChannels()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deleteChannel(id)
  ElMessage.success('删除成功')
  fetchChannels()
}
</script>

<style scoped>
.channel-config-page {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
.field-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
