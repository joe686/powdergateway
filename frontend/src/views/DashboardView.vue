<template>
  <div class="overview-page">
    <!-- 维度切换 -->
    <div class="dimension-bar">
      <el-radio-group v-model="dimension" @change="reload">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="month">本月</el-radio-button>
      </el-radio-group>
    </div>

    <!-- UX-A UI-03: KPI 5 卡改 grid 均分，与下方模块最右像素严格对齐 -->
    <div class="kpi-grid">
      <div v-for="card in statCards" :key="card.title" class="kpi-card">
        <div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-title">{{ card.title }}</div>
        </div>
        <el-icon class="stat-icon" :style="{ color: card.color }">
          <component :is="card.icon" />
        </el-icon>
      </div>
    </div>

    <!-- 调用趋势折线图（全宽） -->
    <el-card shadow="never" class="chart-card">
      <template #header><span>调用趋势</span></template>
      <v-chart :option="trendOption" autoresize style="height: 300px" />
    </el-card>

    <!-- opType 柱图 + 接口状态饼图 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span>操作类型分布</span></template>
          <v-chart :option="opTypeOption" autoresize style="height: 260px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span>接口状态分布</span></template>
          <v-chart :option="statusOption" autoresize style="height: 260px" />
        </el-card>
      </el-col>
    </el-row>

    <!-- TOP5 慢接口 + 未处理告警 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span>TOP 5 慢接口</span></template>
          <el-table :data="topSlow" size="small" style="width: 100%">
            <el-table-column label="接口名称" prop="interfaceName" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.interfaceName || `接口#${row.interfaceId}` || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="平均耗时(ms)" prop="avgCostMs" width="120" />
            <el-table-column label="调用次数" prop="callCount" width="100" />
          </el-table>
          <el-empty v-if="!topSlow.length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span>未处理告警</span>
            <el-badge v-if="activeAlerts.length" :value="activeAlerts.length" type="danger" class="alert-badge" />
          </template>
          <div v-if="activeAlerts.length" class="alert-list">
            <div v-for="a in activeAlerts" :key="a.id" class="alert-item">
              <el-tag type="danger" size="small">{{ a.alertType }}</el-tag>
              <span class="alert-msg">{{ a.message }}</span>
              <span class="alert-time">{{ formatTime(a.checkTime) }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无未处理告警" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-card shadow="never" class="shortcut-card">
      <span class="shortcut-title">快捷入口</span>
      <el-button type="primary" @click="$router.push('/interface/wizard')">新建接口</el-button>
      <el-button @click="$router.push('/convert/format')">格式转换</el-button>
      <el-button @click="$router.push('/tools/debug')">报文调试</el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent, TooltipComponent, LegendComponent, TitleComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import { getOverview } from '@/api/home'

use([CanvasRenderer, LineChart, BarChart, PieChart,
     GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const dimension = ref('today')
const data = ref(null)
let timer = null

async function reload() {
  try {
    data.value = await getOverview(dimension.value)
  } catch (_) {
    // 保留上次数据，30s 自动重试
  }
}

function startPolling() {
  if (timer) return
  timer = setInterval(reload, 30_000)
}
function stopPolling() {
  if (timer) { clearInterval(timer); timer = null }
}
function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    reload()
    startPolling()
  } else {
    stopPolling()
  }
}

onMounted(() => {
  reload()
  startPolling()
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

// ---------- 数字卡片 ----------
const statCards = computed(() => {
  const s = data.value
  const cs = s?.callStats ?? {}
  const is = s?.interfaceStats ?? {}
  return [
    { title: '总接口数',   value: is.total     ?? 0,   icon: 'Connection',   color: '#1890ff' },
    { title: '已发布',     value: is.published ?? 0,   icon: 'Share',        color: '#52c41a' },
    { title: `${dimLabel.value}调用次数`, value: cs.totalCalls ?? 0, icon: 'TrendCharts', color: '#722ed1' },
    { title: `${dimLabel.value}成功率`,   value: cs.totalCalls ? `${cs.successRate}%` : '—', icon: 'CircleCheck', color: '#13c2c2' },
    { title: '缓存命中率', value: cs.cacheHitRate != null ? `${cs.cacheHitRate}%` : '—', icon: 'Coin', color: '#faad14' }
  ]
})

const dimLabel = computed(() => ({ today: '今日', week: '本周', month: '本月' }[dimension.value] ?? '今日'))

// ---------- 调用趋势折线图 ----------
const trendOption = computed(() => {
  const t = data.value?.callTrend ?? { timeline: [], successCounts: [], failCounts: [] }
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功', '失败'] },
    grid: { left: 40, right: 20, bottom: 30, top: 40 },
    xAxis: { type: 'category', data: t.timeline },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '成功', type: 'line', smooth: true, data: t.successCounts, itemStyle: { color: '#52c41a' } },
      { name: '失败', type: 'line', smooth: true, data: t.failCounts,    itemStyle: { color: '#ff4d4f' } }
    ]
  }
})

// ---------- opType 柱图 ----------
const opTypeOption = computed(() => {
  const rows = data.value?.opTypeDistribution ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: { type: 'category', data: rows.map(r => r.opType) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: rows.map(r => r.count),
      itemStyle: { color: '#1890ff' }
    }]
  }
})

// ---------- 接口状态饼图 ----------
const statusOption = computed(() => {
  const is = data.value?.interfaceStats ?? {}
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { name: '草稿',   value: is.draft     ?? 0, itemStyle: { color: '#909399' } },
        { name: '已发布', value: is.published ?? 0, itemStyle: { color: '#52c41a' } },
        { name: '已禁用', value: is.disabled  ?? 0, itemStyle: { color: '#ff4d4f' } }
      ]
    }]
  }
})

// ---------- 慢接口 & 告警 ----------
const topSlow     = computed(() => data.value?.topSlowInterfaces ?? [])
const activeAlerts = computed(() => data.value?.activeAlerts ?? [])

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.overview-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.dimension-bar {
  display: flex;
  justify-content: flex-end;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.kpi-card {
  background: var(--pg-glass-bg);
  backdrop-filter: blur(var(--pg-glass-blur));
  border: 1px solid var(--pg-glass-border);
  border-radius: var(--pg-radius-md);
  box-shadow: var(--pg-glass-shadow);
  padding: 18px 20px;
  display: flex; align-items: center; justify-content: space-between;
  transition: transform 0.2s, box-shadow 0.2s;
}
.kpi-card:hover { transform: translateY(-2px); }
.stat-value { font-size: 24px; font-weight: 700; letter-spacing: -0.5px; color: var(--pg-text-white); }
.stat-title { font-size: 12.5px; color: var(--pg-text-secondary); margin-top: 6px; }
.stat-icon {
  font-size: 44px;
  opacity: 0.2;
}
.chart-card {
  border-radius: 8px;
}
.shortcut-card {
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.shortcut-title {
  font-weight: 600;
  color: #303133;
  margin-right: 8px;
}
.alert-badge {
  margin-left: 8px;
}
.alert-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 220px;
  overflow-y: auto;
}
.alert-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fff1f0;
  border-radius: 4px;
  border-left: 3px solid #ff4d4f;
}
.alert-msg {
  flex: 1;
  font-size: 13px;
  color: #cf1322;
}
.alert-time {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}
</style>
