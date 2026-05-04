<template>
  <div class="connection-list">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="openDialog(null)">新建连接</el-button>
    </div>

    <!-- 连接列表 -->
    <el-table :data="list" stripe v-loading="loading">
      <el-table-column prop="name" label="连接名" min-width="150" />
      <el-table-column prop="dbType" label="数据库类型" width="130" />
      <el-table-column prop="env" label="环境" width="80">
        <template #default="{ row }">
          <el-tag :type="envTagType(row.env)" size="small">{{ row.env }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="url" label="JDBC URL" min-width="250" show-overflow-tooltip />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="poolSize" label="连接池" width="80" />
      <el-table-column prop="createTime" label="创建时间" width="160" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleTest(row)">测试连接</el-button>
          <el-button size="small" type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确认删除该连接？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑连接' : '新建连接'"
      width="600px"
      @close="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="连接名" prop="name">
          <el-input v-model="form.name" placeholder="请输入连接名称" />
        </el-form-item>
        <el-form-item label="数据库类型" prop="dbType">
          <el-select v-model="form.dbType" placeholder="请选择">
            <el-option label="MySQL" value="MySQL" />
            <el-option label="Oracle" value="Oracle" />
            <el-option label="PostgreSQL" value="PostgreSQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="JDBC URL" prop="url">
          <el-input v-model="form.url" placeholder="jdbc:mysql://host:3306/dbname" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="form.id ? '不修改请留空（或填 ***）' : '请输入密码'"
            show-password
          />
        </el-form-item>
        <el-form-item label="环境" prop="env">
          <el-select v-model="form.env" placeholder="请选择">
            <el-option label="开发" value="dev" />
            <el-option label="测试" value="test" />
            <el-option label="生产" value="prod" />
          </el-select>
        </el-form-item>
        <el-form-item label="连接池大小">
          <el-input-number v-model="form.poolSize" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="超时(ms)">
          <el-input-number v-model="form.timeout" :min="1000" :max="30000" :step="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listConnections, saveConnection, deleteConnection, testConnection } from '@/api/dbConnection'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)

const defaultForm = () => ({
  id: null,
  name: '',
  dbType: 'MySQL',
  url: '',
  username: '',
  password: '',
  env: 'dev',
  poolSize: 5,
  timeout: 3000
})

const form = ref(defaultForm())

const rules = {
  name: [{ required: true, message: '连接名不能为空', trigger: 'blur' }],
  dbType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  url: [{ required: true, message: 'JDBC URL 不能为空', trigger: 'blur' }],
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  env: [{ required: true, message: '请选择环境', trigger: 'change' }]
}

const envTagType = (env) => ({ dev: '', test: 'warning', prod: 'danger' }[env] || '')

async function loadList() {
  loading.value = true
  try {
    list.value = await listConnections()
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    form.value = { ...row, password: '' }
  } else {
    form.value = defaultForm()
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...form.value }
    // 编辑时密码留空则传 *** 表示不修改
    if (payload.id && !payload.password) {
      payload.password = '***'
    }
    await saveConnection(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deleteConnection(id)
  ElMessage.success('删除成功')
  loadList()
}

async function handleTest(row) {
  ElMessage.info('测试连接中...')
  const result = await testConnection(row.id)
  if (result.success) {
    ElMessage.success(result.message)
  } else {
    ElMessage.error('连接失败：' + result.message)
  }
}

onMounted(loadList)
</script>

<style scoped>
.connection-list {
  padding: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
</style>
