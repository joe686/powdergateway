<template>
  <div class="field-process-page">
    <el-card class="page-card">
      <template #header>
        <span class="card-title">字段加工规则配置</span>
        <el-tooltip content="配置字段加工规则链，每条规则的输出作为下一条规则的输入" placement="right">
          <el-icon class="help-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </template>

      <!-- 输入区 -->
      <el-row :gutter="16" class="input-row">
        <el-col :span="12">
          <el-form-item label="测试输入值">
            <el-input
              v-model="inputValue"
              placeholder="请输入待处理的字段值"
              clearable
              @input="handleInputChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最终输出值">
            <el-input
              :value="finalOutput"
              readonly
              :class="{'output-changed': outputChanged}"
            >
              <template #suffix>
                <el-tag v-if="finalOutput !== null" type="success" size="small">已处理</el-tag>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 规则列表 -->
      <div class="rules-section">
        <div class="rules-header">
          <span class="rules-title">规则链（从上到下依次执行）</span>
          <el-button type="primary" size="small" @click="addRule">
            <el-icon><Plus /></el-icon> 添加规则
          </el-button>
        </div>

        <div v-if="rules.length === 0" class="empty-rules">
          <el-empty description="暂无规则，点击「添加规则」开始配置" :image-size="80" />
        </div>

        <!-- 可拖拽规则列表 -->
        <draggable
          v-model="rules"
          item-key="id"
          handle=".drag-handle"
          animation="200"
          @end="runPreview"
        >
          <template #item="{ element, index }">
            <div class="rule-item">
              <!-- 拖拽手柄 -->
              <div class="drag-handle">
                <el-icon><Rank /></el-icon>
              </div>

              <!-- 步骤序号 -->
              <div class="rule-step">
                <el-tag size="small" type="info">步骤 {{ index + 1 }}</el-tag>
              </div>

              <!-- 规则标识（唯一编号，供其他配置引用） -->
              <el-input
                v-model="element.ruleName"
                placeholder="规则标识"
                size="small"
                style="width: 120px; flex-shrink: 0"
              />

              <!-- 规则类型选择 -->
              <el-select
                v-model="element.type"
                placeholder="选择规则类型"
                style="width: 160px"
                @change="onRuleTypeChange(element)"
              >
                <el-option
                  v-for="rt in ruleTypes"
                  :key="rt.type"
                  :label="rt.label"
                  :value="rt.type"
                />
              </el-select>

              <!-- 动态参数表单 -->
              <div class="rule-params" v-if="element.type">
                <!-- TRIM 参数 -->
                <template v-if="element.type === 'TRIM'">
                  <el-select v-model="element.params.mode" style="width: 140px" @change="runPreview">
                    <el-option label="首尾去空格" value="BOTH" />
                    <el-option label="左侧去空格" value="LEFT" />
                    <el-option label="右侧去空格" value="RIGHT" />
                    <el-option label="去除所有空格" value="ALL" />
                  </el-select>
                </template>

                <!-- SUBSTRING 参数 -->
                <template v-else-if="element.type === 'SUBSTRING'">
                  <el-input-number
                    v-model="element.params.start"
                    :min="0"
                    placeholder="起始位(0)"
                    style="width: 130px"
                    @change="runPreview"
                  />
                  <el-input-number
                    v-model="element.params.length"
                    :min="1"
                    placeholder="截取长度"
                    style="width: 130px; margin-left: 8px"
                    @change="runPreview"
                  />
                </template>

                <!-- PAD 参数 -->
                <template v-else-if="element.type === 'PAD'">
                  <el-select v-model="element.params.direction" style="width: 100px" @change="runPreview">
                    <el-option label="左补" value="LEFT" />
                    <el-option label="右补" value="RIGHT" />
                  </el-select>
                  <el-input
                    v-model="element.params.char"
                    placeholder="填充字符"
                    maxlength="1"
                    style="width: 80px; margin: 0 8px"
                    @input="runPreview"
                  />
                  <el-input-number
                    v-model="element.params.length"
                    :min="1"
                    placeholder="目标长度"
                    style="width: 110px"
                    @change="runPreview"
                  />
                </template>

                <!-- CASE 参数 -->
                <template v-else-if="element.type === 'CASE'">
                  <el-select v-model="element.params.mode" style="width: 160px" @change="runPreview">
                    <el-option label="全部大写" value="UPPER" />
                    <el-option label="全部小写" value="LOWER" />
                    <el-option label="首字母大写" value="CAPITALIZE" />
                  </el-select>
                </template>

                <!-- TYPE_CAST 参数 -->
                <template v-else-if="element.type === 'TYPE_CAST'">
                  <el-select v-model="element.params.targetType" style="width: 140px" @change="runPreview">
                    <el-option label="字符串" value="STRING" />
                    <el-option label="整数" value="INTEGER" />
                    <el-option label="小数" value="DECIMAL" />
                    <el-option label="布尔值" value="BOOLEAN" />
                  </el-select>
                </template>
              </div>

              <!-- 该步骤输出预览 -->
              <div class="rule-preview" v-if="previewSteps[index + 1] !== undefined">
                <el-tag size="small" type="success">
                  → {{ previewSteps[index + 1]?.output }}
                </el-tag>
              </div>

              <!-- 删除按钮 -->
              <el-popconfirm title="确认删除此规则？" @confirm="removeRule(index)">
                <template #reference>
                  <el-button type="danger" :icon="Delete" circle size="small" class="delete-btn" />
                </template>
              </el-popconfirm>
            </div>
          </template>
        </draggable>
      </div>

      <!-- 底部操作栏 -->
      <div class="footer-actions">
        <el-button @click="clearRules">清空规则</el-button>
        <el-button type="primary" @click="runPreview" :loading="loading">
          <el-icon><VideoPlay /></el-icon> 执行预览
        </el-button>
        <el-button type="success" @click="saveRules" v-if="showSave">
          <el-icon><DocumentChecked /></el-icon> 保存规则
        </el-button>
      </div>

      <!-- 分步预览结果 -->
      <el-collapse v-if="previewSteps.length > 0" class="preview-collapse">
        <el-collapse-item title="分步执行详情" name="steps">
          <el-steps :active="previewSteps.length - 1" finish-status="success" simple>
            <el-step
              v-for="(step, i) in previewSteps"
              :key="i"
              :title="step.ruleName"
              :description="step.output"
            />
          </el-steps>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Rank, QuestionFilled, VideoPlay, DocumentChecked } from '@element-plus/icons-vue'
import { VueDraggableNext as draggable } from 'vue-draggable-next'
import request from '@/api/request'

// ==================== Props ====================
const props = defineProps({
  /** 初始规则列表（从父组件或模板传入），格式与 ProcessRule 一致 */
  modelValue: {
    type: Array,
    default: () => []
  },
  /** 是否显示「保存规则」按钮 */
  showSave: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'save'])

// ==================== 规则类型元数据 ====================
const ruleTypes = [
  { type: 'TRIM',      label: '去空格' },
  { type: 'SUBSTRING', label: '截位' },
  { type: 'PAD',       label: '补位' },
  { type: 'CASE',      label: '大小写转换' },
  { type: 'TYPE_CAST', label: '类型转换' }
]

// 各规则类型的默认参数
const defaultParams = {
  TRIM:      { mode: 'BOTH' },
  SUBSTRING: { start: 0, length: 10 },
  PAD:       { direction: 'LEFT', char: '0', length: 8 },
  CASE:      { mode: 'UPPER' },
  TYPE_CAST: { targetType: 'STRING' }
}

// ==================== 状态 ====================
const inputValue   = ref('')
const rules        = ref([])  // { id, type, params }
const previewSteps = ref([])  // [{ step, ruleName, params, output }]
const loading      = ref(false)
const outputChanged = ref(false)
let ruleIdCounter  = 0

// 初始化：从 modelValue 加载规则
watch(() => props.modelValue, (val) => {
  if (val && val.length > 0) {
    rules.value = val.map((r, i) => ({
      id: ++ruleIdCounter,
      ruleName: r.ruleName || `rule_${ruleIdCounter}`,
      type: r.type,
      params: { ...(r.params || {}) }
    }))
  }
}, { immediate: true })

// 最终输出
const finalOutput = computed(() => {
  if (previewSteps.value.length === 0) return null
  return previewSteps.value[previewSteps.value.length - 1].output
})

// ==================== 规则操作 ====================
function addRule() {
  const seq = ++ruleIdCounter
  rules.value.push({
    id: seq,
    ruleName: `rule_${seq}`,
    type: 'TRIM',
    params: { ...defaultParams['TRIM'] }
  })
  // 只有已有输入值时才自动触发预览，避免无意义的 API 调用
  if (inputValue.value) runPreview()
}

function removeRule(index) {
  rules.value.splice(index, 1)
  runPreview()
}

function clearRules() {
  rules.value = []
  previewSteps.value = []
}

function onRuleTypeChange(element) {
  element.params = { ...defaultParams[element.type] }
  runPreview()
}

function handleInputChange() {
  runPreview()
}

// ==================== 预览执行 ====================
async function runPreview() {
  if (!inputValue.value || rules.value.length === 0) {
    previewSteps.value = []
    return
  }

  loading.value = true
  try {
    const payload = buildPayload()
    const data = await request.post('/field-process/preview', payload)
    previewSteps.value = data
    outputChanged.value = true
    setTimeout(() => { outputChanged.value = false }, 800)
  } catch (err) {
    const msg = err.response?.data?.message || err.message
    ElMessage.error('执行出错：' + msg)
  } finally {
    loading.value = false
  }
}

function buildPayload() {
  return {
    value: inputValue.value,
    rules: rules.value.map(r => ({
      ruleName: r.ruleName,
      type: r.type,
      params: normalizeParams(r.type, r.params)
    }))
  }
}

/** 将 el-input-number 的数字类型转为字符串传给后端 */
function normalizeParams(type, params) {
  const p = { ...params }
  if (type === 'SUBSTRING') {
    if (p.start !== undefined) p.start = String(p.start)
    if (p.length !== undefined) p.length = String(p.length)
  } else if (type === 'PAD') {
    if (p.length !== undefined) p.length = String(p.length)
  }
  return p
}

// ==================== 保存 ====================
function saveRules() {
  const serialized = rules.value.map(r => ({
    ruleName: r.ruleName,
    type: r.type,
    params: normalizeParams(r.type, r.params)
  }))
  emit('update:modelValue', serialized)
  emit('save', serialized)
  ElMessage.success('规则已保存')
}
</script>

<style scoped>
.field-process-page {
  padding: 16px;
}
.page-card {
  max-width: 1100px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
}
.help-icon {
  margin-left: 6px;
  color: #909399;
  cursor: help;
  vertical-align: middle;
}
.input-row {
  margin-bottom: 8px;
}
.output-changed {
  transition: background-color 0.3s;
  background-color: #f0f9eb;
}
.rules-section {
  margin-top: 16px;
}
.rules-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.rules-title {
  font-weight: 500;
  color: #303133;
}
.empty-rules {
  padding: 16px 0;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;
  text-align: center;
}
.rule-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  transition: box-shadow 0.2s;
}
.rule-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.drag-handle {
  cursor: grab;
  color: #909399;
  font-size: 18px;
  flex-shrink: 0;
}
.drag-handle:active {
  cursor: grabbing;
}
.rule-step {
  flex-shrink: 0;
}
.rule-params {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
}
.rule-preview {
  flex-shrink: 0;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.delete-btn {
  flex-shrink: 0;
  margin-left: auto;
}
.footer-actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
.preview-collapse {
  margin-top: 16px;
}
</style>
