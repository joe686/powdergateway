<template>
  <div class="condition-builder">
    <div v-if="conditions.length === 0" class="empty-hint">
      <el-empty description="暂无查询条件，点击下方按钮添加" :image-size="60" />
    </div>

    <el-table v-else :data="conditions" border size="small">
      <el-table-column v-if="showTableColumn" label="目标表" width="160">
        <template #default="{ row }">
          <el-select v-model="row.tableName" placeholder="选择表" size="small" style="width: 100%">
            <el-option v-for="t in tableOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="字段" min-width="160">
        <template #default="{ row }">
          <el-select v-model="row.field" placeholder="选择字段" filterable style="width: 100%">
            <el-option
              v-for="opt in computedFieldOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="操作符" width="130">
        <template #default="{ row }">
          <el-select v-model="row.op" style="width: 100%">
            <el-option label="等于 (=)" value="EQ" />
            <el-option label="不等于 (≠)" value="NE" />
            <el-option label="大于 (>)" value="GT" />
            <el-option label="小于 (<)" value="LT" />
            <el-option label="包含 (LIKE)" value="LIKE" />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="参数名" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.paramKey" placeholder="请求参数名（如 status）" />
        </template>
      </el-table-column>

      <el-table-column label="操作" width="70" align="center">
        <template #default="{ $index }">
          <el-button type="danger" link size="small" @click="removeCondition($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 10px">
      <el-button type="primary" plain size="small" @click="addCondition">+ 添加条件</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  fieldOptions: {
    type: Array,
    default: () => []
  },
  highlightPrimaryKeys: {
    type: Boolean,
    default: false
  },
  showTableColumn: {
    type: Boolean,
    default: false
  },
  tableOptions: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const conditions = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const computedFieldOptions = computed(() => {
  if (!props.highlightPrimaryKeys) return props.fieldOptions
  return props.fieldOptions.map(opt => ({
    ...opt,
    label: (opt.isPrimary || opt.isUnique) ? opt.label + ' ★' : opt.label
  }))
})

function addCondition() {
  emit('update:modelValue', [
    ...props.modelValue,
    { tableName: '', field: '', op: 'EQ', paramKey: '' }
  ])
}

function removeCondition(index) {
  const updated = [...props.modelValue]
  updated.splice(index, 1)
  emit('update:modelValue', updated)
}
</script>

<style scoped>
.condition-builder { padding: 4px 0; }
.empty-hint { padding: 8px 0; }
</style>
