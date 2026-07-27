<template>
  <el-dialog :model-value="visible" @update:model-value="emit('update:visible', $event)"
             title="字典转换参数" width="480px">
    <el-form label-width="90px">
      <el-form-item label="系统">
        <el-select v-model="local.system" filterable placeholder="选择系统" style="width:100%">
          <el-option v-for="s in systems" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="字典键">
        <el-select v-model="local.dictKey" filterable placeholder="选择字典键" :disabled="!local.system"
                   style="width:100%">
          <el-option v-for="k in dictKeys" :key="k" :label="k" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="方向">
        <el-radio-group v-model="local.direction">
          <el-radio :value="1">出向</el-radio>
          <el-radio :value="2">入向</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!canConfirm" @click="doConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useDictMappingStore } from '@/stores/dictMapping'

const props = defineProps({
  visible:    { type: Boolean, default: false },
  modelValue: { type: Object,  default: () => ({ system: '', dictKey: '', direction: 1 }) }
})
const emit = defineEmits(['update:visible', 'confirm'])

const store = useDictMappingStore()
const local = ref({ system: '', dictKey: '', direction: 1 })

watch(() => props.visible, async v => {
  if (v) {
    await store.ensureLoaded()
    local.value = {
      system:    props.modelValue?.system    || '',
      dictKey:   props.modelValue?.dictKey   || '',
      direction: Number(props.modelValue?.direction) || 1
    }
  }
}, { immediate: true })

const systems  = computed(() => store.systems)
const dictKeys = computed(() => local.value.system ? store.dictKeysOf(local.value.system) : [])
const canConfirm = computed(() =>
  !!(local.value.system && local.value.dictKey && [1, 2].includes(local.value.direction)))

function doConfirm() {
  emit('confirm', {
    system:    local.value.system,
    dictKey:   local.value.dictKey,
    direction: String(local.value.direction)   // ProcessRule.params 只能存字符串
  })
  emit('update:visible', false)
}

defineExpose({ local, doConfirm, canConfirm })
</script>
