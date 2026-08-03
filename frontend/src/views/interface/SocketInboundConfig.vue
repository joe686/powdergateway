<template>
  <div class="socket-inbound-config">
    <div class="header">
      <h2>{{ pageTitle }}</h2>
      <div>
        <el-button @click="goList">返回列表</el-button>
        <el-button type="primary" @click="doSave" :loading="saving">保存</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="max-width:820px;margin-bottom:16px">
      <template #title>入站(渠道方发起)TCP Socket 接口</template>
      <div style="font-size:12px;line-height:1.6">
        · 保存并<b>发布</b>后 · Manager 会自动拉起 Netty ServerSocket 监听指定端口(v0.3.9 CHG-049 · publish/disable 即时生效)<br />
        · 接收报文按分帧切帧 · dom4j 手工递归提取 XPath 位置(默认 //FunctionId)<br />
        · 命中后走 v0.3.1 双层路由(FN-12 字典 scope=3)+ 出站段 applicationName 走 REG-1 discover 转 PG 内部 HTTP 服务<br />
        · 全链路 trace_id 贯穿 sys_log / sql_audit_log / perf_stat 三表(v0.3.1)
      </div>
    </el-alert>

    <el-form :model="form" label-width="130px" style="max-width:820px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-form-item label="接口名称" required>
        <el-input v-model="form.name" placeholder="如 lcpt-bank 入站监听" />
      </el-form-item>
      <el-form-item label="PG 功能号">
        <el-input v-model="form.functionId" clearable placeholder="选填 · 用作路由源命中(CR-007)" style="max-width:420px" />
        <div style="font-size:12px;color:#909399">入站场景下 PG 功能号一般不填(路由由入站 XPath 提取动态命中)</div>
      </el-form-item>
      <el-form-item label="日志记录">
        <el-switch v-model="form.logEnabled" />
      </el-form-item>

      <el-divider content-position="left">监听配置(Netty ServerSocket)</el-divider>
      <el-form-item label="监听端口" required>
        <el-input-number v-model="inbound.port" :min="1" :max="65535" style="width:200px" />
        <span style="margin-left:8px;color:#F56C6C;font-size:12px">发布后立即抢占 · 需确保端口未被占用</span>
      </el-form-item>
      <el-form-item label="分帧方式" required>
        <el-select v-model="inbound.framing" style="width:280px">
          <el-option label="XML 边界(根标签自闭合)" value="XML_BOUNDARY" />
          <el-option label="4 字节大端长度前缀" value="LENGTH_PREFIX_BE4" />
          <el-option label="8 字节大端长度前缀" value="LENGTH_PREFIX_BE8" />
        </el-select>
      </el-form-item>
      <el-form-item label="报文编码" required>
        <el-radio-group v-model="inbound.charset">
          <el-radio-button value="UTF-8" />
          <el-radio-button value="GBK" />
        </el-radio-group>
      </el-form-item>
      <el-form-item label="连接模式">
        <el-radio-group v-model="inbound.connectionMode">
          <el-radio-button value="short">短连接</el-radio-button>
        </el-radio-group>
        <span style="margin-left:8px;color:#909399;font-size:12px">仅 short 实装 · long/pooled 预留(Q20=C)</span>
      </el-form-item>
      <el-form-item label="读超时(ms)">
        <el-input-number v-model="inbound.readTimeoutMs" :min="1000" :max="300000" :step="5000" style="width:200px" />
      </el-form-item>
      <el-form-item label="最大并发连接">
        <el-input-number v-model="inbound.maxConnections" :min="1" :max="10000" :step="10" style="width:200px" />
      </el-form-item>

      <el-divider content-position="left">路由提取</el-divider>
      <el-form-item label="功能号 XPath">
        <el-input v-model="inbound.functionIdXPath" placeholder="//FunctionId(默认 · 手工简化 XPath · bank 与 host 均在此位置)" style="max-width:420px" />
        <div style="font-size:12px;color:#909399">
          · 默认 //FunctionId(等价 dom4j selectSingleNode)· 后端 InboundSocketOrchestrator 手工递归查找不依赖 jaxen<br />
          · 从入站 XML 报文提取此位置的功能号值 · 走双层路由映射到 PG 内部功能号并执行
        </div>
      </el-form-item>

      <!-- v0.3.10 · outbound 段(必填 · 之前 v0.3.7 遗漏 UI · 后端 validateInboundSocketConfig 强校验 applicationName + path) -->
      <el-divider content-position="left">出站转发目标(必填)</el-divider>
      <el-form-item label="应用名" required>
        <el-input v-model="outbound.applicationName" placeholder="如 pg-internal · 走 REG-1 discover(Eureka/Nacos)" style="max-width:420px" />
        <div style="font-size:12px;color:#909399">Eureka/Nacos 注册中心里的服务名 · 大小写需与注册端一致</div>
      </el-form-item>
      <el-form-item label="转发路径" required>
        <el-input v-model="outbound.path" placeholder="如 /api/exec/{{fnId}} 或 /api/route" style="max-width:420px" />
        <div style="font-size:12px;color:#909399">HTTP POST 目标 path · 支持 {{fnId}} 变量占位 · Orchestrator 自动拼 http://{ip}:{port}{path}</div>
      </el-form-item>
      <el-form-item label="请求超时(ms)">
        <el-input-number v-model="outbound.timeoutMs" :min="500" :max="120000" :step="1000" style="width:200px" />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { saveInterface, getInterface } from '@/api/interface'

const router = useRouter()
const route = useRoute()

const form = reactive({
  id: null,
  name: '',
  functionId: '',
  logEnabled: true,
})

const inbound = reactive({
  port: 9099,
  framing: 'XML_BOUNDARY',
  charset: 'UTF-8',
  connectionMode: 'short',
  readTimeoutMs: 30000,
  maxConnections: 100,
  functionIdXPath: '//FunctionId',
})

// v0.3.10 CHG-052 · outbound 段(v0.3.7 遗漏 UI · 后端 validateInboundSocketConfig 强校验)
const outbound = reactive({
  applicationName: 'pg-internal',
  path: '/api/exec/{fnId}',
  timeoutMs: 10000,
})

const saving = ref(false)

const pageTitle = computed(() => form.id ? `编辑 INBOUND_SOCKET 入站接口 #${form.id} · ${form.name || '(未命名)'}` : '新建 INBOUND_SOCKET 入站接口(TCP)')

async function loadForEdit(id) {
  try {
    const cfg = await getInterface(id)
    if (cfg.type !== 'INBOUND_SOCKET') {
      ElMessage.warning('该接口类型不是 INBOUND_SOCKET')
    }
    form.id = cfg.id
    form.name = cfg.name || ''
    form.functionId = cfg.functionId || ''
    form.logEnabled = cfg.logEnabled === 1 || cfg.logEnabled === true
    if (cfg.configJson) {
      const j = JSON.parse(cfg.configJson)
      Object.assign(inbound, j.inbound || {})
      Object.assign(outbound, j.outbound || {})
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
    type: 'INBOUND_SOCKET',
    logEnabled: form.logEnabled,
    configJson: JSON.stringify({ inbound: { ...inbound }, outbound: { ...outbound } }),
  }
}

async function doSave() {
  if (!form.name.trim()) { ElMessage.warning('请填接口名称'); return }
  if (!inbound.port || !inbound.framing || !inbound.charset) {
    ElMessage.warning('监听端口/分帧/编码必填')
    return
  }
  if (!outbound.applicationName || !outbound.path) {
    ElMessage.warning('出站 applicationName/path 必填(后端强校验)')
    return
  }
  saving.value = true
  try {
    const id = await saveInterface(buildPayload())
    form.id = id
    ElMessage.success('保存成功 · 接口 ID #' + id + ' · 需在列表中手动发布以启动 ServerSocket')
  } finally {
    saving.value = false
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
.socket-inbound-config { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.header h2 { margin: 0; }
</style>
