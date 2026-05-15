<template>
  <div class="wizard-page">
    <!-- 页头 -->
    <div class="wizard-header">
      <span class="wizard-title">接口配置向导</span>
      <div class="wizard-header-right">
        <span class="draft-hint" v-if="draftSaved">草稿已自动保存</span>
        <el-button size="small" @click="goList">返回列表</el-button>
      </div>
    </div>

    <!-- 步骤条 -->
    <el-steps
      :active="wizard.currentStep"
      finish-status="success"
      align-center
      class="wizard-steps"
    >
      <el-step v-for="s in visibleSteps" :key="s.key" :title="s.label" />
    </el-steps>

    <!-- 内容区 -->
    <el-card class="wizard-content">
      <template #header>
        <div class="step-header">
          <span>Step {{ wizard.currentStep + 1 }} · {{ currentStepDef.label }}</span>
          <el-tooltip :content="currentStepDef.tip" placement="left">
            <span class="help-btn">?</span>
          </el-tooltip>
        </div>
      </template>

      <!-- 占位：各步骤内容将在后续 Task 中填充 -->
      <div v-for="s in visibleSteps" :key="s.key">
        <div v-show="isActive(s.key)">
          <p style="color:#909399">步骤 {{ s.label }} 内容开发中</p>
        </div>
      </div>
    </el-card>

    <!-- 底部导航 -->
    <div class="wizard-footer">
      <el-button :disabled="wizard.currentStep === 0" @click="prevStep">← 上一步</el-button>
      <span class="step-counter">步骤 {{ wizard.currentStep + 1 }} / {{ visibleSteps.length }}</span>
      <el-button
        v-if="!isLastStep"
        type="primary"
        @click="nextStep"
      >
        下一步 →
      </el-button>
      <el-button
        v-else
        type="success"
        @click="nextStep"
      >
        完成
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useWizardStore } from '@/store/wizard'

const router = useRouter()
const wizard = useWizardStore()
const draftSaved = ref(false)

// ─── 步骤定义 ──────────────────────────────────────────────────────────────
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

const currentStepDef = computed(() =>
  visibleSteps.value[wizard.currentStep] ?? STEP_DEFS[0]
)

const isLastStep = computed(() =>
  wizard.currentStep === visibleSteps.value.length - 1
)

function isActive(key) {
  return currentStepDef.value.key === key
}

// ─── 草稿持久化 ───────────────────────────────────────────────────────────
watch(
  () => wizard.$state,
  () => {
    wizard.persist()
    draftSaved.value = true
  },
  { deep: true }
)

// ─── 初始化：恢复草稿 ─────────────────────────────────────────────────────
onMounted(async () => {
  if (wizard.hasDraft() && wizard.interfaceType === '') {
    try {
      await ElMessageBox.confirm(
        '发现未完成的向导配置，是否恢复？',
        '恢复草稿',
        { confirmButtonText: '恢复', cancelButtonText: '重新开始', type: 'info' }
      )
      wizard.loadDraft()
    } catch {
      wizard.reset()
    }
  }
})

// ─── 步骤导航 ─────────────────────────────────────────────────────────────
function prevStep() {
  if (wizard.currentStep > 0) wizard.currentStep--
}

function nextStep() {
  wizard.currentStep++
}

function goList() {
  router.push('/interface/list')
}
</script>

<style scoped>
.wizard-page {
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
