<template>
  <div class="formula-picker">
    <el-select
      :model-value="modelValue"
      filterable clearable
      :placeholder="placeholder"
      style="width: 100%"
      @update:model-value="onChange"
    >
      <el-option
        v-for="f in list"
        :key="f.id"
        :value="f.id"
        :label="displayLabel(f)"
      />
    </el-select>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listFormulas } from '@/api/fieldFormula'

const props = defineProps({
  modelValue: { type: [Number, null], default: null },
  placeholder: { type: String, default: '选择已存字段公式' },
  scene: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'select'])

const list = ref([])

async function reload() {
  const res = await listFormulas({ pageNo: 1, pageSize: 200, scene: props.scene || undefined })
  list.value = res.records || []
}

function displayLabel(f) {
  return f.scene ? `${f.name}（${f.scene}）` : f.name
}

function onChange(id) {
  emit('update:modelValue', id)
  emit('select', list.value.find(x => x.id === id) || null)
}

onMounted(reload)
defineExpose({ reload })
</script>

<style scoped>
.formula-picker { display: inline-block; width: 100%; }
</style>
