<template>
  <div class="rh-editor">
    <el-table :data="rows" size="small" border>
      <el-table-column label="Header 名" width="220">
        <template #default="{ row }">
          <el-input v-model="row.k" size="small" @change="emitChange" />
        </template>
      </el-table-column>
      <el-table-column label="Header 值">
        <template #default="{ row }">
          <el-input v-model="row.v" size="small" @change="emitChange" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ $index }">
          <el-button size="small" text type="danger" @click="remove($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button size="small" @click="add">添加 Header</el-button>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({ modelValue: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue'])

const rows = ref([])

function parse(json) {
  if (!json) return []
  try {
    const obj = JSON.parse(json)
    return Object.entries(obj).map(([k, v]) => ({ k, v: String(v) }))
  } catch { return [] }
}

function emitChange() {
  const obj = {}
  rows.value.forEach(r => { if (r.k) obj[r.k] = r.v })
  emit('update:modelValue', Object.keys(obj).length ? JSON.stringify(obj) : '')
}
function add() { rows.value.push({ k: '', v: '' }); emitChange() }
function remove(i) { rows.value.splice(i, 1); emitChange() }

watch(() => props.modelValue, v => { rows.value = parse(v) }, { immediate: true })
</script>
