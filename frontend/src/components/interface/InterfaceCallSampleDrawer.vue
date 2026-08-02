<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    :title="`接口调用示例 · #${interfaceInfo?.id || '?'} · ${interfaceInfo?.name || ''}`"
    size="50%"
    :with-header="true"
    :destroy-on-close="true"
  >
    <div v-if="!interfaceInfo" class="empty">加载中...</div>
    <div v-else class="drawer-body">
      <el-alert
        v-if="interfaceInfo.status !== 'published'"
        title="⚠️ 此接口尚未发布 · 需先在接口管理点「发布」按钮才能被外部调用"
        type="warning" show-icon :closable="false" style="margin-bottom: 12px"
      />

      <div class="url-block">
        <label>调用地址</label>
        <div class="url-line">
          <code>POST {{ execUrl }}</code>
          <el-button size="small" @click="copy(execUrl)">📋</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="curl" name="curl">
          <div class="tab-actions">
            <el-button size="small" type="primary" @click="copy(curlSample)">📋 复制</el-button>
          </div>
          <pre class="code-block">{{ curlSample }}</pre>
        </el-tab-pane>

        <el-tab-pane label="Postman JSON" name="postman">
          <div class="tab-actions">
            <el-alert
              title="拷贝下方 JSON,在 Postman 里 Import → Raw text 粘贴,即可导入本接口的调用示例"
              type="info" :closable="false" style="margin-bottom: 8px"
            />
            <el-button size="small" type="primary" @click="copy(postmanSample)">📋 复制</el-button>
          </div>
          <pre class="code-block">{{ postmanSample }}</pre>
        </el-tab-pane>

        <el-tab-pane label="HTTP Raw" name="raw">
          <div class="tab-actions">
            <el-button size="small" type="primary" @click="copy(httpRawSample)">📋 复制</el-button>
          </div>
          <pre class="code-block">{{ httpRawSample }}</pre>
        </el-tab-pane>

        <el-tab-pane label="Python requests" name="python">
          <div class="tab-actions">
            <el-button size="small" type="primary" @click="copy(pythonSample)">📋 复制</el-button>
          </div>
          <pre class="code-block">{{ pythonSample }}</pre>
        </el-tab-pane>
      </el-tabs>

      <el-alert
        title="💡 提示: 已发布接口可在 Swagger UI 页面下方「接口执行」分组下找到并直接测试。请不要把接口路径输入到 Swagger 顶部 URL 栏(那是 OpenAPI 定义文件 URL,输入接口路径会报「Unable to render this definition」错误)。"
        type="info" show-icon :closable="false" style="margin-top: 12px"
      />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  visible: { type: Boolean, default: false },
  interfaceInfo: { type: Object, default: null }
})
const emit = defineEmits(['update:visible'])

const activeTab = ref('curl')

const host = computed(() => `${location.protocol}//${location.host}`)
const execUrl = computed(() =>
  props.interfaceInfo ? `${host.value}/api/exec/${props.interfaceInfo.id}` : ''
)

const samplePayload = computed(() => {
  if (!props.interfaceInfo) return '{"params":{}}'
  const isSelect = props.interfaceInfo.type === 'SELECT'
  const body = isSelect
    ? { params: {}, page: 1, pageSize: 20 }
    : { params: {} }
  return JSON.stringify(body, null, 2)
})

const curlSample = computed(() => {
  if (!props.interfaceInfo) return ''
  const bodyOneLine = JSON.stringify(JSON.parse(samplePayload.value))
  return `curl -X POST '${execUrl.value}' \\
  -H 'Content-Type: application/json' \\
  -d '${bodyOneLine}'`
})

const postmanSample = computed(() => {
  if (!props.interfaceInfo) return ''
  const iface = props.interfaceInfo
  const collection = {
    info: {
      name: `PowerGateway · ${iface.name || iface.id}`,
      schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
    },
    item: [{
      name: iface.name || `接口 #${iface.id}`,
      request: {
        method: 'POST',
        header: [{ key: 'Content-Type', value: 'application/json' }],
        url: {
          raw: execUrl.value,
          host: [host.value],
          path: ['api', 'exec', String(iface.id)]
        },
        body: {
          mode: 'raw',
          raw: samplePayload.value
        }
      }
    }]
  }
  return JSON.stringify(collection, null, 2)
})

const httpRawSample = computed(() => {
  if (!props.interfaceInfo) return ''
  const body = samplePayload.value
  return `POST /api/exec/${props.interfaceInfo.id} HTTP/1.1
Host: ${location.host}
Content-Type: application/json
Content-Length: ${body.length}

${body}`
})

const pythonSample = computed(() => {
  if (!props.interfaceInfo) return ''
  return `import requests

url = '${execUrl.value}'
payload = ${samplePayload.value}

resp = requests.post(url, json=payload, timeout=10)
print(resp.status_code)
print(resp.json())`
})

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.error('复制失败,请手动选中复制:' + (e.message || e))
  }
}
</script>

<style scoped>
.drawer-body {
  padding: 0 20px 20px;
}
.empty {
  padding: 40px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.url-block {
  margin-bottom: 12px;
}
.url-block label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.url-line {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--el-fill-color-lighter);
  padding: 8px 12px;
  border-radius: 4px;
}
.url-line code {
  flex: 1;
  color: var(--el-color-primary);
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
.tab-actions {
  margin-bottom: 8px;
}
.code-block {
  background: var(--el-fill-color-lighter);
  padding: 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 500px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
}
</style>
