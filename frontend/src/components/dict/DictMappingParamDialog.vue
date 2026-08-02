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
      <!-- v0.2.5 CR-004 · FB-049 · "去新建"跳转 -->
      <div class="jump-hint">
        <el-link type="primary" @click="jumpToDictMenu" :underline="false">
          <el-icon><Plus /></el-icon>
          没有想要的字典键？跳去{{ scopeLabel }}新建
        </el-link>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :disabled="!canConfirm" @click="doConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import * as VueRouter from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { useDictMappingStore } from '@/stores/dictMapping'

// v0.2.5 CR-004: useRoute/useRouter fallback · 兼容部分测试 mock 只导出 useRouter 的场景
const useRoute = VueRouter.useRoute || (() => ({ fullPath: '/' }))
const useRouter = VueRouter.useRouter || (() => ({ push: () => {} }))

const props = defineProps({
  visible:    { type: Boolean, default: false },
  modelValue: { type: Object,  default: () => ({ system: '', dictKey: '', direction: 1 }) },
  // v0.2.5 CR-004: 使用场景 · null=全部 1=M1侧 2=M2侧 3=共享
  scope:      { type: Number,  default: null }
})
const emit = defineEmits(['update:visible', 'confirm'])

const store = useDictMappingStore()
const route = useRoute()
const router = useRouter()
const local = ref({ system: '', dictKey: '', direction: 1 })

const scopeLabel = computed(() => {
  if (props.scope === 1) return '"接口转换 · 字典映射"'
  if (props.scope === 2) return '"可视化接口 · 字典映射"'
  return '"字典映射"'
})

watch(() => props.visible, async v => {
  if (v) {
    await store.ensureLoaded()
    // v0.2.5 CR-004: 若从字典页新建后回跳,检测 sessionStorage['pg-dict-return-value'] · auto-select
    tryRestoreFromDictReturn()

    local.value = {
      system:    props.modelValue?.system    || local.value.system    || '',
      dictKey:   props.modelValue?.dictKey   || local.value.dictKey   || '',
      direction: Number(props.modelValue?.direction) || local.value.direction || 1
    }
  }
}, { immediate: true })

function tryRestoreFromDictReturn() {
  try {
    const raw = sessionStorage.getItem('pg-dict-return-value')
    if (!raw) return
    const back = JSON.parse(raw)
    if (back?.system && back?.dictKey) {
      local.value.system = back.system
      local.value.dictKey = back.dictKey
      if (back.direction) local.value.direction = Number(back.direction)
      store.invalidate() // 重拉 · 因新建条目仍未在缓存
    }
    sessionStorage.removeItem('pg-dict-return-value')
  } catch {}
}

const systems = computed(() =>
  props.scope != null ? store.systemsForScope(props.scope) : store.systems
)
const dictKeys = computed(() => {
  if (!local.value.system) return []
  return props.scope != null
    ? store.dictKeysForScope(props.scope, local.value.system)
    : store.dictKeysOf(local.value.system)
})
const canConfirm = computed(() =>
  !!(local.value.system && local.value.dictKey && [1, 2].includes(local.value.direction)))

function doConfirm() {
  const params = {
    system:    local.value.system,
    dictKey:   local.value.dictKey,
    direction: String(local.value.direction)   // ProcessRule.params 只能存字符串
  }
  // v0.2.5 CR-004: scope 也传给 processor 让 lookup 有 scope 视角
  if (props.scope != null) params.scope = String(props.scope)
  emit('confirm', params)
  emit('update:visible', false)
}

/**
 * v0.2.5 CR-004 · FB-049
 * 跳转到对应侧字典菜单 · URL 预填 · 保留 wizard 状态(靠 wizardStore.persist 已覆盖)
 */
function jumpToDictMenu() {
  const path = props.scope === 1 ? '/transform/dict'
             : props.scope === 2 ? '/interface/dict'
             : '/interface/dict'  // fallback:默认 M2 侧
  router.push({
    path,
    query: {
      system: local.value.system || '',
      dictKey: local.value.dictKey || '',
      direction: local.value.direction || 1,
      returnTo: route.fullPath
    }
  })
  emit('update:visible', false)
}

defineExpose({ local, doConfirm, canConfirm, jumpToDictMenu })
</script>

<style scoped>
.jump-hint {
  margin-top: 8px;
  padding-left: 90px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
