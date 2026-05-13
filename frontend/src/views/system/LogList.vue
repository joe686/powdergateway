<template>
  <div class="log-list">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">

      <!-- ─── Tab 1: 操作日志 ─── -->
      <el-tab-pane label="操作日志" name="operation">
        <div class="toolbar">
          <el-input v-model="opForm.operator" placeholder="操作人" clearable style="width:160px" />
          <el-select v-model="opForm.module" placeholder="操作模块" clearable style="width:150px">
            <el-option v-for="m in moduleOptions" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="opForm.level" placeholder="日志级别" clearable style="width:120px">
            <el-option label="INFO"  value="INFO" />
            <el-option label="ERROR" value="ERROR" />
          </el-select>
          <el-date-picker
            v-model="opForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width:340px"
          />
          <el-button type="primary" @click="loadOpLogs">查询</el-button>
          <el-button @click="resetOpForm">重置</el-button>
          <div style="flex:1" />
          <span style="margin-right:8px;font-size:13px;color:#606266">查历史数据</span>
          <el-switch v-model="showHistory" @change="loadOpLogs" />
          <el-button type="success" style="margin-left:12px" @click="handleExport">导出 Excel</el-button>
        </div>

        <el-table :data="opList" stripe border v-loading="opLoading" style="margin-top:14px">
          <el-table-column prop="opTime"   label="时间"   width="175" />
          <el-table-column prop="module"   label="模块"   width="110" />
          <el-table-column prop="action"   label="动作"   width="120" />
          <el-table-column prop="operator" label="操作人" width="100" />
          <el-table-column prop="opIp"     label="IP"    width="130" />
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <el-tag :type="row.level === 'ERROR' ? 'danger' : 'success'" size="small">
                {{ row.level }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="costMs" label="耗时(ms)" width="90" />
          <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
        </el-table>

        <el-pagination
          v-model:current-page="opPage"
          :page-size="opPageSize"
          :total="opTotal"
          layout="total, prev, pager, next"
          style="margin-top:14px;text-align:right"
          @current-change="loadOpLogs"
        />
      </el-tab-pane>

      <!-- ─── Tab 2: SQL 审计 ─── -->
      <el-tab-pane label="SQL 审计" name="audit">
        <div class="toolbar">
          <el-select v-model="auditForm.opType" placeholder="操作类型" clearable style="width:140px">
            <el-option label="INSERT" value="INSERT" />
            <el-option label="UPDATE" value="UPDATE" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
          <el-select v-model="auditForm.result" placeholder="执行结果" clearable style="width:120px">
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAIL"    value="FAIL" />
          </el-select>
          <el-date-picker
            v-model="auditForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width:340px"
          />
          <el-button type="primary" @click="loadAuditLogs">查询</el-button>
          <el-button @click="resetAuditForm">重置</el-button>
        </div>

        <el-table :data="auditList" stripe border v-loading="auditLoading" style="margin-top:14px">
          <el-table-column prop="opTime"     label="时间"     width="175" />
          <el-table-column prop="interfaceId" label="接口ID"  width="80" />
          <el-table-column prop="opType"     label="操作类型" width="90" />
          <el-table-column prop="operator"   label="操作人"   width="100">
            <template #default="{ row }">{{ row.operator || '—' }}</template>
          </el-table-column>
          <el-table-column prop="opIp" label="IP" width="130">
            <template #default="{ row }">{{ row.opIp || '—' }}</template>
          </el-table-column>
          <el-table-column prop="targetDb"    label="目标库"   width="110" />
          <el-table-column prop="targetTable" label="目标表"   width="110" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 'FAIL' ? 'danger' : 'success'" size="small">
                {{ row.result }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sqlText" label="SQL" min-width="200" show-overflow-tooltip />
        </el-table>

        <el-pagination
          v-model:current-page="auditPage"
          :page-size="auditPageSize"
          :total="auditTotal"
          layout="total, prev, pager, next"
          style="margin-top:14px;text-align:right"
          @current-change="loadAuditLogs"
        />
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listLogs, listHistoryLogs, listAuditLogs, exportLogs } from '@/api/log'

// ─── Tab 状态 ──────────────────────────────
const activeTab = ref('operation')

// ─── 操作日志 ──────────────────────────────
const moduleOptions = [
  '认证', '模板管理', '渠道配置', '端口路由', '数据库连接',
  '接口配置', '用户管理', '分库分表', '缓存管理'
]
const opForm = ref({ operator: '', module: '', level: '', timeRange: null })
const showHistory = ref(false)
const opList = ref([])
const opLoading = ref(false)
const opPage = ref(1)
const opPageSize = 20
const opTotal = ref(0)

async function loadOpLogs() {
  opLoading.value = true
  try {
    const params = buildOpParams()
    const fn = showHistory.value ? listHistoryLogs : listLogs
    const data = await fn(params)
    opList.value = data.records || []
    opTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载日志失败')
  } finally {
    opLoading.value = false
  }
}

function buildOpParams() {
  const p = {
    operator: opForm.value.operator || undefined,
    module:   opForm.value.module   || undefined,
    level:    opForm.value.level    || undefined,
    page:     opPage.value,
    size:     opPageSize
  }
  if (opForm.value.timeRange) {
    p.startTime = opForm.value.timeRange[0]?.toISOString()
    p.endTime   = opForm.value.timeRange[1]?.toISOString()
  }
  return p
}

function resetOpForm() {
  opForm.value = { operator: '', module: '', level: '', timeRange: null }
  opPage.value = 1
  showHistory.value = false
  loadOpLogs()
}

async function handleExport() {
  try {
    await exportLogs(buildOpParams())
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

// ─── SQL 审计 ──────────────────────────────
const auditForm = ref({ opType: '', result: '', timeRange: null })
const auditList = ref([])
const auditLoading = ref(false)
const auditPage = ref(1)
const auditPageSize = 20
const auditTotal = ref(0)

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const params = {
      opType: auditForm.value.opType || undefined,
      result: auditForm.value.result || undefined,
      page:   auditPage.value,
      size:   auditPageSize
    }
    if (auditForm.value.timeRange) {
      params.startTime = auditForm.value.timeRange[0]?.toISOString()
      params.endTime   = auditForm.value.timeRange[1]?.toISOString()
    }
    const data = await listAuditLogs(params)
    auditList.value = data.records || []
    auditTotal.value = data.total || 0
  } catch (e) {
    ElMessage.error('加载审计日志失败')
  } finally {
    auditLoading.value = false
  }
}

function resetAuditForm() {
  auditForm.value = { opType: '', result: '', timeRange: null }
  auditPage.value = 1
  loadAuditLogs()
}

function handleTabChange(tab) {
  if (tab === 'audit' && auditList.value.length === 0) loadAuditLogs()
}

onMounted(loadOpLogs)
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
