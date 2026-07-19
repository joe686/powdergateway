<template>
  <div>
    <!-- Step 1 · 选择系统 -->
    <div v-show="props.isActive('system')">
      <el-form label-width="140px" style="max-width:720px">
        <el-form-item label="来源系统" required>
          <el-select v-model="s.sourceChannelCode" filterable placeholder="选择来源系统" style="width:280px" @change="onSourceChannelChange">
            <el-option v-for="c in channels" :key="c.channelCode" :label="`${c.channelName} · ${c.channelCode}`" :value="c.channelCode" />
          </el-select>
          <el-button link style="margin-left:8px" @click="openAddChannel('source')">+ 新增渠道</el-button>
        </el-form-item>
        <el-form-item label="目标系统" required>
          <el-select v-model="s.targetChannelCode" filterable placeholder="选择目标系统" style="width:280px" @change="onTargetChannelChange">
            <el-option v-for="c in channels" :key="c.channelCode" :label="`${c.channelName} · ${c.channelCode}`" :value="c.channelCode" />
          </el-select>
          <el-button link style="margin-left:8px" @click="openAddChannel('target')">+ 新增渠道</el-button>
        </el-form-item>
        <el-form-item label="来源报文格式" required>
          <el-radio-group v-model="s.sourceFormat">
            <el-radio-button label="JSON" />
            <el-radio-button label="XML" />
            <el-radio-button label="CSV" />
            <el-radio-button label="FormData" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标报文格式" required>
          <el-radio-group v-model="s.targetFormat">
            <el-radio-button label="JSON" />
            <el-radio-button label="XML" />
            <el-radio-button label="CSV" />
            <el-radio-button label="FormData" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="转换复杂度">
          <el-radio-group v-model="s.complexity">
            <el-radio label="HEADER_ONLY">仅换报文头</el-radio>
            <el-radio label="BODY_FIELDS">换报文体字段</el-radio>
            <el-radio label="FORMAT_AND_PROCESS">换格式+字段+加工</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <!-- 新增渠道弹窗 -->
      <el-dialog v-model="addChannelVisible" title="新增渠道" width="480px">
        <el-form label-width="100px">
          <el-form-item label="渠道编码" required><el-input v-model="newChannel.channelCode" /></el-form-item>
          <el-form-item label="渠道名称" required><el-input v-model="newChannel.channelName" /></el-form-item>
          <el-form-item label="识别字段"><el-input v-model="newChannel.identifyField" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addChannelVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmAddChannel">确定</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- Step 2 · 功能号 -->
    <div v-show="props.isActive('function')">
      <el-form label-width="140px" style="max-width:720px">
        <el-form-item label="功能号" required>
          <el-input v-model="s.functionCode" placeholder="如 CBS_QUERY_ACCOUNT" @blur="checkFcExists" />
          <div v-if="fcWarning" style="color:#E6A23C;font-size:12px;margin-top:4px">{{ fcWarning }}</div>
        </el-form-item>
        <el-form-item label="功能号中文名">
          <el-input v-model="s.functionName" placeholder="可选，如 账户查询" />
        </el-form-item>
        <el-form-item label="报文类别">
          <el-select v-model="s.messageCategory" placeholder="选择类别" style="width:200px">
            <el-option label="查询类" value="QUERY" />
            <el-option label="交易类" value="TRANSACTION" />
            <el-option label="通知类" value="NOTIFY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- Step 3 · 端口配置 -->
    <div v-show="props.isActive('port')">
      <el-form label-width="140px" style="max-width:720px">
        <el-form-item label="端口地址" required>
          <el-input v-model="s.portAddress" placeholder="http(s)://host:port/path" />
        </el-form-item>
        <el-form-item label="HTTP Method" required>
          <el-select v-model="s.portMethod" style="width:160px">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时（ms）">
          <el-input-number v-model="s.timeout" :min="100" :max="60000" />
        </el-form-item>
        <el-form-item label="失败重试次数">
          <el-input-number v-model="s.retryCount" :min="0" :max="10" />
        </el-form-item>
        <div style="color:#909399;font-size:12px;margin-left:140px">发布后可在 Step 6 端到端测试中验证连通性</div>
      </el-form>
    </div>

    <!-- Step 4 · 端口路由绑定 -->
    <div v-show="props.isActive('route')">
      <el-descriptions :column="2" border style="margin-bottom:16px">
        <el-descriptions-item label="来源系统">{{ s.sourceChannelName }} · {{ s.sourceChannelCode }}</el-descriptions-item>
        <el-descriptions-item label="目标系统">{{ s.targetChannelName }} · {{ s.targetChannelCode }}</el-descriptions-item>
        <el-descriptions-item label="功能号">{{ s.functionCode }}</el-descriptions-item>
        <el-descriptions-item label="端口地址">{{ s.portAddress }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="140px" style="max-width:720px">
        <el-form-item label="Content-Type">
          <el-select v-model="s.headerConfig.contentType" style="width:280px">
            <el-option label="application/json" value="application/json" />
            <el-option label="application/xml" value="application/xml" />
            <el-option label="text/plain" value="text/plain" />
            <el-option label="application/x-www-form-urlencoded" value="application/x-www-form-urlencoded" />
          </el-select>
        </el-form-item>
        <el-form-item label="Charset">
          <el-select v-model="s.headerConfig.charset" style="width:200px">
            <el-option label="UTF-8" value="UTF-8" />
            <el-option label="GBK" value="GBK" />
            <el-option label="ISO-8859-1" value="ISO-8859-1" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="s.savedPortRouteId" style="color:#67C23A;margin-top:8px">
        已保存路由 ID：{{ s.savedPortRouteId }}（继续将更新此记录）
      </div>
    </div>

    <!-- Step 5 · 转换模板 -->
    <div v-show="props.isActive('template')">
      <el-radio-group v-model="s.templateMode">
        <el-radio-button label="EXISTING">选择已有模板</el-radio-button>
        <el-radio-button label="NEW">新建模板</el-radio-button>
      </el-radio-group>
      <div v-if="s.templateMode === 'EXISTING'" style="margin-top:16px">
        <el-select v-model="s.savedTemplateId" placeholder="选择模板" filterable style="width:360px" @change="onExistingTemplatePicked">
          <el-option v-for="t in fcTemplates" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <div v-if="fcTemplates.length === 0" style="color:#E6A23C;margin-top:8px">该功能号暂无模板，请切换到"新建"</div>
      </div>
      <div v-else style="margin-top:16px">
        <el-form label-width="120px" style="max-width:720px">
          <el-form-item label="模板名" required>
            <el-input v-model="s.newTemplateDraft.name" />
          </el-form-item>
          <el-form-item label="字段映射">
            <el-table :data="s.newTemplateDraft.mappingRules" border size="small">
              <el-table-column label="源字段">
                <template #default="{ row }"><el-input v-model="row.src" size="small" /></template>
              </el-table-column>
              <el-table-column label="目标字段">
                <template #default="{ row }"><el-input v-model="row.target" size="small" /></template>
              </el-table-column>
              <el-table-column label="加工规则" width="140">
                <template #default="{ row }">
                  <el-select v-model="row.process" size="small" clearable>
                    <el-option label="TRIM" value="TRIM" />
                    <el-option label="UPPER" value="UPPER" />
                    <el-option label="LOWER" value="LOWER" />
                    <el-option label="SUBSTRING" value="SUBSTRING" />
                    <el-option label="PAD" value="PAD" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="s.newTemplateDraft.mappingRules.splice($index, 1)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" style="margin-top:6px" @click="s.newTemplateDraft.mappingRules.push({ src: '', target: '', process: '' })">+ 添加映射</el-button>
          </el-form-item>
          <el-form-item>
            <el-link type="primary" href="/convert/template" target="_blank">需要更复杂映射？跳转完整字段映射页配置 →</el-link>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- Step 6 · 测试 -->
    <div v-show="props.isActive('test')">
      <el-form label-width="120px">
        <el-form-item label="测试模式">
          <el-radio-group v-model="s.testMode">
            <el-radio label="SIMULATE">模拟调用（/api/convert）</el-radio>
            <el-radio label="LIVE">实际调用（/api/dispatch）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="输入报文">
          <el-input type="textarea" v-model="s.testInput" :rows="8" />
          <el-button size="small" style="margin-top:6px" @click="fillExample">填充示例</el-button>
        </el-form-item>
      </el-form>
      <el-button type="primary" :loading="testing" @click="runTest">执行测试</el-button>
      <div v-if="s.testOutput" style="margin-top:16px">
        <p style="font-weight:500">转换/应答报文：</p>
        <el-input type="textarea" :model-value="s.testOutput" :rows="10" readonly />
      </div>
      <el-alert v-if="s.testError" :title="s.testError" type="error" style="margin-top:12px" />
      <el-link type="primary" style="margin-top:12px;display:block" @click="skipTestToPublish">跳过测试直接发布</el-link>
    </div>

    <!-- Step 7 · 发布 -->
    <div v-show="props.isActive('publish')">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="来源→目标">{{ s.sourceChannelCode }} → {{ s.targetChannelCode }}</el-descriptions-item>
        <el-descriptions-item label="报文格式">{{ s.sourceFormat }} → {{ s.targetFormat }}</el-descriptions-item>
        <el-descriptions-item label="功能号">{{ s.functionCode }} · {{ s.functionName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="端口">{{ s.portMethod }} {{ s.portAddress }}</el-descriptions-item>
        <el-descriptions-item label="路由 ID">{{ s.savedPortRouteId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="模板 ID">{{ s.savedTemplateId || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div style="color:#909399;margin-top:12px">点击"保存并启用"将回到端口路由列表，可以看到刚才配置的记录。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useTransformWizardStore } from '@/store/transformWizard'
import { listChannels, saveChannel } from '@/api/channel'
import { savePortRoute } from '@/api/portRoute'
import { checkFunctionCodeExists } from '@/api/functionCode'
import { listTemplates, saveTemplate } from '@/api/template'
import request from '@/api/request'

const props = defineProps({
  isActive: { type: Function, required: true }
})

const router = useRouter()
const s = useTransformWizardStore()

const channels = ref([])
const fcWarning = ref('')
const addChannelVisible = ref(false)
const addChannelTarget = ref('source')
const newChannel = ref({ channelCode: '', channelName: '', identifyField: '' })
const fcTemplates = ref([])
const testing = ref(false)

onMounted(async () => {
  try {
    const list = await listChannels()
    channels.value = Array.isArray(list) ? list : (list?.records || [])
  } catch (e) {
    console.warn('[TransformInterfaceSteps] 渠道加载失败', e)
  }
})

watch(() => s.functionCode, async (fc) => {
  if (fc && s.templateMode === 'EXISTING') {
    try {
      const res = await listTemplates({ page: 1, size: 100, functionCode: fc })
      fcTemplates.value = res?.records || []
    } catch { fcTemplates.value = [] }
  }
})

function onSourceChannelChange(code) {
  const found = channels.value.find(c => c.channelCode === code)
  s.sourceChannelName = found ? found.channelName : code
}

function onTargetChannelChange(code) {
  const found = channels.value.find(c => c.channelCode === code)
  s.targetChannelName = found ? found.channelName : code
}

function openAddChannel(target) {
  addChannelTarget.value = target
  newChannel.value = { channelCode: '', channelName: '', identifyField: '' }
  addChannelVisible.value = true
}

async function confirmAddChannel() {
  if (!newChannel.value.channelCode || !newChannel.value.channelName) {
    ElMessage.warning('渠道编码和名称不能为空')
    return
  }
  try {
    await saveChannel(newChannel.value)
    channels.value.push({ ...newChannel.value })
    if (addChannelTarget.value === 'source') {
      s.sourceChannelCode = newChannel.value.channelCode
      s.sourceChannelName = newChannel.value.channelName
    } else {
      s.targetChannelCode = newChannel.value.channelCode
      s.targetChannelName = newChannel.value.channelName
    }
    addChannelVisible.value = false
    ElMessage.success('渠道新增成功')
  } catch (e) {
    ElMessage.error('新增渠道失败：' + (e?.message || ''))
  }
}

async function checkFcExists() {
  if (!s.functionCode) { fcWarning.value = ''; return }
  try {
    const exists = await checkFunctionCodeExists(s.functionCode)
    fcWarning.value = exists ? '该功能号已被使用，继续将覆盖既有路由' : ''
  } catch { fcWarning.value = '' }
}

function validateStep(key) {
  if (key === 'system') {
    if (!s.sourceChannelCode) return '请选择来源系统'
    if (!s.targetChannelCode) return '请选择目标系统'
    if (!s.sourceFormat) return '请选择来源报文格式'
    if (!s.targetFormat) return '请选择目标报文格式'
    return true
  }
  if (key === 'function') {
    if (!/^[A-Za-z0-9_]{5,64}$/.test(s.functionCode)) return '功能号需为 5-64 位英文/数字/下划线'
    return true
  }
  if (key === 'port') {
    if (!/^https?:\/\//.test(s.portAddress)) return '端口地址必须以 http:// 或 https:// 开头'
    if (!s.portMethod) return '请选择 HTTP Method'
    if (!(s.timeout > 0)) return '超时必须大于 0'
    if (s.retryCount < 0 || s.retryCount > 10) return '重试次数需在 0-10 之间'
    return true
  }
  if (key === 'route') {
    if (!s.headerConfig?.contentType) return 'Content-Type 不能为空'
    if (!s.headerConfig?.charset) return 'Charset 不能为空'
    return true
  }
  if (key === 'template') {
    if (s.templateMode === 'EXISTING' && !s.savedTemplateId) return '请选择模板或切换到"新建"'
    if (s.templateMode === 'NEW') {
      if (!s.newTemplateDraft.name?.trim() && !s.functionCode) return '请填写模板名'
    }
    return true
  }
  if (key === 'test') {
    if (!s.testOutput && !s.testError) return '请至少执行一次测试或点"跳过测试直接发布"'
    return true
  }
  return true
}

function buildPortRoutePayload() {
  return {
    id: s.savedPortRouteId || undefined,
    channelCode: s.sourceChannelCode,
    portAddress: s.portAddress,
    portMethod: s.portMethod,
    timeout: s.timeout,
    retryCount: s.retryCount,
    functionCode: s.functionCode,
    functionName: s.functionName,
    headerConfig: s.headerConfig
  }
}

async function savePortRouteIfNeeded() {
  const id = await savePortRoute(buildPortRoutePayload())
  s.savedPortRouteId = id
}

function onExistingTemplatePicked(id) {
  s.savedTemplateId = id
  syncTemplateToRoute()
}

async function syncTemplateToRoute() {
  if (!s.savedPortRouteId || !s.savedTemplateId) return
  await savePortRoute({
    id: s.savedPortRouteId,
    channelCode: s.sourceChannelCode,
    portAddress: s.portAddress,
    portMethod: s.portMethod,
    timeout: s.timeout,
    retryCount: s.retryCount,
    functionCode: s.functionCode,
    functionName: s.functionName,
    headerConfig: s.headerConfig,
    requestTemplateId: s.savedTemplateId
  })
}

async function saveNewTemplateAndBind() {
  const tplId = await saveTemplate({
    name: s.newTemplateDraft.name || `${s.functionCode}_TPL_${new Date().toISOString().slice(0, 10).replace(/-/g, '')}`,
    srcFormat: s.sourceFormat,
    targetFormat: s.targetFormat,
    mappingRule: JSON.stringify(s.newTemplateDraft.mappingRules),
    functionCode: s.functionCode
  })
  s.savedTemplateId = tplId
  await syncTemplateToRoute()
}

function fillExample() {
  if (s.newTemplateDraft.mappingRules.length) {
    const obj = {}
    for (const r of s.newTemplateDraft.mappingRules) obj[r.src] = 'example'
    s.testInput = JSON.stringify(obj, null, 2)
  }
}

async function runTest() {
  testing.value = true
  s.testOutput = ''
  s.testError = ''
  try {
    const payload = {
      message: s.testInput,
      srcFormat: s.sourceFormat,
      targetFormat: s.targetFormat,
      templateId: s.savedTemplateId
    }
    const res = s.testMode === 'SIMULATE'
      ? await request.post('/convert', payload)
      : await request.post('/dispatch', { channelCode: s.sourceChannelCode, message: s.testInput })
    s.testOutput = typeof res === 'string' ? res : JSON.stringify(res, null, 2)
  } catch (e) {
    s.testError = e?.message || '测试失败'
  } finally {
    testing.value = false
  }
}

function skipTestToPublish() {
  s.testOutput = '(已跳过测试)'
}

async function onSubmit() {
  if (s.templateMode === 'NEW' && !s.savedTemplateId) await saveNewTemplateAndBind()
  s.reset()
  router.push('/convert/port-route')
}

defineExpose({ validateStep, savePortRouteIfNeeded, buildPortRoutePayload, onSubmit })
</script>
