<template>
  <div class="operand-input">
    <el-select v-model="local.kind" size="small" style="width:110px" @change="onKindChange">
      <el-option label="列" value="COLUMN" />
      <el-option label="请求参数" value="REQUEST_PARAM" />
      <el-option label="常量" value="CONST" />
      <el-option v-if="recursive" label="算术表达式" value="ARITH" />
      <el-option label="公式引用" value="FORMULA_REF" />
    </el-select>

    <!-- COLUMN -->
    <template v-if="local.kind === 'COLUMN'">
      <el-select v-model="local.tableName" size="small" style="width:140px; margin-left:6px"
                 placeholder="表" filterable @change="onFieldChange">
        <el-option v-for="t in tableNames" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="local.columnName" size="small" style="width:160px; margin-left:6px"
                 placeholder="列" filterable @change="onFieldChange">
        <el-option v-for="c in columnsOf(local.tableName)" :key="c.name"
                   :label="c.name" :value="c.name" />
      </el-select>
    </template>

    <!-- REQUEST_PARAM -->
    <template v-else-if="local.kind === 'REQUEST_PARAM'">
      <el-input v-model="local.paramKey" size="small" placeholder="请求参数名"
                style="width:200px; margin-left:6px" @input="onFieldChange" />
    </template>

    <!-- CONST -->
    <template v-else-if="local.kind === 'CONST'">
      <el-select v-model="local.constType" size="small" style="width:130px; margin-left:6px"
                 placeholder="类型" @change="onConstTypeChange">
        <el-option label="数字" value="NUMBER" />
        <el-option label="字符串" value="STRING" />
        <el-option label="布尔" value="BOOLEAN" />
        <el-option label="字符串数组" value="STRING_ARRAY" />
        <el-option label="数字数组" value="NUMBER_ARRAY" />
      </el-select>
      <el-input v-if="isScalarConst" v-model="local.constValue" size="small"
                :placeholder="constPlaceholder" style="width:180px; margin-left:6px"
                @input="onFieldChange" />
      <el-input v-else v-model="arrayText" size="small"
                placeholder="逗号分隔，如 A,B,C 或 1,2"
                style="width:220px; margin-left:6px" @input="onArrayInput" />
    </template>

    <!-- ARITH -->
    <template v-else-if="local.kind === 'ARITH' && recursive">
      <div class="arith-box">
        <OperandInput
          :model-value="local.expr && local.expr.left ? local.expr.left : { kind: 'CONST', constType: 'NUMBER', constValue: 0 }"
          :table-columns-map="tableColumnsMap"
          :recursive="false"
          @update:model-value="setArithLeft" />
        <el-select v-model="arithOp" size="small" style="width:70px; margin:0 4px" @change="onFieldChange">
          <el-option label="+" value="ADD" />
          <el-option label="-" value="SUB" />
          <el-option label="×" value="MUL" />
          <el-option label="÷" value="DIV" />
        </el-select>
        <OperandInput
          :model-value="local.expr && local.expr.right ? local.expr.right : { kind: 'CONST', constType: 'NUMBER', constValue: 0 }"
          :table-columns-map="tableColumnsMap"
          :recursive="false"
          @update:model-value="setArithRight" />
      </div>
    </template>

    <!-- FORMULA_REF -->
    <template v-else-if="local.kind === 'FORMULA_REF'">
      <el-input-number v-model="local.formulaId" size="small" :min="1"
                       placeholder="公式 ID" style="width:140px; margin-left:6px"
                       @change="onFieldChange" />
    </template>
  </div>
</template>

<script setup>
import { reactive, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  tableColumnsMap: { type: Object, default: () => ({}) },
  recursive: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

// 本地可变副本
const local = reactive({
  kind: 'COLUMN',
  tableName: '',
  columnName: '',
  paramKey: '',
  constType: 'STRING',
  constValue: '',
  expr: null,
  formulaId: null,
  ...props.modelValue
})

// 外部 modelValue 改变时同步（父组件重置场景）
watch(() => props.modelValue, (v) => {
  Object.assign(local, {
    kind: 'COLUMN', tableName: '', columnName: '', paramKey: '',
    constType: 'STRING', constValue: '', expr: null, formulaId: null,
    ...v
  })
}, { deep: false })

const tableNames = computed(() => Object.keys(props.tableColumnsMap || {}))
const columnsOf = (t) => (t && props.tableColumnsMap[t]) || []

const isScalarConst = computed(() =>
  ['NUMBER', 'STRING', 'BOOLEAN'].includes(local.constType)
)
const constPlaceholder = computed(() => {
  if (local.constType === 'NUMBER') return '如 100'
  if (local.constType === 'BOOLEAN') return 'true / false'
  return '字符串值'
})

const arrayText = computed({
  get: () => Array.isArray(local.constValue) ? local.constValue.join(',') : '',
  set: (v) => {
    const parts = String(v).split(',').map(s => s.trim()).filter(Boolean)
    local.constValue = local.constType === 'NUMBER_ARRAY'
      ? parts.map(Number)
      : parts
  }
})

const arithOp = computed({
  get: () => (local.expr && local.expr.op) || 'ADD',
  set: (v) => {
    if (!local.expr) local.expr = { op: v, left: null, right: null }
    else local.expr.op = v
  }
})

function onKindChange() {
  // 切换 kind 时清空互斥字段
  local.tableName = ''; local.columnName = ''; local.paramKey = ''
  local.constValue = ''; local.expr = null; local.formulaId = null
  if (local.kind === 'CONST' && !local.constType) local.constType = 'STRING'
  if (local.kind === 'ARITH') {
    local.expr = { op: 'ADD',
      left:  { kind: 'CONST', constType: 'NUMBER', constValue: 0 },
      right: { kind: 'CONST', constType: 'NUMBER', constValue: 0 } }
  }
  emitUpdate()
}
function onConstTypeChange() {
  local.constValue = ['STRING_ARRAY', 'NUMBER_ARRAY'].includes(local.constType) ? [] : ''
  emitUpdate()
}
function onFieldChange() { emitUpdate() }
function onArrayInput() { emitUpdate() }
function setArithLeft(v)  { if (!local.expr) local.expr = { op: 'ADD' }; local.expr.left = v; emitUpdate() }
function setArithRight(v) { if (!local.expr) local.expr = { op: 'ADD' }; local.expr.right = v; emitUpdate() }

function emitUpdate() {
  emit('update:modelValue', JSON.parse(JSON.stringify(local)))
}
</script>

<style scoped>
.operand-input { display: inline-flex; align-items: center; flex-wrap: wrap; gap: 2px; }
.arith-box { display: inline-flex; align-items: center; padding: 4px 6px; border: 1px dashed #dcdfe6; border-radius: 4px; margin-left: 6px; }
</style>
