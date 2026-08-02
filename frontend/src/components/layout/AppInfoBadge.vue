<template>
  <el-popover placement="right-end" trigger="click" :width="240">
    <template #reference>
      <span class="app-info-trigger" :title="triggerTitle">
        <el-icon><InfoFilled /></el-icon>
        <span class="app-info-ver">{{ info.version || 'v?' }}</span>
      </span>
    </template>
    <div class="app-info-card">
      <div class="row"><span class="label">程序:</span><span class="val">PowerGateway {{ info.version }}</span></div>
      <div class="row"><span class="label">作者:</span><span class="val">{{ info.author }}</span></div>
      <div class="row"><span class="label">日期:</span><span class="val">{{ info.currentDate }}</span></div>
      <div v-if="info.gitSha" class="row"><span class="label">Build:</span><span class="val mono">{{ info.gitSha }}</span></div>
      <div v-if="info.buildTime" class="row"><span class="label">构建:</span><span class="val mono">{{ info.buildTime }}</span></div>
      <div class="row note">{{ info.releaseNote || '当前仅为测试版本' }}</div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { getAppInfo } from '@/api/appInfo'

const info = ref({
  version: 'v?', author: '光斓', currentDate: '', releaseNote: '当前仅为测试版本'
})
const triggerTitle = computed(() => `PowerGateway ${info.value.version} · ${info.value.author}`)

onMounted(async () => {
  try {
    info.value = await getAppInfo()
  } catch (e) {
    // 免登接口应能读到 · 失败也不影响主流程
  }
})
</script>

<style scoped>
.app-info-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  cursor: pointer;
  color: var(--pg-text-secondary);
  font-size: 11px;
  opacity: 0.65;
  transition: opacity 0.15s;
}
.app-info-trigger:hover {
  opacity: 1;
  color: var(--pg-text-primary);
}
.app-info-ver {
  font-family: monospace;
}
.app-info-card {
  font-size: 13px;
  line-height: 1.7;
}
.app-info-card .row {
  display: flex;
  gap: 6px;
}
.app-info-card .label {
  color: var(--pg-text-secondary);
  min-width: 44px;
}
.app-info-card .val {
  color: var(--pg-text-primary);
}
.app-info-card .val.mono {
  font-family: monospace;
  font-size: 12px;
}
.app-info-card .note {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid var(--pg-line-strong);
  color: var(--pg-warning, #E6A23C);
  font-size: 12px;
}
</style>
