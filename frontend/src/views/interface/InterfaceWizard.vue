<template>
  <WizardShell
    title="接口配置向导"
    :steps="visibleSteps"
    v-model:current-step="wizard.currentStep"
    :draft-saved="draftSaved"
    :validate-next="onValidateNext"
    @submit="onSubmit"
    @back="goList"
  >
    <template #default="{ isActive }">
      <SelectInterfaceSteps ref="stepsRef" :is-active="isActive" />
    </template>
  </WizardShell>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import WizardShell from '@/components/wizard/WizardShell.vue'
import SelectInterfaceSteps from '@/views/interface/SelectInterfaceSteps.vue'
import { useWizardStore } from '@/store/wizard'
import { useDraft } from '@/composables/useWizardShell'

const router = useRouter()
const wizard = useWizardStore()
const stepsRef = ref(null)
const { draftSaved, promptRestoreDraft } = useDraft(wizard)

const STEP_DEFS = [
  { key: 'type',    label: '接口类型',  skipFor: [],         tip: '选择要创建的接口类型：查询(SELECT)/插入(INSERT)/修改(UPDATE)/删除(DELETE)' },
  { key: 'db',      label: '数据库连接', skipFor: [],         tip: '选择目标数据库连接，并填写接口名称' },
  { key: 'tables',  label: '选表结构',  skipFor: [],         tip: 'SELECT 选主表和关联表；INSERT 选目标表（最多3张）；UPDATE/DELETE 选单表' },
  { key: 'fields',  label: '字段配置',  skipFor: ['DELETE'], tip: 'SELECT 勾选返回字段；INSERT/UPDATE 配置每字段的数据来源' },
  { key: 'shard',   label: '分库分表',  skipFor: [],         tip: '可选：选择分库分表规则，不需要可跳过' },
  { key: 'process', label: '字段加工',  skipFor: ['DELETE'], tip: '可选：配置字段加工规则（截位/补位/大小写等），不需要可跳过' },
  { key: 'cond',    label: '条件配置',  skipFor: ['INSERT'], tip: 'SELECT 可选条件；UPDATE/DELETE 必须配置至少一个含主键的条件' },
  { key: 'log',     label: '日志开关',  skipFor: [],         tip: '是否记录该接口的操作日志（审计用），默认开启' },
  { key: 'preview', label: '预览测试',  skipFor: [],         tip: '自动保存配置并执行预览查询，验证接口逻辑正确' },
  { key: 'publish', label: '保存发布',  skipFor: [],         tip: '填写接口名称后可仅保存草稿或立即发布' },
]

const visibleSteps = computed(() =>
  STEP_DEFS.filter(s => !wizard.interfaceType || !s.skipFor.includes(wizard.interfaceType))
)

function onValidateNext(key) {
  return stepsRef.value ? stepsRef.value.validateStep(key) : true
}

async function onSubmit() {
  if (stepsRef.value) await stepsRef.value.onSubmit()
}

function goList() {
  router.push('/interface/list')
}

onMounted(async () => {
  if (wizard.interfaceType === '') await promptRestoreDraft()
  // UPDATE/DELETE tables[0] 兜底（CHG-011 E2E-6 教训）
  if ((wizard.interfaceType === 'UPDATE' || wizard.interfaceType === 'DELETE') && wizard.tables.length === 0) {
    wizard.tables = [{ tableName: '' }]
  }
})
</script>
