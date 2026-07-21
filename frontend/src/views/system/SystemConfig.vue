<template>
  <div class="sys-config-page">
    <div class="page-header">
      <span class="title">系统配置</span>
      <div>
        <!-- REG-1 · 手动重新注册本机到所有已启用注册中心 -->
        <el-button
          :loading="reregistering"
          :disabled="!isAdmin"
          @click="confirmReregister"
          style="margin-right: 8px"
        >
          重新注册本机
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!isAdmin"
          @click="handleSave"
        >
          保存
        </el-button>
      </div>
    </div>

    <el-card
      v-for="(items, group) in groupedConfigs"
      :key="group"
      class="group-card"
    >
      <template #header>{{ group }}</template>
      <el-form label-width="220px" label-position="left">
        <el-form-item
          v-for="item in items"
          :key="item.configKey"
          :label="item.configKey"
        >
          <el-input-number
            v-if="item.valueType === 'number'"
            :model-value="Number(form[item.configKey])"
            :min="0"
            @update:model-value="val => form[item.configKey] = String(val)"
          />
          <el-switch
            v-else-if="item.valueType === 'boolean'"
            v-model="form[item.configKey]"
            active-value="true"
            inactive-value="false"
          />
          <el-input
            v-else
            v-model="form[item.configKey]"
            style="width: 240px"
          />
          <span :class="['desc', { 'pg-garbled': isGarbled(item.description) }]">
            {{ sanitize(item.description, '（编码异常，请联系管理员）') }}
          </span>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getAllConfig, updateConfig } from '@/api/sysConfig'
import { sanitize, isGarbled } from '@/utils/textSanitizer'
import { reregisterSelf } from '@/api/registry'

const reregistering = ref(false)
async function confirmReregister() {
  try {
    await ElMessageBox.confirm(
      '确定重新注册本机到所有已启用的注册中心？该操作会向注册中心重新发送本机实例信息。',
      '重新注册确认',
      { type: 'info' }
    )
  } catch { return }
  reregistering.value = true
  try {
    const triggered = await reregisterSelf()
    if (triggered) {
      ElMessage.success('已触发重新注册，各注册中心状态请稍后到「辅助工具 → 注册中心管理」查看')
    } else {
      ElMessage.warning('未启用任何注册中心，请先到「辅助工具 → 注册中心管理」新增')
    }
  } catch (e) {
    ElMessage.error('重新注册失败：' + (e?.message || e))
  } finally {
    reregistering.value = false
  }
}

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'admin')

const configs = ref([])
const form = ref({})
const saving = ref(false)

const groupedConfigs = computed(() => {
  const groups = {}
  configs.value.forEach(item => {
    const g = item.groupName || '其他'
    if (!groups[g]) groups[g] = []
    groups[g].push(item)
  })
  return groups
})

onMounted(async () => {
  const data = await getAllConfig()
  configs.value = data
  const newForm = {}
  data.forEach(item => {
    newForm[item.configKey] = item.configValue ?? ''
  })
  form.value = newForm
})

async function handleSave() {
  saving.value = true
  try {
    await updateConfig(form.value)
    ElMessage.success('配置已保存，立即生效')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.sys-config-page { padding: 20px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.title { font-size: 18px; font-weight: 600; }
.group-card { margin-bottom: 16px; }
.desc { margin-left: 12px; color: var(--pg-text-secondary); font-size: 12px; }
.pg-garbled {
  color: var(--pg-warning, #F59E0B);
  font-style: italic;
}
</style>
