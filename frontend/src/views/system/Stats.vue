<template>
  <div class="stats-page" style="padding: 20px">

    <!-- 图表区 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>接口调用趋势</span>
          <el-radio-group v-model="dimension" @change="loadSummary">
            <el-radio-button label="today">今天</el-radio-button>
            <el-radio-button label="week">本周</el-radio-button>
            <el-radio-button label="month">本月</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <div style="display:flex;gap:20px" v-loading="summaryLoading">
        <v-chart :option="lineOption" style="height:280px;flex:1" autoresize />
        <v-chart :option="barOption"  style="height:280px;flex:1" autoresize />
      </div>
    </el-card>

    <!-- 告警列表 + 配置按钮 -->
    <el-card>
      <template #header>
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span>告警记录</span>
          <el-button type="warning" @click="configVisible = true">配置告警阈值</el-button>
        </div>
      </template>

      <el-table :data="alertList" stripe border v-loading="alertLoading" style="margin-bottom:14px">
        <el-table-column prop="alertType"  label="告警类型"  width="140">
          <template #default="{ row }">
            <el-tag :type="row.alertType === 'FAIL_RATE' ? 'danger' : 'warning'" size="small">
              {{ row.alertType === 'FAIL_RATE' ? '失败率' : '响应时间' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertValue" label="实际值"   width="100" />
        <el-table-column prop="threshold"  label="阈值"     width="100" />
        <el-table-column prop="message"    label="消息"     min-width="200" show-overflow-tooltip />
        <el-table-column prop="checkTime"  label="检查时间" width="175" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.resolved ? 'success' : 'danger'" size="small">
              {{ row.resolved ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="alertPage"
        :page-size="alertPageSize"
        :total="alertTotal"
        layout="total, prev, pager, next"
        style="text-align:right"
        @current-change="loadAlerts"
      />
    </el-card>

    <!-- 告警阈值配置弹窗 -->
    <el-dialog v-model="configVisible" title="告警阈值配置" width="420px" :close-on-click-modal="false">
      <el-form :model="configForm" label-width="130px">
        <el-form-item label="失败率阈值 (%)">
          <el-input-number v-model="configForm.failRate" :min="1" :max="100" :step="1" />
        </el-form-item>
        <el-form-item label="响应时间阈值 (ms)">
          <el-input-number v-model="configForm.responseMs" :min="100" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { getStatsSummary, getAlerts, updateAlertConfig } from '@/api/stats'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const dimension     = ref('today')
const summaryLoading = ref(false)
const summary       = ref({ timeline: [], successCounts: [], failCounts: [], avgCostMs: [] })

const alertList     = ref([])
const alertLoading  = ref(false)
const alertPage     = ref(1)
const alertPageSize = ref(10)
const alertTotal    = ref(0)

const configVisible = ref(false)
const configSaving  = ref(false)
const configForm    = ref({ failRate: 5, responseMs: 1000 })

const lineOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['成功次数', '失败次数'] },
  xAxis: { type: 'category', data: summary.value.timeline },
  yAxis: { type: 'value', name: '次数' },
  series: [
    { name: '成功次数', type: 'line', data: summary.value.successCounts, smooth: true,
      itemStyle: { color: '#67C23A' } },
    { name: '失败次数', type: 'line', data: summary.value.failCounts, smooth: true,
      itemStyle: { color: '#F56C6C' } }
  ]
}))

const barOption = computed(() => ({
  tooltip: { trigger: 'axis', formatter: (p) => p && p[0] ? `${p[0].name}<br/>平均耗时：${p[0].value} ms` : '' },
  xAxis: { type: 'category', data: summary.value.timeline },
  yAxis: { type: 'value', name: '耗时 (ms)' },
  series: [
    { name: '平均响应时间', type: 'bar', data: summary.value.avgCostMs,
      itemStyle: { color: '#409EFF' } }
  ]
}))

async function loadSummary() {
  summaryLoading.value = true
  try {
    summary.value = await getStatsSummary(dimension.value)
  } catch {
    ElMessage.error('加载统计数据失败')
  } finally {
    summaryLoading.value = false
  }
}

async function loadAlerts() {
  alertLoading.value = true
  try {
    const page = await getAlerts(alertPage.value, alertPageSize.value)
    alertList.value  = page.records || []
    alertTotal.value = page.total   || 0
  } catch {
    ElMessage.error('加载告警记录失败')
  } finally {
    alertLoading.value = false
  }
}

async function saveConfig() {
  configSaving.value = true
  try {
    await updateAlertConfig(configForm.value)
    ElMessage.success('配置已更新')
    configVisible.value = false
  } finally {
    configSaving.value = false
  }
}

onMounted(() => {
  loadSummary()
  loadAlerts()
})
</script>
