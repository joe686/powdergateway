<template>
  <div class="message-debug">
    <!-- 模式切换 -->
    <div class="mode-bar">
      <el-radio-group v-model="mode" @change="onModeChange">
        <el-radio-button value="convert">格式转换调试</el-radio-button>
        <el-radio-button value="exec">接口调用调试</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 配置栏 -->
    <div class="config-bar">
      <template v-if="mode === 'convert'">
        <span class="config-label">选择模板：</span>
        <el-select
          v-model="selectedTemplateId"
          placeholder="请选择转换模板"
          style="width: 320px"
          filterable
          clearable
        >
          <el-option
            v-for="t in templates"
            :key="t.id"
            :label="t.name"
            :value="t.id"
          />
        </el-select>
      </template>
      <template v-else>
        <span class="config-label">选择接口：</span>
        <el-select
          v-model="selectedInterfaceId"
          placeholder="请选择已发布接口"
          style="width: 320px"
          filterable
          clearable
        >
          <el-option
            v-for="i in publishedInterfaces"
            :key="i.id"
            :label="`${i.name}（${i.path}）`"
            :value="i.id"
          />
        </el-select>
      </template>
      <el-button
        type="primary"
        :loading="executing"
        style="margin-left: 12px"
        @click="execute"
      >
        执行
      </el-button>
    </div>

    <!-- 主区：左右分区 -->
    <div class="main-area">
      <!-- 左区：输入 -->
      <div class="panel">
        <div class="panel-header">
          {{ mode === 'convert' ? '源报文' : '请求参数' }}
        </div>
        <el-input
          v-model="inputText"
          type="textarea"
          :placeholder="inputPlaceholder"
          class="input-area"
          resize="none"
        />
      </div>

      <!-- 右区：结果 -->
      <div class="panel">
        <div class="panel-header">
          执行结果
          <span v-if="costMs !== null" class="cost-tag">耗时 {{ costMs }}ms</span>
        </div>
        <div class="result-area">
          <pre v-if="resultHtml" class="hljs result-pre"><code v-html="resultHtml" /></pre>
          <div v-else class="result-empty">执行后结果显示在此处</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import { listTemplates } from '@/api/template'
import { listInterfaces, execInterface } from '@/api/interface'
import { convertMessage } from '@/api/convert'

const mode = ref('convert')

// 格式转换模式
const templates = ref([])
const selectedTemplateId = ref(null)

// 接口调用模式
const allInterfaces = ref([])
const selectedInterfaceId = ref(null)
const publishedInterfaces = computed(() =>
  allInterfaces.value.filter(i => i.status === 'published')
)

// 公共状态
const inputText = ref('')
const resultHtml = ref(null)
const costMs = ref(null)
const executing = ref(false)

const inputPlaceholder = computed(() =>
  mode.value === 'convert'
    ? '输入源报文（JSON / XML / CSV 均可）'
    : '输入请求参数（JSON 格式，如 {"status": 1}）'
)

async function loadTemplates() {
  const res = await listTemplates({ page: 1, size: 200, latestOnly: true })
  templates.value = res.records ?? []
}

async function loadInterfaces() {
  const res = await listInterfaces('', 1, 500)
  allInterfaces.value = res.records ?? []
}

function onModeChange() {
  inputText.value = ''
  resultHtml.value = null
  costMs.value = null
}

function renderResult(text) {
  try {
    resultHtml.value = hljs.highlightAuto(text).value
  } catch {
    resultHtml.value = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

async function execute() {
  if (!inputText.value.trim()) {
    ElMessage.warning('请输入报文或参数')
    return
  }
  if (mode.value === 'convert' && !selectedTemplateId.value) {
    ElMessage.warning('请先选择转换模板')
    return
  }
  if (mode.value === 'exec' && !selectedInterfaceId.value) {
    ElMessage.warning('请先选择接口')
    return
  }

  executing.value = true
  resultHtml.value = null
  costMs.value = null

  try {
    if (mode.value === 'convert') {
      const res = await convertMessage({
        templateId: selectedTemplateId.value,
        message: inputText.value
      })
      renderResult(res.result)
      costMs.value = res.costMs
    } else {
      let body
      try {
        body = JSON.parse(inputText.value)
      } catch {
        ElMessage.warning('参数格式错误，请输入合法 JSON')
        return
      }
      const t0 = Date.now()
      const res = await execInterface(selectedInterfaceId.value, body)
      costMs.value = Date.now() - t0
      const text = typeof res === 'string' ? res : JSON.stringify(res, null, 2)
      renderResult(text)
    }
  } catch (e) {
    const msg = e?.message || '执行失败'
    const escaped = msg.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    resultHtml.value = `<span style="color:var(--pg-danger)">${escaped}</span>`
  } finally {
    executing.value = false
  }
}

onMounted(() => {
  loadTemplates()
  loadInterfaces()
})
</script>

<style scoped>
.message-debug {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  padding: 16px;
  gap: 12px;
}

.mode-bar {
  flex-shrink: 0;
}

.config-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.config-label {
  margin-right: 8px;
  color: var(--pg-text-regular);
  font-size: 14px;
}

.main-area {
  flex: 1;
  display: flex;
  gap: 12px;
  overflow: hidden;
}

.panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}

.panel-header {
  padding: 8px 12px;
  background: var(--pg-track-bg);
  border-bottom: 1px solid var(--pg-line-strong);
  font-size: 13px;
  font-weight: 500;
  color: var(--pg-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cost-tag {
  font-size: 12px;
  color: var(--pg-success);
  font-weight: normal;
}

.input-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.input-area :deep(.el-textarea) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.input-area :deep(.el-textarea__inner) {
  flex: 1;
  height: auto;
  font-family: monospace;
  font-size: 13px;
  border: none;
  border-radius: 0;
}

.result-area {
  flex: 1;
  overflow: auto;
  background: var(--pg-bg-base);
}

.result-pre {
  margin: 0;
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-empty {
  padding: 24px;
  color: var(--pg-text-secondary);
  font-size: 13px;
}
</style>
