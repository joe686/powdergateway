<template>
  <div class="json-tree">
    <div v-if="parseError" class="json-error">JSON 解析失败:{{ parseError }}</div>
    <JsonNode v-else :name="null" :value="parsed" :depth="0" :is-last="true" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import JsonNode from './JsonNode.vue'

const props = defineProps({
  json: { type: String, default: '[]' },
})

const parseError = ref('')
const parsed = computed(() => {
  parseError.value = ''
  try {
    if (!props.json || !props.json.trim()) return null
    return JSON.parse(props.json)
  } catch (e) {
    parseError.value = e.message
    return null
  }
})

watch(() => props.json, () => { void parsed.value })
</script>

<style scoped>
.json-tree {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.7;
  background: #fafafa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 12px;
  max-height: 500px;
  overflow: auto;
}
.json-error {
  color: #F56C6C;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
</style>
