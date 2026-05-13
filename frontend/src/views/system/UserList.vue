<template>
  <div class="user-list">
    <div class="toolbar">
      <el-input v-model="searchUsername" placeholder="搜索用户名" clearable
        style="width: 240px" @keyup.enter="loadList" @clear="loadList" />
      <el-button type="primary" @click="loadList">查询</el-button>
      <el-button type="success" @click="openForm(null)">新建用户</el-button>
    </div>

    <el-table :data="list" stripe border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openForm(row)">编辑</el-button>
          <el-popconfirm title="确认删除该用户？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; text-align: right"
      @current-change="loadList"
    />

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新建用户'" width="480px" @close="resetForm">
      <el-form :model="form" label-width="80px" style="padding-right: 16px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="form.id ? '留空则不修改密码' : '至少6位'" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员 (admin)" value="admin" />
            <el-option label="普通用户 (user)" value="user" />
            <el-option label="只读用户 (readonly)" value="readonly" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listUsers, saveUser, deleteUser } from '@/api/user'

const list = ref([])
const loading = ref(false)
const searchUsername = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const formVisible = ref(false)
const saving = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return { id: null, username: '', password: '', role: 'user', statusBool: true }
}

function roleTagType(role) {
  var map = { admin: 'danger', user: '', readonly: 'info' }
  return map[role] || 'info'
}

function roleLabel(role) {
  var map = { admin: '管理员', user: '普通用户', readonly: '只读' }
  return map[role] || role
}

async function loadList() {
  loading.value = true
  try {
    var res = await listUsers(searchUsername.value || undefined, page.value, pageSize.value)
    list.value = res || []
    total.value = list.value.length
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = emptyForm()
  if (row) {
    form.value.id = row.id
    form.value.username = row.username
    form.value.role = row.role
    form.value.statusBool = row.status === 1
  }
  formVisible.value = true
}

function resetForm() {
  form.value = emptyForm()
}

async function handleSave() {
  if (!form.value.username.trim() && !form.value.id) {
    ElMessage.error('请填写用户名')
    return
  }
  if (!form.value.role) {
    ElMessage.error('请选择角色')
    return
  }
  saving.value = true
  try {
    await saveUser({
      id: form.value.id,
      username: form.value.username,
      password: form.value.password || '',
      role: form.value.role,
      status: form.value.statusBool ? 1 : 0
    })
    ElMessage.success(form.value.id ? '更新成功' : '创建成功')
    formVisible.value = false
    await loadList()
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
  }
}

onMounted(loadList)
</script>

<style scoped>
.user-list { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
</style>
