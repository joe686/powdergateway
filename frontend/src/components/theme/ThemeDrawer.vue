<template>
  <el-drawer
    v-model="innerVisible"
    size="380px"
    :with-header="false"
    direction="rtl"
    :append-to-body="true"
  >
    <div class="pg-drawer">
      <div class="pg-drawer-head">
        <h2>主题设置</h2>
        <span class="pg-drawer-close" @click="innerVisible = false">×</span>
      </div>
      <div class="pg-drawer-body">

        <div class="pg-section-title">切换模式</div>
        <div class="pg-mode-list">
          <div class="pg-mode" :class="{ active: pref.mode === 'manual' }" @click="setMode('manual')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">☀ / ☾ 手动切换</div>
              <div class="pg-mode-desc">顶栏按钮点一下就切。</div>
            </div>
          </div>

          <div class="pg-mode" :class="{ active: pref.mode === 'schedule' }" @click="setMode('schedule')">
            <div class="pg-radio" />
            <div style="flex:1">
              <div class="pg-mode-name">⏰ 定时切换</div>
              <div class="pg-mode-desc">按固定时间点自动切主题。</div>
              <div v-if="pref.mode === 'schedule'" class="pg-schedule-inputs">
                <div>切亮色：<el-time-picker v-model="lightAt" format="HH:mm" size="small" @change="onScheduleChange" /></div>
                <div>切暗色：<el-time-picker v-model="darkAt" format="HH:mm" size="small" @change="onScheduleChange" /></div>
              </div>
            </div>
          </div>

          <div class="pg-mode" :class="{ active: pref.mode === 'system' }" @click="setMode('system')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">⚙ 跟随系统</div>
              <div class="pg-mode-desc">读取操作系统 prefers-color-scheme。</div>
            </div>
          </div>

          <div class="pg-mode" :class="{ active: pref.mode === 'sun' }" @click="setMode('sun')">
            <div class="pg-radio" />
            <div style="flex:1">
              <div class="pg-mode-name">☼ 日出日落</div>
              <div class="pg-mode-desc">按当前定位的日出日落时间切换。</div>
              <div v-if="pref.mode === 'sun'" class="pg-location-hint">
                当前定位：<b>北京</b>（不可修改）
              </div>
            </div>
          </div>
        </div>

        <div class="pg-section-title">应用范围</div>
        <div class="pg-mode-list">
          <div class="pg-mode" :class="{ active: pref.storage === 'local' }" @click="setStorage('local')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">仅本设备（localStorage）</div>
              <div class="pg-mode-desc">换设备需重设。</div>
            </div>
          </div>
          <div class="pg-mode" :class="{ active: pref.storage === 'account' }" @click="setStorage('account')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">跟随账号（后端存储）</div>
              <div class="pg-mode-desc">保存到用户配置，换设备同步。</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useTheme } from '@/composables/useTheme'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible'])
const innerVisible = computed({
  get: () => props.visible,
  set: v => emit('update:visible', v)
})

const { pref, setMode: apiSetMode, setSchedule } = useTheme()

// 时间 picker 需要 Date 对象；防守：schedule 可能为 undefined 或字段缺失
const lightAt = ref(strToDate(pref.value?.schedule?.lightAt))
const darkAt = ref(strToDate(pref.value?.schedule?.darkAt))

function strToDate(hhmm) {
  const s = (typeof hhmm === 'string' && hhmm.includes(':')) ? hhmm : '07:00'
  const [h, m] = s.split(':').map(Number)
  const d = new Date(); d.setHours(h || 0, m || 0, 0, 0); return d
}
function dateToStr(d) {
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
function setMode(mode) { apiSetMode(mode) }
function onScheduleChange() {
  setSchedule({ lightAt: dateToStr(lightAt.value), darkAt: dateToStr(darkAt.value) })
}
function setStorage(s) { apiSetMode(pref.value.mode, { storage: s }) }
</script>

<style scoped>
.pg-drawer { height: 100%; display: flex; flex-direction: column; }
.pg-drawer-head { padding: 18px 20px; border-bottom: 1px solid var(--pg-line); display: flex; justify-content: space-between; align-items: center; }
.pg-drawer-head h2 { margin: 0; font-size: 16px; color: var(--pg-text-white); }
.pg-drawer-close { cursor: pointer; color: var(--pg-text-secondary); font-size: 22px; padding: 0 6px; }
.pg-drawer-body { padding: 18px 20px; overflow-y: auto; flex: 1; }
.pg-section-title { font-size: 12px; color: var(--pg-text-placeholder); letter-spacing: 1px; margin: 22px 0 10px; font-weight: 500; }
.pg-section-title:first-child { margin-top: 0; }
.pg-mode-list { display: flex; flex-direction: column; gap: 8px; }
.pg-mode { padding: 12px 14px; border-radius: 12px; border: 1.5px solid var(--pg-line-strong); background: var(--pg-hover-surface); cursor: pointer; display: flex; gap: 12px; align-items: flex-start; }
.pg-mode.active { border-color: var(--pg-primary); background: var(--pg-primary-soft); }
.pg-radio { flex-shrink: 0; width: 16px; height: 16px; border-radius: 50%; border: 2px solid var(--pg-text-placeholder); margin-top: 2px; position: relative; }
.pg-mode.active .pg-radio { border-color: var(--pg-primary); }
.pg-mode.active .pg-radio::after { content: ""; position: absolute; inset: 2px; border-radius: 50%; background: var(--pg-primary); }
.pg-mode-name { font-size: 14px; font-weight: 600; color: var(--pg-text-white); }
.pg-mode-desc { font-size: 12px; color: var(--pg-text-secondary); margin-top: 4px; }
.pg-schedule-inputs { margin-top: 10px; padding: 10px 12px; background: var(--pg-hover-surface); border-radius: 10px; display: flex; flex-direction: column; gap: 8px; font-size: 12.5px; }
.pg-location-hint { margin-top: 8px; font-size: 12px; color: var(--pg-text-secondary); }
.pg-location-hint b { color: var(--pg-text-white); }
</style>
