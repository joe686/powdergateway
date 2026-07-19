<template>
  <div class="formula-builder">
    <div class="group-header" :style="{ paddingLeft: depth * 16 + 'px' }">
      <el-tag :type="tagType" size="small">{{ groupLogic }}</el-tag>
      <el-select v-model="localGroup.logic" size="small" style="width:80px; margin-left:6px" @change="emitUpdate">
        <el-option label="AND" value="AND" />
        <el-option label="OR" value="OR" />
        <el-option label="NOT" value="NOT" />
      </el-select>
      <el-button link size="small" type="primary" @click="addCondition">+ 条件</el-button>
      <el-button link size="small" type="primary" @click="addSubGroup">+ 子组</el-button>
      <el-button v-if="depth > 0" link size="small" type="danger" @click="$emit('remove')">删除本组</el-button>
    </div>

    <div v-for="(child, idx) in localGroup.children" :key="idx" class="child-row" :style="{ paddingLeft: (depth + 1) * 16 + 'px' }">
      <!-- 嵌套子组 -->
      <FormulaBuilder
        v-if="child.nodeType === 'CONDITION_GROUP'"
        :model-value="{ type: 'CONDITION_GROUP', logic: child.logic, children: child.children, interfaceRefs: [] }"
        :table-columns-map="tableColumnsMap"
        :depth="depth + 1"
        @update:model-value="(v) => onChildGroupUpdate(idx, v)"
        @remove="removeChild(idx)"
      />
      <!-- 条件行 -->
      <div v-else class="cond-row">
        <OperandInput :model-value="child.left || {}" :table-columns-map="tableColumnsMap"
                      @update:model-value="(v) => { child.left = v; emitUpdate() }" />
        <el-select v-model="child.op" size="small" style="width:100px; margin:0 6px" @change="emitUpdate">
          <el-option label="=" value="EQ" />
          <el-option label="≠" value="NE" />
          <el-option label=">" value="GT" />
          <el-option label="≥" value="GE" />
          <el-option label="<" value="LT" />
          <el-option label="≤" value="LE" />
          <el-option label="LIKE" value="LIKE" />
          <el-option label="IN" value="IN" />
          <el-option label="BETWEEN" value="BETWEEN" />
          <el-option label="IS NULL" value="IS_NULL" />
          <el-option label="IS NOT NULL" value="IS_NOT_NULL" />
        </el-select>
        <OperandInput
          v-if="child.op !== 'IS_NULL' && child.op !== 'IS_NOT_NULL'"
          :model-value="child.right || {}"
          :table-columns-map="tableColumnsMap"
          @update:model-value="(v) => { child.right = v; emitUpdate() }" />
        <el-button link size="small" type="danger" style="margin-left:6px" @click="removeChild(idx)">删除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, computed, watch } from 'vue'
import OperandInput from './OperandInput.vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  tableColumnsMap: { type: Object, default: () => ({}) },
  depth: { type: Number, default: 0 }
})
const emit = defineEmits(['update:modelValue', 'remove'])

const localGroup = reactive({
  type: 'CONDITION_GROUP',
  logic: 'AND',
  children: [],
  interfaceRefs: [],
  ...deepClone(props.modelValue)
})

watch(() => props.modelValue, (v) => {
  Object.assign(localGroup, { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [], ...deepClone(v) })
}, { deep: false })

const groupLogic = computed(() => localGroup.logic || 'AND')
const tagType = computed(() => ({ AND: 'primary', OR: 'success', NOT: 'danger' }[localGroup.logic] || 'info'))

function addCondition() {
  localGroup.children.push({
    nodeType: 'CONDITION',
    op: 'EQ',
    left:  { kind: 'COLUMN', tableName: '', columnName: '' },
    right: { kind: 'CONST', constType: 'STRING', constValue: '' }
  })
  emitUpdate()
}
function addSubGroup() {
  localGroup.children.push({
    nodeType: 'CONDITION_GROUP',
    logic: 'AND',
    children: []
  })
  emitUpdate()
}
function removeChild(idx) {
  localGroup.children.splice(idx, 1)
  emitUpdate()
}
function onChildGroupUpdate(idx, v) {
  localGroup.children[idx] = { nodeType: 'CONDITION_GROUP', logic: v.logic, children: v.children }
  emitUpdate()
}

function emitUpdate() {
  emit('update:modelValue', deepClone(localGroup))
}
function deepClone(o) { return o ? JSON.parse(JSON.stringify(o)) : o }
</script>

<style scoped>
.formula-builder { padding: 4px 0; }
.group-header { display: flex; align-items: center; gap: 6px; padding: 4px 0; }
.child-row { padding: 4px 0; }
.cond-row { display: flex; align-items: center; flex-wrap: wrap; padding: 4px 0; border-left: 2px solid #ebeef5; padding-left: 8px; }
</style>
