<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="filter.system" clearable placeholder="系统" style="width:180px">
        <el-option v-for="s in allSystems" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="filter.dictKey" clearable placeholder="字典键" style="width:180px">
        <el-option v-for="k in allDictKeys" :key="k" :label="k" :value="k" />
      </el-select>
      <el-select v-model="filter.direction" clearable placeholder="方向" style="width:120px">
        <el-option label="出向" :value="1"/>
        <el-option label="入向" :value="2"/>
      </el-select>
      <el-button @click="reload">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button v-if="canWrite" type="primary" @click="openEdit()">新增</el-button>
      <el-button v-if="canWrite" @click="importVisible = true">导入 xlsx</el-button>
      <el-button @click="download">导出 xlsx</el-button>
    </div>

    <el-table :data="pagedData" v-loading="loading" style="margin-top:12px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="systemCode" label="系统" width="120" />
      <el-table-column prop="dictKey" label="字典键" width="150" />
      <el-table-column label="方向" width="90">
        <template #default="{row}">
          <el-tag :type="row.direction === 1 ? 'success' : 'warning'">
            {{ row.direction === 1 ? '出向' : '入向' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sourceValue" label="source" />
      <el-table-column prop="targetValue" label="target" />
      <el-table-column prop="cnLabel" label="中文含义" />
      <el-table-column label="状态" width="80">
        <template #default="{row}">
          <el-switch v-model="row.status" :active-value="1" :inactive-value="0"
                     :disabled="!canWrite" @change="onStatusChange(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" v-if="canWrite">
        <template #default="{row}">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-model:current-page="page" :page-size="pageSize" :total="filteredData.length"
                   style="margin-top:12px; justify-content: flex-end" />
  </el-card>

  <!-- 新增/编辑 dialog -->
  <el-dialog v-model="editVisible" :title="form.id ? '编辑字典条目' : '新增字典条目'" width="600px">
    <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
      <el-form-item label="系统" prop="systemCode">
        <el-select v-model="form.systemCode" filterable allow-create default-first-option
                   placeholder="选择或输入新系统代号">
          <el-option v-for="s in allSystems" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="字典键" prop="dictKey">
        <el-input v-model="form.dictKey" placeholder="如 GENDER"/>
      </el-form-item>
      <el-form-item label="方向" prop="mode">
        <el-radio-group v-model="form.mode">
          <el-radio value="1">出向 (PG → 对端)</el-radio>
          <el-radio value="2">入向 (对端 → PG)</el-radio>
          <el-radio value="both">双向（后端拆两条）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="源值" prop="sourceValue"><el-input v-model="form.sourceValue"/></el-form-item>
      <el-form-item label="目标值" prop="targetValue"><el-input v-model="form.targetValue"/></el-form-item>
      <el-form-item label="中文含义"><el-input v-model="form.cnLabel"/></el-form-item>
      <el-form-item label="启用"><el-switch v-model="form.status" :active-value="1" :inactive-value="0"/></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="doSave(false)">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import * as api from '@/api/dictMapping'

// 用户权限
const userStore = useUserStore()
const canWrite = computed(() => ['admin', 'user'].includes(userStore.role))

// 列表状态
const list = ref([])
const loading = ref(false)
const filter = reactive({ system: null, dictKey: null, direction: null })
const page = ref(1)
const pageSize = 20

// 对话框状态
const editVisible = ref(false)
const importVisible = ref(false)  // Task 3 将填充导入对话框内容
const formRef = ref(null)
const form = reactive({
  id: null,
  systemCode: '',
  dictKey: '',
  mode: '1',
  sourceValue: '',
  targetValue: '',
  cnLabel: '',
  status: 1
})

// 表单校验规则
const rules = {
  systemCode:  [{ required: true, message: '必填', trigger: 'blur' }],
  dictKey:     [{ required: true, message: '必填', trigger: 'blur' }],
  mode:        [{ required: true, message: '请选择方向', trigger: 'change' }],
  sourceValue: [{ required: true, message: '必填', trigger: 'blur' }],
  targetValue: [{ required: true, message: '必填', trigger: 'blur' }]
}

// 三级筛选的联动 computed
const allSystems = computed(() =>
  [...new Set(list.value.map(r => r.systemCode))].sort()
)
const allDictKeys = computed(() => {
  const arr = filter.system
    ? list.value.filter(r => r.systemCode === filter.system)
    : list.value
  return [...new Set(arr.map(r => r.dictKey))].sort()
})

// 过滤 + 分页
const filteredData = computed(() => list.value.filter(r =>
  (!filter.system    || r.systemCode === filter.system) &&
  (!filter.dictKey   || r.dictKey    === filter.dictKey) &&
  (!filter.direction || r.direction  === filter.direction)
))
const pagedData = computed(() =>
  filteredData.value.slice((page.value - 1) * pageSize, page.value * pageSize)
)

// 加载列表
async function reload() {
  loading.value = true
  try {
    const res = await api.listDictMappings()
    list.value = res?.data || res || []
  } finally {
    loading.value = false
  }
}

// 重置筛选条件
function resetFilter() {
  filter.system = null
  filter.dictKey = null
  filter.direction = null
}

// 打开新增/编辑 dialog
function openEdit(row) {
  Object.assign(form, {
    id:          row?.id          || null,
    systemCode:  row?.systemCode  || '',
    dictKey:     row?.dictKey     || '',
    mode:        row ? String(row.direction) : '1',
    sourceValue: row?.sourceValue || '',
    targetValue: row?.targetValue || '',
    cnLabel:     row?.cnLabel     || '',
    status:      row?.status ?? 1
  })
  editVisible.value = true
}

// 保存（新增 or 更新）· skipValidate=true 供测试跳过 el-form 校验
async function doSave(skipValidate = false) {
  if (!skipValidate && formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
  }
  // 三选一 radio 拆条逻辑：both → bidirectional=true, direction=1
  const payload = {
    systemCode:   form.systemCode,
    dictKey:      form.dictKey,
    sourceValue:  form.sourceValue,
    targetValue:  form.targetValue,
    cnLabel:      form.cnLabel,
    status:       form.status,
    bidirectional: form.mode === 'both',
    direction:    form.mode === 'both' ? 1 : Number(form.mode)
  }
  if (form.id) {
    await api.updateDictMapping(form.id, payload)
  } else {
    await api.saveDictMapping(payload)
  }
  ElMessage.success('保存成功')
  editVisible.value = false
  await reload()
}

// 删除（由 el-popconfirm confirm 事件触发）
async function doDelete(id) {
  await api.deleteDictMapping(id)
  ElMessage.success('已删除')
  await reload()
}

// 切换状态 switch
async function onStatusChange(row) {
  await api.updateDictMapping(row.id, {
    systemCode:  row.systemCode,
    dictKey:     row.dictKey,
    sourceValue: row.sourceValue,
    targetValue: row.targetValue,
    cnLabel:     row.cnLabel,
    status:      row.status,
    direction:   row.direction,
    bidirectional: false
  })
}

// 导出 xlsx
async function download() {
  const params = { ...filter }
  Object.keys(params).forEach(k => params[k] == null && delete params[k])
  const res = await api.exportDictMappings(params)
  const url = URL.createObjectURL(new Blob([res]))
  const a = document.createElement('a')
  a.href = url
  a.download = `字典映射_${Date.now()}.xlsx`
  a.click()
  URL.revokeObjectURL(url)
}

// 暴露给测试 · 供断言 form 状态、调 doSave、调 openEdit
defineExpose({ form, doSave, openEdit })

onMounted(reload)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
