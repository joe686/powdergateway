<template>
  <div ref="host" class="code-editor" />
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import CodeMirror from 'codemirror'
import 'codemirror/mode/javascript/javascript'
import 'codemirror/mode/xml/xml'
import 'codemirror/lib/codemirror.css'

const props = defineProps({
  modelValue: { type: String, default: '' },
  language:   { type: String, default: 'json' } // json / xml / csv
})
const emit = defineEmits(['update:modelValue'])

const host = ref(null)
let cm = null

function modeOf(lang) {
  if (lang === 'xml') return 'xml'
  if (lang === 'json') return { name: 'javascript', json: true }
  return null // csv 走纯文本
}

onMounted(() => {
  cm = CodeMirror(host.value, {
    value: props.modelValue || '',
    mode: modeOf(props.language),
    lineNumbers: true,
    lineWrapping: true,
    tabSize: 2
  })
  cm.on('change', () => emit('update:modelValue', cm.getValue()))
})

onBeforeUnmount(() => { cm = null })

watch(() => props.modelValue, v => {
  if (cm && cm.getValue() !== v) cm.setValue(v || '')
})

watch(() => props.language, lang => {
  if (cm) cm.setOption('mode', modeOf(lang))
})
</script>

<style scoped>
.code-editor { border: 1px solid var(--el-border-color, #dcdfe6); border-radius: 4px; }
.code-editor :deep(.CodeMirror) { min-height: 220px; font-family: Menlo, Consolas, monospace; }
</style>
