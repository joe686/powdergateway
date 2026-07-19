<template>
  <div class="wizard-shell">
    <div class="wizard-header">
      <span class="wizard-title">{{ title }}</span>
      <div class="wizard-header-right">
        <span v-if="draftSaved" class="draft-hint">草稿已自动保存</span>
        <slot name="header-right">
          <el-button size="small" @click="$emit('back')">返回列表</el-button>
        </slot>
      </div>
    </div>
    <el-steps :active="currentStep" finish-status="success" align-center class="wizard-steps">
      <el-step v-for="s in visibleSteps" :key="s.key" :title="s.label" />
    </el-steps>
    <el-card class="wizard-content">
      <template #header>
        <div class="step-header">
          <span>Step {{ currentStep + 1 }} · {{ currentStepDef.label }}</span>
          <el-tooltip v-if="currentStepDef.tip" :content="currentStepDef.tip" placement="left">
            <span class="help-btn">?</span>
          </el-tooltip>
        </div>
      </template>
      <slot :current-key="currentStepDef.key" :is-active="isActive" />
    </el-card>
    <div class="wizard-footer">
      <el-button :disabled="currentStep === 0" @click="onPrev">← 上一步</el-button>
      <span class="step-counter">步骤 {{ currentStep + 1 }} / {{ visibleSteps.length }}</span>
      <slot name="footer-extra" />
      <el-button v-if="!isLastStep" type="primary" @click="onNext">下一步 →</el-button>
      <el-button v-else type="success" @click="onSubmit">{{ submitLabel }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  title: { type: String, required: true },
  steps: { type: Array, required: true },
  currentStep: { type: Number, required: true },
  draftSaved: { type: Boolean, default: false },
  submitLabel: { type: String, default: '完成' },
  validateNext: { type: Function, default: null }
})

const emit = defineEmits(['update:currentStep', 'submit', 'back'])

const visibleSteps = computed(() =>
  (props.steps || []).filter(s => !(typeof s.skipWhen === 'function' && s.skipWhen()))
)

// CHG-011 E2E-6 铁律：默认对象兜底，保证 .label/.tip 深引用不崩溃
const currentStepDef = computed(() =>
  visibleSteps.value[props.currentStep] ?? { key: '', label: '', tip: '' }
)

const isLastStep = computed(() =>
  visibleSteps.value.length > 0 && props.currentStep === visibleSteps.value.length - 1
)

function isActive(key) {
  return currentStepDef.value.key === key
}

function onNext() {
  const res = props.validateNext ? props.validateNext(currentStepDef.value.key) : true
  if (res === false) return
  if (typeof res === 'string') {
    ElMessage.warning(res)
    return
  }
  if (props.currentStep < visibleSteps.value.length - 1) {
    emit('update:currentStep', props.currentStep + 1)
  }
}

function onPrev() {
  if (props.currentStep > 0) {
    emit('update:currentStep', props.currentStep - 1)
  }
}

function onSubmit() {
  const res = props.validateNext ? props.validateNext(currentStepDef.value.key) : true
  if (res === false) return
  if (typeof res === 'string') {
    ElMessage.warning(res)
    return
  }
  emit('submit')
}
</script>

<style scoped>
.wizard-shell {
  padding: 20px;
}
.wizard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.wizard-title {
  font-size: 18px;
  font-weight: 600;
}
.wizard-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.draft-hint {
  font-size: 12px;
  color: #67C23A;
}
.wizard-steps {
  margin-bottom: 20px;
}
.wizard-content {
  min-height: 300px;
  margin-bottom: 20px;
}
.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.help-btn {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #f0f2f5;
  border: 1px solid #ddd;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
}
.wizard-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
}
.step-counter {
  font-size: 13px;
  color: #909399;
}
</style>
