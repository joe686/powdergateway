<template>
  <WizardShell
    title="接口转换配置向导"
    :steps="visibleSteps"
    v-model:current-step="s.currentStep"
    :draft-saved="draftSaved"
    :validate-next="onValidateNext"
    submit-label="保存并启用"
    @submit="onSubmit"
    @back="goList"
  >
    <template #default="{ isActive }">
      <TransformInterfaceSteps ref="stepsRef" :is-active="isActive" />
    </template>
  </WizardShell>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WizardShell from '@/components/wizard/WizardShell.vue'
import TransformInterfaceSteps from '@/views/convert/TransformInterfaceSteps.vue'
import { useTransformWizardStore } from '@/store/transformWizard'
import { useDraft } from '@/composables/useWizardShell'

const router = useRouter()
const s = useTransformWizardStore()
const stepsRef = ref(null)
const { draftSaved, promptRestoreDraft } = useDraft(s)

const visibleSteps = [
  { key: 'system',   label: '选择系统',   tip: '定义来源系统 → 目标系统与报文格式' },
  { key: 'function', label: '功能号',     tip: '给这次转换指定一个稳定业务标识' },
  { key: 'port',     label: '端口配置',   tip: '目标系统物理接入点' },
  { key: 'route',    label: '路由绑定',   tip: '把渠道+功能号+端口绑定成一条 port_route' },
  { key: 'template', label: '转换模板',   tip: '选或新建报文转换规则（映射+加工）' },
  { key: 'test',     label: '测试',       tip: '端到端跑通转换链路' },
  { key: 'publish',  label: '发布',       tip: '汇总回显 + 一键启用' }
]

async function onValidateNext(key) {
  if (!stepsRef.value) return true
  const res = stepsRef.value.validateStep(key)
  if (res !== true) return res
  if (key === 'route') {
    try { await stepsRef.value.savePortRouteIfNeeded() }
    catch { return '路由保存失败，请重试' }
  }
  return true
}

async function onSubmit() {
  if (stepsRef.value?.onSubmit) await stepsRef.value.onSubmit()
  else { s.reset(); router.push('/convert/port-route') }
  ElMessage.success('转换配置已启用')
}

function goList() { router.push('/convert/port-route') }

onMounted(async () => { if (!s.functionCode) await promptRestoreDraft() })
</script>
