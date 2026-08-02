<template>
  <el-card>
    <div class="page-header">
      <h2 v-if="scopeLabel">{{ scopeLabel }}</h2>
    </div>
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
      <el-button v-if="canWrite" type="success" @click="openBatch()">批量新增</el-button>
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

  <!-- 单条 新增/编辑 dialog -->
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

  <!-- 批量键值对 dialog · v0.2.5 CR-004 · FB-048 -->
  <el-dialog v-model="batchVisible" title="批量新增字典条目" width="900px" top="6vh">
    <el-alert
      title="顶部四字段整批共享,下方每行只填源值/目标值/中文。支持粘贴 Excel 三列 TSV 数据。"
      type="info" :closable="false" style="margin-bottom:12px" />

    <el-form :model="batchForm" label-width="100px" inline-message>
      <el-form-item label="系统" required>
        <el-select v-model="batchForm.systemCode" filterable allow-create default-first-option
                   placeholder="选择或输入系统代号" style="width:200px">
          <el-option v-for="s in allSystems" :key="s" :label="s" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item label="字典键" required>
        <el-input v-model="batchForm.dictKey" placeholder="如 GENDER" style="width:200px"/>
      </el-form-item>
      <el-form-item label="方向" required>
        <el-radio-group v-model="batchForm.direction">
          <el-radio :value="1">出向</el-radio>
          <el-radio :value="2">入向</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <div class="batch-items-header">
      <span>键值对列表（{{ batchForm.items.length }} / 200）</span>
      <div class="batch-items-actions">
        <el-button size="small" @click="addBatchRow">+ 添加一行</el-button>
        <el-button size="small" @click="pasteFromClipboard">📋 粘贴 TSV</el-button>
        <el-button size="small" @click="clearBatchItems">清空</el-button>
      </div>
    </div>

    <el-table :data="batchForm.items" max-height="360" border style="margin-top:8px">
      <el-table-column type="index" label="#" width="60" />
      <el-table-column label="源值 source" min-width="180">
        <template #default="{row}">
          <el-input v-model="row.sourceValue" size="small" placeholder="必填"/>
        </template>
      </el-table-column>
      <el-table-column label="目标值 target" min-width="180">
        <template #default="{row}">
          <el-input v-model="row.targetValue" size="small" placeholder="必填"/>
        </template>
      </el-table-column>
      <el-table-column label="中文 cnLabel" min-width="150">
        <template #default="{row}">
          <el-input v-model="row.cnLabel" size="small" placeholder="选填"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{$index}">
          <el-button size="small" type="danger" link @click="removeBatchRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="batchForm.items.length > 200"
              :title="`超过 200 条上限,请使用 Excel 导入功能 (${batchForm.items.length} 条)`"
              type="error" :closable="false" style="margin-top:12px" />

    <template #footer>
      <el-button @click="batchVisible = false">取消</el-button>
      <el-button type="primary" :disabled="batchForm.items.length > 200 || batchForm.items.length === 0"
                 @click="doBatchSave">保存 {{ batchForm.items.length }} 条</el-button>
    </template>
  </el-dialog>

  <!-- 导入 dialog -->
  <el-dialog v-model="importVisible" title="导入字典 Excel" width="700px" @close="onImportClose">
    <el-upload accept=".xlsx" :auto-upload="false" :on-change="handleFileSelect" :show-file-list="false">
      <el-button>选择 xlsx 文件</el-button>
    </el-upload>
    <div v-if="selectedFile" style="margin:8px 0">已选：{{ selectedFile.name }}</div>
    <el-button type="primary" :disabled="!selectedFile || uploading" @click="doImport">
      {{ uploading ? '上传中...' : '上传' }}
    </el-button>

    <template v-if="importResult">
      <el-alert v-if="importResult.failedRows.length > 0"
                :title="`共 ${importResult.failedRows.length} 行失败，事务已回滚，无数据入库`"
                type="error" :closable="false" style="margin-top:12px" />
      <el-alert v-else :title="`导入成功：${importResult.successCount} 条`"
                type="success" :closable="false" style="margin-top:12px" />
      <el-table v-if="importResult.failedRows.length > 0" :data="importResult.failedRows"
                max-height="300" style="margin-top:12px">
        <el-table-column prop="rowIndex" label="行号" width="100" />
        <el-table-column prop="errorMsg" label="错误描述" />
      </el-table>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import * as api from '@/api/dictMapping'

// v0.2.5 CR-004: scope prop 由路由传入 · 1=M1 侧 2=M2 侧 3=共享 · null=全部作用域(admin 兜底)
const props = defineProps({
  scope: { type: Number, default: null }
})

// 场景标题(用户可视化区分当前在哪个业务菜单)
const scopeLabel = computed(() => {
  if (props.scope === 1) return '字典映射管理 · 接口转换 (M1 侧)'
  if (props.scope === 2) return '字典映射管理 · 可视化接口 (M2 侧)'
  if (props.scope === 3) return '字典映射管理 · 通用共享'
  return ''
})

// 用户权限
const userStore = useUserStore()
const canWrite = computed(() => ['admin', 'user'].includes(userStore.role))

// v0.2.5 CR-004 · FB-049 · 从字段选择器跳转来的 returnTo 状态
const route = useRoute()
const router = useRouter()
const returnTo = ref(null)      // 记录返回路径 · 保存完成后回跳

// 列表状态
const list = ref([])
const loading = ref(false)
const filter = reactive({ system: null, dictKey: null, direction: null })
const page = ref(1)
const pageSize = 20

// 单条对话框状态
const editVisible = ref(false)
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

// v0.2.5 CR-004 批量键值对 dialog 状态
const batchVisible = ref(false)
const batchForm = reactive({
  systemCode: '',
  dictKey: '',
  direction: 1,
  items: []  // [{sourceValue, targetValue, cnLabel}]
})

// 导入对话框状态
const importVisible = ref(false)
const selectedFile = ref(null)
const uploading = ref(false)
const importResult = ref(null)

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

// 加载列表(scope 参数从 props 传入 · null 时不限)
async function reload() {
  loading.value = true
  try {
    const params = props.scope != null ? { scope: props.scope } : {}
    const res = await api.listDictMappings(params)
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

// 打开单条 新增/编辑 dialog
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

// 保存单条（新增 or 更新）· skipValidate=true 供测试跳过 el-form 校验
async function doSave(skipValidate = false) {
  if (!skipValidate && formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
  }
  const payload = {
    scope:        props.scope ?? 3,
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
  // v0.2.5 CR-004 · FB-049:有 returnTo 时回跳
  const dir = form.mode === 'both' ? 1 : Number(form.mode)
  tryReturnToWizard(form.systemCode, form.dictKey, dir)
}

// ═════════ v0.2.5 CR-004 批量键值对 dialog ═════════

function openBatch() {
  batchForm.systemCode = ''
  batchForm.dictKey = ''
  batchForm.direction = 1
  batchForm.items = [{ sourceValue: '', targetValue: '', cnLabel: '' }]
  batchVisible.value = true
}

function addBatchRow() {
  batchForm.items.push({ sourceValue: '', targetValue: '', cnLabel: '' })
}

function removeBatchRow(index) {
  batchForm.items.splice(index, 1)
  if (batchForm.items.length === 0) addBatchRow()
}

function clearBatchItems() {
  batchForm.items = [{ sourceValue: '', targetValue: '', cnLabel: '' }]
}

/**
 * 粘贴剪贴板 TSV 数据(源自 Excel 复制 · 3 列: source/target/cnLabel)。
 * 忽略只有空白的行 · 空 cnLabel 也接受。
 */
async function pasteFromClipboard() {
  try {
    const text = await navigator.clipboard.readText()
    if (!text) {
      ElMessage.warning('剪贴板为空')
      return
    }
    const lines = text.split(/\r?\n/).filter(l => l.trim() !== '')
    if (lines.length === 0) {
      ElMessage.warning('无有效数据')
      return
    }
    const parsed = lines.map(line => {
      const cols = line.split('\t')
      return {
        sourceValue: (cols[0] || '').trim(),
        targetValue: (cols[1] || '').trim(),
        cnLabel:     (cols[2] || '').trim()
      }
    }).filter(row => row.sourceValue !== '' || row.targetValue !== '')

    // 如当前只有一空行 · 替换 · 否则追加
    if (batchForm.items.length === 1 && !batchForm.items[0].sourceValue && !batchForm.items[0].targetValue) {
      batchForm.items = parsed
    } else {
      batchForm.items = batchForm.items.concat(parsed)
    }
    ElMessage.success(`已粘贴 ${parsed.length} 行 · 当前共 ${batchForm.items.length} 行`)
  } catch (e) {
    ElMessage.error('粘贴失败:' + (e.message || e))
  }
}

async function doBatchSave() {
  if (!batchForm.systemCode || !batchForm.dictKey) {
    ElMessage.warning('系统 / 字典键必填')
    return
  }
  if (batchForm.items.length === 0) {
    ElMessage.warning('至少填一行')
    return
  }
  if (batchForm.items.length > 200) {
    ElMessage.warning('超过 200 条上限,请使用 Excel 导入')
    return
  }
  // 前端预校验:每行 source/target 必填
  for (let i = 0; i < batchForm.items.length; i++) {
    const it = batchForm.items[i]
    if (!it.sourceValue || !it.targetValue) {
      ElMessage.error(`第 ${i + 1} 行 source 与 target 必填`)
      return
    }
  }
  const payload = {
    scope:      props.scope ?? 3,
    systemCode: batchForm.systemCode,
    dictKey:    batchForm.dictKey,
    direction:  batchForm.direction,
    items:      batchForm.items.map(it => ({
      sourceValue: it.sourceValue,
      targetValue: it.targetValue,
      cnLabel:     it.cnLabel || null
    }))
  }
  try {
    const res = await api.saveDictMappingBatch(payload)
    const ids = res?.data || res || []
    ElMessage.success(`批量保存成功:${ids.length} 条`)
    batchVisible.value = false
    await reload()
    // v0.2.5 CR-004 · FB-049:有 returnTo 时回跳,并 sessionStorage 传新建 key 让原页 auto-select
    tryReturnToWizard(batchForm.systemCode, batchForm.dictKey, batchForm.direction)
  } catch (e) {
    // 后端会精准到第 N 行 · 已由 request 拦截器 ElMessage 报错,无需再报
  }
}

/**
 * v0.2.5 CR-004 · FB-049
 * 若当前是从 wizard 字段选择器跳来(URL 带 returnTo),保存完写 sessionStorage 让原页 auto-select · 回跳
 */
function tryReturnToWizard(system, dictKey, direction) {
  if (!returnTo.value) return
  try {
    sessionStorage.setItem('pg-dict-return-value', JSON.stringify({ system, dictKey, direction }))
  } catch {}
  const target = returnTo.value
  returnTo.value = null
  router.push(target)
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
    scope:       row.scope,
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

// 导出 xlsx(带 scope 过滤)
async function download() {
  const params = { ...filter }
  if (props.scope != null) params.scope = props.scope
  Object.keys(params).forEach(k => params[k] == null && delete params[k])
  const res = await api.exportDictMappings(params)
  const url = URL.createObjectURL(new Blob([res]))
  const a = document.createElement('a')
  a.href = url
  a.download = `字典映射_${Date.now()}.xlsx`
  a.click()
  URL.revokeObjectURL(url)
}

// 导入处理函数
function handleFileSelect(file) {
  selectedFile.value = file.raw
}

async function doImport() {
  if (!selectedFile.value) return
  const fd = new FormData()
  fd.append('file', selectedFile.value)
  uploading.value = true
  try {
    const res = await api.importDictMappings(fd, props.scope)
    importResult.value = res?.data || res
    if (importResult.value.failedRows.length === 0) {
      ElMessage.success(`成功导入 ${importResult.value.successCount} 条`)
      importVisible.value = false
      await reload()
    }
  } catch (e) {
    ElMessage.error('导入失败：' + (e.message || e))
  } finally {
    uploading.value = false
  }
}

function onImportClose() {
  selectedFile.value = null
  importResult.value = null
  uploading.value = false
}

// 暴露给测试
defineExpose({ form, doSave, openEdit, doImport, importResult, selectedFile,
               batchForm, openBatch, addBatchRow, removeBatchRow, doBatchSave })

// v0.2.5 CR-004 · FB-049: mount 时检测 URL query · 自动打开批量弹窗预填
onMounted(async () => {
  await reload()
  const q = route.query
  if (q.returnTo) {
    returnTo.value = String(q.returnTo)
  }
  if (q.system || q.dictKey) {
    batchForm.systemCode = String(q.system || '')
    batchForm.dictKey = String(q.dictKey || '')
    batchForm.direction = Number(q.direction) || 1
    batchForm.items = [{ sourceValue: '', targetValue: '', cnLabel: '' }]
    batchVisible.value = true
  }
})
</script>

<style scoped>
.page-header h2 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
}
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.batch-items-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.batch-items-header > span {
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.batch-items-actions {
  display: flex;
  gap: 8px;
}
</style>
