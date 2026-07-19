<template>
  <div class="field-formula-page">
    <el-card>
      <template #header>
        <div class="page-header">
          <span class="title">字段公式管理</span>
          <div>
            <el-input v-model="query.scene" placeholder="场景" size="small" style="width:120px" clearable />
            <el-input v-model="query.keyword" placeholder="关键字（名称/备注）" size="small" style="width:200px; margin-left:6px" clearable />
            <el-button size="small" type="primary" @click="reload">查询</el-button>
            <el-button size="small" @click="resetQuery">重置</el-button>
            <el-button size="small" type="success" @click="openCreate">+ 新增公式</el-button>
          </div>
        </div>
      </template>

      <el-table :data="rows" border v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="scene" label="场景" width="140" show-overflow-tooltip />
        <el-table-column label="关联数据库" width="160">
          <template #default="{ row }">{{ dbNameById(row.dbConnectionId) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="creator" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="primary" @click="onDuplicate(row)">复制</el-button>
            <el-popconfirm v-if="isAdmin" title="确认删除该公式？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 12px; justify-content: flex-end; display:flex"
        v-model:current-page="page.pageNo"
        v-model:page-size="page.pageSize"
        :total="page.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="reload"
        @current-change="reload" />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑公式' : '新增公式'" width="900px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="公式名称" required>
          <el-input v-model="form.name" placeholder="全局唯一" />
        </el-form-item>
        <el-form-item label="场景">
          <el-input v-model="form.scene" placeholder="业务场景，如 客户信息" />
        </el-form-item>
        <el-form-item label="关联数据库" required>
          <el-select v-model="form.dbConnectionId" placeholder="选择数据库连接" style="width:100%" @change="loadTables">
            <el-option v-for="db in dbList" :key="db.id" :label="db.name" :value="db.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="公式配置">
          <FormulaBuilder v-model="formulaObj" :table-columns-map="tableColumnsMap" />
        </el-form-item>
        <el-form-item label="校验结果" v-if="validateResult">
          <el-tag v-if="validateResult.ok" type="success">校验通过</el-tag>
          <div v-else>
            <el-tag type="danger">校验未通过</el-tag>
            <ul class="err-list">
              <li v-for="(e, i) in validateResult.errors" :key="i">
                <code>{{ e.path }}</code> — {{ e.message }}
              </li>
            </ul>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="doValidate" :loading="validating">校验预览</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listFormulas, getFormula, saveFormula, duplicateFormula, deleteFormula, validateFormula } from '@/api/fieldFormula'
import { listConnections } from '@/api/dbConnection'
import { getTableStructure } from '@/api/tableStructure'
import FormulaBuilder from '@/components/formula/FormulaBuilder.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.role === 'admin')

const loading = ref(false)
const rows = ref([])
const query = reactive({ scene: '', keyword: '' })
const page = reactive({ pageNo: 1, pageSize: 20, total: 0 })

const dbList = ref([])
const dbNameById = (id) => (dbList.value.find(d => d.id === id) || {}).name || '-'

const dialogVisible = ref(false)
const saving = ref(false)
const validating = ref(false)
const validateResult = ref(null)

const form = reactive({ id: null, name: '', scene: '', dbConnectionId: null, remark: '' })
const formulaObj = ref({ type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] })
const tableColumnsMap = ref({})

async function reload() {
  loading.value = true
  try {
    const res = await listFormulas({
      scene: query.scene || undefined,
      keyword: query.keyword || undefined,
      pageNo: page.pageNo,
      pageSize: page.pageSize
    })
    rows.value = res.records || []
    page.total = res.total || 0
  } finally {
    loading.value = false
  }
}
function resetQuery() { query.scene = ''; query.keyword = ''; page.pageNo = 1; reload() }

async function loadTables(dbId) {
  tableColumnsMap.value = {}
  if (!dbId) return
  try {
    const list = await getTableStructure(dbId)
    ;(list || []).forEach(t => { tableColumnsMap.value[t.tableName] = t.columns || [] })
  } catch {
    ElMessage.error('加载表结构失败')
  }
}

function openCreate() {
  Object.assign(form, { id: null, name: '', scene: '', dbConnectionId: null, remark: '' })
  formulaObj.value = { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] }
  validateResult.value = null
  tableColumnsMap.value = {}
  dialogVisible.value = true
}

async function openEdit(row) {
  const detail = await getFormula(row.id)
  if (!detail) { ElMessage.warning('公式已被删除'); reload(); return }
  Object.assign(form, {
    id: detail.id, name: detail.name, scene: detail.scene,
    dbConnectionId: detail.dbConnectionId, remark: detail.remark
  })
  try {
    formulaObj.value = detail.formulaJson ? JSON.parse(detail.formulaJson)
      : { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] }
  } catch {
    formulaObj.value = { type: 'CONDITION_GROUP', logic: 'AND', children: [], interfaceRefs: [] }
    ElMessage.warning('公式 JSON 解析失败，已重置')
  }
  validateResult.value = null
  await loadTables(detail.dbConnectionId)
  dialogVisible.value = true
}

async function doValidate() {
  validating.value = true
  try {
    validateResult.value = await validateFormula({
      dbConnectionId: form.dbConnectionId,
      formulaJson: JSON.stringify(formulaObj.value)
    })
  } finally {
    validating.value = false
  }
}

async function doSave() {
  if (!form.name || !form.dbConnectionId) { ElMessage.warning('名称与数据库必填'); return }
  saving.value = true
  try {
    await saveFormula({
      id: form.id,
      name: form.name,
      scene: form.scene,
      dbConnectionId: form.dbConnectionId,
      remark: form.remark,
      formulaJson: JSON.stringify(formulaObj.value)
    })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onDuplicate(row) {
  const newId = await duplicateFormula(row.id)
  ElMessage.success(`已复制，新公式 id=${newId}，请修改后保存`)
  reload()
}

async function onDelete(row) {
  await deleteFormula(row.id)
  ElMessage.success('已删除')
  reload()
}

onMounted(async () => {
  try {
    dbList.value = (await listConnections()) || []
  } catch {
    ElMessage.error('加载数据库连接失败')
  }
  reload()
})
</script>

<style scoped>
.field-formula-page { padding: 0; }
.page-header { display:flex; justify-content:space-between; align-items:center; gap: 6px; }
.title { font-size:16px; font-weight:600; }
.err-list { margin: 4px 0 0 16px; color: #f56c6c; font-size: 12px; }
.err-list code { background:#fef0f0; padding: 0 4px; border-radius: 2px; }
</style>
