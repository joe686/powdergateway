<template>
  <el-dialog v-model="visible" title="从注册中心选择服务" width="560px" @close="onClose">
    <el-form inline>
      <el-form-item label="服务名">
        <el-input v-model="serviceName" placeholder="如：CBS_SYSTEM" style="width: 280px" @keyup.enter="doDiscover" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="doDiscover">查询</el-button>
      </el-form-item>
    </el-form>

    <el-alert v-if="hint" :title="hint" type="info" :closable="false" style="margin-bottom: 12px" />

    <el-table v-if="instances.length" :data="instances" size="small">
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="port" label="端口" width="90" />
      <el-table-column prop="scheme" label="协议" width="80" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="pick(row)">选中</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && serviceName && !instances.length && searched" style="text-align:center; margin-top: 12px; color: #999">
      注册中心中未发现「{{ serviceName }}」的可用实例；请先在「辅助工具 → 注册中心管理」确认对方服务已注册。
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { discoverServicePreview } from '@/api/registry'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  initialServiceName: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'pick'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const serviceName = ref('')
const instances = ref([])
const loading = ref(false)
const searched = ref(false)

const hint = computed(() => {
  if (searched.value) return ''
  return '按服务名跨所有已启用的注册中心聚合查询；查询后选中一个实例即会自动回填 service:// 前缀'
})

watch(visible, v => {
  if (v) {
    serviceName.value = props.initialServiceName || ''
    instances.value = []
    searched.value = false
    if (serviceName.value) doDiscover()
  }
})

async function doDiscover() {
  if (!serviceName.value.trim()) {
    ElMessage.warning('请输入服务名')
    return
  }
  loading.value = true
  try {
    const r = await discoverServicePreview(serviceName.value.trim())
    instances.value = r || []
    searched.value = true
  } catch (e) {
    ElMessage.error('发现失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

function pick(row) {
  emit('pick', {
    serviceName: serviceName.value.trim(),
    instance: row,
    // 让业务侧决定 URL 组装：默认给出 service:// 前缀 + 尾斜线
    url: 'service://' + serviceName.value.trim() + '/'
  })
  visible.value = false
}

function onClose() {
  serviceName.value = ''
  instances.value = []
  searched.value = false
}
</script>
