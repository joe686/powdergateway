<template>
  <div class="format-convert-page">
    <el-card class="page-card">
      <template #header>
        <span class="card-title">报文格式转换</span>
        <el-tooltip content="支持 JSON / XML / CSV / FormData 四种格式互转" placement="right">
          <el-icon class="help-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </template>

      <!-- 模板选择行（可选） -->
      <el-row :gutter="16" class="template-row">
        <el-col :span="16">
          <el-form-item label="使用模板（可选）">
            <el-select
              v-model="selectedTemplateId"
              placeholder="不使用模板（直接格式转换）"
              clearable
              filterable
              style="width: 100%"
              @change="onTemplateChange"
            >
              <el-option
                v-for="t in templateList"
                :key="t.id"
                :label="`${t.name}（${t.srcFormat} → ${t.targetFormat}）`"
                :value="t.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8" class="template-hint-col">
          <el-tag v-if="selectedTemplateId" type="success" size="small">含字段映射 + 字段加工</el-tag>
          <span v-else class="template-hint">选择模板后可应用字段映射和加工规则</span>
        </el-col>
      </el-row>

      <!-- 格式选择行 -->
      <el-row :gutter="16" class="format-row">
        <el-col :span="8">
          <el-form-item label="源格式">
            <el-select v-model="srcFormat" placeholder="选择源格式" style="width: 100%" :disabled="!!selectedTemplateId" @change="clearResult">
              <el-option v-for="f in formats" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="2" class="arrow-col">
          <el-icon size="24" color="#409EFF"><Right /></el-icon>
        </el-col>
        <el-col :span="8">
          <el-form-item label="目标格式">
            <el-select v-model="targetFormat" placeholder="选择目标格式" style="width: 100%" :disabled="!!selectedTemplateId" @change="clearResult">
              <el-option v-for="f in formats" :key="f.value" :label="f.label" :value="f.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6" class="btn-col">
          <el-button
            type="primary"
            :icon="VideoPlay"
            :loading="loading"
            :disabled="!srcFormat || !targetFormat || !srcMessage"
            @click="doConvert"
          >
            开始转换
          </el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-col>
      </el-row>

      <!-- 示例填充 -->
      <div class="sample-bar">
        <span class="sample-label">快速填充示例：</span>
        <el-button
          v-for="s in samples"
          :key="s.format"
          size="small"
          text
          type="primary"
          @click="fillSample(s)"
        >
          {{ s.label }}
        </el-button>
      </div>

      <!-- 报文输入 / 输出区 -->
      <el-row :gutter="16" class="message-row">
        <el-col :span="12">
          <div class="panel-title">
            <span>源报文</span>
            <el-tag size="small" type="info">{{ srcFormat || '未选择' }}</el-tag>
          </div>
          <el-input
            v-model="srcMessage"
            type="textarea"
            :rows="14"
            placeholder="请在此输入源格式报文..."
            @input="clearResult"
          />
        </el-col>
        <el-col :span="12">
          <div class="panel-title">
            <span>转换结果</span>
            <el-tag size="small" :type="resultMessage ? 'success' : 'info'">
              {{ targetFormat || '未选择' }}
            </el-tag>
            <el-button
              v-if="resultMessage"
              size="small"
              text
              :icon="CopyDocument"
              @click="copyResult"
              style="margin-left: auto"
            >
              复制
            </el-button>
          </div>
          <el-input
            v-model="resultMessage"
            type="textarea"
            :rows="14"
            readonly
            placeholder="转换结果将显示在此处..."
            :class="{ 'result-success': resultMessage }"
          />
        </el-col>
      </el-row>

      <!-- 字段解析预览 -->
      <div v-if="parsedFields && Object.keys(parsedFields).length > 0" class="fields-section">
        <div class="panel-title">
          <span>字段解析预览</span>
          <el-tag size="small" type="warning">{{ Object.keys(parsedFields).length }} 个字段</el-tag>
        </div>
        <el-table :data="fieldRows" border stripe size="small" max-height="200">
          <el-table-column prop="key" label="字段名" width="200" />
          <el-table-column prop="value" label="字段值" />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled, Right, VideoPlay, Refresh, CopyDocument } from '@element-plus/icons-vue'
import { convertFormat, parseMessage, convertMessage } from '@/api/convert'
import { listTemplates } from '@/api/template'

// ==================== 格式选项 ====================
const formats = [
  { label: 'JSON', value: 'JSON' },
  { label: 'XML',  value: 'XML' },
  { label: 'CSV',  value: 'CSV' },
  { label: 'FormData', value: 'FORM_DATA' }
]

// ==================== 示例报文 ====================
const samples = [
  {
    format: 'JSON',
    label: 'JSON 示例',
    message: '{\n  "userId": "001",\n  "name": "Alice",\n  "amount": "100.00"\n}'
  },
  {
    format: 'XML',
    label: 'XML 示例',
    message: '<root>\n  <userId>001</userId>\n  <name>Alice</name>\n  <amount>100.00</amount>\n</root>'
  },
  {
    format: 'CSV',
    label: 'CSV 示例',
    message: 'userId,name,amount\n001,Alice,100.00'
  },
  {
    format: 'FORM_DATA',
    label: 'FormData 示例',
    message: 'userId=001&name=Alice&amount=100.00'
  }
]

// ==================== 状态 ====================
const srcFormat          = ref('')
const targetFormat       = ref('')
const srcMessage         = ref('')
const resultMessage      = ref('')
const parsedFields       = ref(null)
const loading            = ref(false)
const selectedTemplateId = ref(null)
const templateList       = ref([])

onMounted(async () => {
  try {
    const res = await listTemplates({ page: 1, size: 200 })
    templateList.value = res.records || []
  } catch { /* 加载失败静默，不影响直接转换功能 */ }
})

const fieldRows = computed(() =>
  parsedFields.value
    ? Object.entries(parsedFields.value).map(([key, value]) => ({ key, value: String(value) }))
    : []
)

// ==================== 操作 ====================
function clearResult() {
  resultMessage.value = ''
  parsedFields.value = null
}

function onTemplateChange(id) {
  if (!id) return
  const tmpl = templateList.value.find(t => t.id === id)
  if (tmpl) {
    srcFormat.value = tmpl.srcFormat
    targetFormat.value = tmpl.targetFormat
    clearResult()
  }
}

function reset() {
  srcFormat.value = ''
  targetFormat.value = ''
  srcMessage.value = ''
  resultMessage.value = ''
  parsedFields.value = null
  selectedTemplateId.value = null
}

function fillSample(sample) {
  srcFormat.value = sample.format
  srcMessage.value = sample.message
  clearResult()
}

async function doConvert() {
  if (!srcFormat.value || !targetFormat.value) {
    ElMessage.warning('请先选择源格式和目标格式')
    return
  }
  if (!srcMessage.value.trim()) {
    ElMessage.warning('请输入源报文')
    return
  }
  if (srcFormat.value === targetFormat.value) {
    ElMessage.warning('源格式和目标格式相同，无需转换')
    return
  }

  loading.value = true
  try {
    if (selectedTemplateId.value) {
      // 使用模板：串联全流程（格式转换 + 字段映射 + 字段加工）
      const res = await convertMessage({
        templateId: selectedTemplateId.value,
        message: srcMessage.value,
        srcFormat: srcFormat.value
      })
      resultMessage.value = res.result
      if (res.targetFormat) targetFormat.value = res.targetFormat
      ElMessage.success('转换成功')
    } else {
      // 直接格式转换（原有逻辑）：并行请求转换 + 字段解析
      const [convertRes, parseRes] = await Promise.all([
        convertFormat({
          message: srcMessage.value,
          srcFormat: srcFormat.value,
          targetFormat: targetFormat.value
        }),
        parseMessage({
          message: srcMessage.value,
          format: srcFormat.value
        })
      ])
      resultMessage.value = convertRes.result
      parsedFields.value = parseRes
      ElMessage.success('转换成功')
    }
  } catch (err) {
    // 错误由 request.js 拦截器统一展示，此处只做日志
    console.error('格式转换失败', err)
  } finally {
    loading.value = false
  }
}

async function copyResult() {
  try {
    await navigator.clipboard.writeText(resultMessage.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.format-convert-page {
  padding: 16px;
}
.page-card {
  max-width: 1200px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
}
.help-icon {
  margin-left: 6px;
  color: #909399;
  cursor: help;
  vertical-align: middle;
}
.template-row {
  align-items: flex-end;
  margin-bottom: 4px;
}
.template-hint-col {
  display: flex;
  align-items: center;
  padding-top: 8px;
}
.template-hint {
  font-size: 12px;
  color: #909399;
}
.format-row {
  align-items: flex-end;
  margin-bottom: 4px;
}
.arrow-col {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 8px;
}
.btn-col {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
}
.sample-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.sample-label {
  font-size: 13px;
  color: #606266;
}
.message-row {
  margin-bottom: 16px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 8px;
}
.result-success :deep(textarea) {
  background-color: #f0f9eb;
}
.fields-section {
  margin-top: 8px;
}
</style>
