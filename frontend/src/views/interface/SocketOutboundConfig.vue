<template>
  <div class="socket-outbound-config">
    <div class="header">
      <h2>{{ pageTitle }}</h2>
      <div>
        <el-button @click="goList">返回列表</el-button>
        <el-button type="primary" plain @click="doTestConnect" :loading="testing">测试连接</el-button>
        <el-button type="primary" @click="doSave" :loading="saving">保存</el-button>
      </div>
    </div>

    <el-form :model="form" label-width="130px" style="max-width:820px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-form-item label="接口名称" required>
        <el-input v-model="form.name" placeholder="如 lcpt-host 组合宝查询出站" />
      </el-form-item>
      <el-form-item label="PG 功能号">
        <el-input v-model="form.functionId" clearable placeholder="选填 · 建议 PG- 前缀 · 全局唯一(CR-007)" style="max-width:420px" />
      </el-form-item>
      <el-form-item label="日志记录">
        <el-switch v-model="form.logEnabled" />
      </el-form-item>

      <el-divider content-position="left">目标连接(Netty TCP 客户端)</el-divider>
      <el-form-item label="主机 IP" required>
        <el-input v-model="socket.ip" placeholder="如 127.0.0.1" />
      </el-form-item>
      <el-form-item label="端口" required>
        <el-input-number v-model="socket.port" :min="1" :max="65535" style="width:200px" />
      </el-form-item>
      <el-form-item label="分帧方式" required>
        <el-select v-model="socket.framing" style="width:280px">
          <el-option label="XML 边界(根标签自闭合)" value="XML_BOUNDARY" />
          <el-option label="4 字节大端长度前缀" value="LENGTH_PREFIX_BE4" />
          <el-option label="8 字节大端长度前缀" value="LENGTH_PREFIX_BE8" />
        </el-select>
      </el-form-item>
      <el-form-item label="报文编码" required>
        <el-radio-group v-model="socket.charset">
          <el-radio-button value="UTF-8" />
          <el-radio-button value="GBK" />
        </el-radio-group>
      </el-form-item>
      <el-form-item label="连接模式">
        <el-radio-group v-model="socket.connectionMode">
          <el-radio-button value="short">短连接</el-radio-button>
        </el-radio-group>
        <span style="margin-left:8px;color:#909399;font-size:12px">仅 short 实装 · long/pooled 预留</span>
      </el-form-item>
      <el-form-item label="连接超时(ms)">
        <el-input-number v-model="socket.connTimeoutMs" :min="100" :max="60000" :step="500" style="width:200px" />
      </el-form-item>
      <el-form-item label="读超时(ms)">
        <el-input-number v-model="socket.readTimeoutMs" :min="100" :max="120000" :step="1000" style="width:200px" />
      </el-form-item>

      <el-divider content-position="left">请求/响应模板</el-divider>
      <el-form-item label="请求 XML 模板" required>
        <el-input v-model="socket.requestTemplate" type="textarea" :rows="10" placeholder="XML 模板 · 变量用 ${paramKey} · SocketExecutor 会用请求参数渲染" />
        <div style="font-size:12px;color:#909399">支持 ${xxx} 占位符 · exec 时按传入 params 渲染 · 发出前按分帧封装。</div>
      </el-form-item>
      <el-form-item label="响应扁平化前缀">
        <el-input v-model="socket.responseFlattenPrefix" placeholder="可选 · 如 head.body · 用于剥离响应嵌套" style="max-width:420px" />
        <div style="font-size:12px;color:#909399">留空则整包扁平 · 填 head.body 则仅扁平 //head/body 下的字段。</div>
      </el-form-item>
    </el-form>

    <el-alert v-if="testResult" :type="testResult.ok ? 'success' : 'error'" :closable="false" show-icon style="max-width:820px;margin-top:12px">
      <template #title>{{ testResult.title }}</template>
      <div style="font-size:12px;white-space:pre-wrap">{{ testResult.detail }}</div>
    </el-alert>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { saveInterface, getInterface } from '@/api/interface'

const router = useRouter()
const route = useRoute()

const form = reactive({
  id: null,
  name: '',
  functionId: '',
  logEnabled: true,
})

const socket = reactive({
  ip: '127.0.0.1',
  port: 9001,
  framing: 'XML_BOUNDARY',
  charset: 'UTF-8',
  connectionMode: 'short',
  connTimeoutMs: 3000,
  readTimeoutMs: 10000,
  requestTemplate: '',
  responseFlattenPrefix: '',
})

const saving = ref(false)
const testing = ref(false)
const testResult = ref(null)

const pageTitle = computed(() => form.id ? `编辑 SOCKET 出站接口 #${form.id} · ${form.name || '(未命名)'}` : '新建 SOCKET 出站接口(TCP)')

async function loadForEdit(id) {
  try {
    const cfg = await getInterface(id)
    if (cfg.type !== 'SOCKET') {
      ElMessage.warning('该接口类型不是 SOCKET · 已切到只读')
    }
    form.id = cfg.id
    form.name = cfg.name || ''
    form.functionId = cfg.functionId || ''
    form.logEnabled = cfg.logEnabled === 1 || cfg.logEnabled === true
    if (cfg.configJson) {
      const j = JSON.parse(cfg.configJson)
      Object.assign(socket, j.socket || {})
    }
  } catch (e) {
    ElMessage.error('加载失败:' + (e?.message || e))
  }
}

function buildPayload() {
  return {
    id: form.id || undefined,
    name: form.name,
    functionId: (form.functionId || '').trim() || undefined,
    type: 'SOCKET',
    logEnabled: form.logEnabled,
    configJson: JSON.stringify({ socket: { ...socket } }),
  }
}

async function doSave() {
  if (!form.name.trim()) { ElMessage.warning('请填接口名称'); return }
  if (!socket.ip || !socket.port || !socket.requestTemplate) {
    ElMessage.warning('主机/端口/请求模板必填')
    return
  }
  saving.value = true
  try {
    const id = await saveInterface(buildPayload())
    form.id = id
    ElMessage.success('保存成功 · 接口 ID #' + id + ' · 需在列表中手动发布')
  } finally {
    saving.value = false
  }
}

async function doTestConnect() {
  if (!socket.ip || !socket.port) {
    ElMessage.warning('主机/端口必填')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const res = await request.post('/socket/test-connect', {
      ip: socket.ip,
      port: socket.port,
      connTimeoutMs: socket.connTimeoutMs,
    })
    testResult.value = {
      ok: !!res.reachable,
      title: res.reachable ? `连接成功 · ${socket.ip}:${socket.port}` : `连接失败 · ${socket.ip}:${socket.port}`,
      detail: res.detail || (res.reachable ? `耗时 ${res.elapsedMs || '?'}ms` : '目标端口未监听或超时'),
    }
  } catch (e) {
    testResult.value = { ok: false, title: '测试连接异常', detail: e?.message || String(e) }
  } finally {
    testing.value = false
  }
}

function goList() {
  router.push('/interface/list')
}

onMounted(() => {
  if (route.query.id) {
    loadForEdit(route.query.id)
  }
})
</script>

<style scoped>
.socket-outbound-config { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header h2 { margin: 0; }
</style>
