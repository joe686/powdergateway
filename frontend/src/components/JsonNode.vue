<template>
  <div class="json-node" :style="{ marginLeft: depth === 0 ? '0' : '16px' }">
    <template v-if="isPrimitive">
      <span v-if="name !== null" class="key">{{ nameLabel }}:</span>
      <span :class="['value', valueClass]">{{ formattedValue }}</span><span v-if="!isLast" class="sep">,</span>
    </template>
    <template v-else>
      <span class="toggle" @click="open = !open">{{ open ? '▼' : '▶' }}</span>
      <span v-if="name !== null" class="key">{{ nameLabel }}:</span>
      <span class="brace">{{ openBrace }}</span>
      <span v-if="!open" class="preview">{{ preview }}</span>
      <span v-if="!open" class="brace">{{ closeBrace }}</span><span v-if="!open && !isLast" class="sep">,</span>
      <div v-if="open">
        <JsonNode
          v-for="(child, idx) in entries"
          :key="child.key"
          :name="child.key"
          :value="child.val"
          :depth="depth + 1"
          :is-last="idx === entries.length - 1"
          :is-array="isArray"
        />
        <div :style="{ marginLeft: '0' }">
          <span class="brace">{{ closeBrace }}</span><span v-if="!isLast" class="sep">,</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
export default { name: 'JsonNode' }
</script>
<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  name: { type: [String, Number, null], default: null },
  value: { default: null },
  depth: { type: Number, default: 0 },
  isLast: { type: Boolean, default: true },
  isArray: { type: Boolean, default: false },
})

const open = ref(props.depth < 2)

const isArray = computed(() => Array.isArray(props.value))
const isObject = computed(() => props.value !== null && typeof props.value === 'object' && !isArray.value)
const isPrimitive = computed(() => !isArray.value && !isObject.value)

const nameLabel = computed(() => (typeof props.name === 'number' || props.isArray) ? props.name : `"${props.name}"`)

const openBrace = computed(() => isArray.value ? '[' : '{')
const closeBrace = computed(() => isArray.value ? ']' : '}')

const entries = computed(() => {
  if (isArray.value) return props.value.map((v, i) => ({ key: i, val: v }))
  if (isObject.value) return Object.entries(props.value).map(([k, v]) => ({ key: k, val: v }))
  return []
})

const preview = computed(() => {
  const count = entries.value.length
  if (count === 0) return ''
  return ` ${count} ${isArray.value ? '项' : '键'} `
})

const valueClass = computed(() => {
  if (props.value === null) return 'null'
  const t = typeof props.value
  return t
})
const formattedValue = computed(() => {
  if (props.value === null) return 'null'
  if (typeof props.value === 'string') return `"${props.value}"`
  return String(props.value)
})
</script>

<style scoped>
.json-node {
  padding-left: 4px;
}
.key { color: #67C23A; }
.value.string { color: #E6A23C; }
.value.number { color: #409EFF; }
.value.boolean { color: #909399; font-weight: 600; }
.value.null { color: #909399; font-style: italic; }
.brace { color: #333; font-weight: 600; }
.toggle {
  color: #666;
  cursor: pointer;
  user-select: none;
  display: inline-block;
  width: 12px;
}
.preview { color: #909399; font-size: 12px; font-style: italic; }
.sep { color: #606266; }
</style>
