<template>
  <div class="registry-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>注册中心管理</span>
          <div>
            <el-button @click="openDiscoverPreview">服务发现预览</el-button>
            <el-button type="primary" @click="openCreate">+ 新增注册中心</el-button>
          </div>
        </div>
      </template>

      <el-table :data="registries" v-loading="loading" style="width: 100%">
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.enabled === 1" type="success">启用</el-tag>
            <el-tag v-else type="info">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="别名" min-width="140" />
        <el-table-column prop="serverAddr" label="服务端地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="namespace" label="命名空间" width="120" />
        <el-table-column prop="groupName" label="分组" width="140" />
        <el-table-column label="自注册" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.registerSelf === 1" type="success" effect="plain">是</el-tag>
            <el-tag v-else type="info" effect="plain">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务名" width="140" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :loading="testingId === row.id" @click="testConn(row)">测试</el-button>
            <el-button size="small" type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑注册中心' : '新增注册中心'" width="600px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button value="nacos">Nacos</el-radio-button>
            <el-radio-button value="eureka">Eureka</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="别名" prop="name">
          <el-input v-model="form.name" placeholder="如：内部 Nacos / 部门 Eureka" />
        </el-form-item>
        <el-form-item label="服务端地址" prop="serverAddr">
          <el-input v-model="form.serverAddr" :placeholder="serverAddrPlaceholder" />
        </el-form-item>
        <el-form-item label="命名空间" v-if="form.type === 'nacos'">
          <el-input v-model="form.namespace" placeholder="留空则为 public" />
        </el-form-item>
        <el-form-item label="分组" v-if="form.type === 'nacos'">
          <el-input v-model="form.groupName" placeholder="默认 DEFAULT_GROUP" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="off" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" show-password autocomplete="new-password"
                    :placeholder="form.id ? '不修改留 *** 或留空' : '首次配置请填写'" />
        </el-form-item>
        <el-form-item label="自注册">
          <el-switch v-model="form.registerSelf" :active-value="1" :inactive-value="0" />
          <span class="form-tip">本机是否注册到该注册中心，供外部系统发现</span>
        </el-form-item>
        <el-form-item label="服务名" v-if="form.registerSelf === 1">
          <el-input v-model="form.serviceName" placeholder="留空则用 sys_config.registry.self.service_name" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 服务发现预览 -->
    <el-dialog v-model="discoverVisible" title="服务发现预览" width="600px">
      <el-form inline>
        <el-form-item label="服务名">
          <el-input v-model="discoverSvc" placeholder="如：CBS_SYSTEM" style="width: 260px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="discovering" @click="doDiscover">发现</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="discoverResult" size="small">
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="port" label="端口" width="90" />
        <el-table-column prop="scheme" label="协议" width="80" />
        <el-table-column label="元数据">
          <template #default="{ row }">
            <pre style="margin: 0; font-size: 12px">{{ JSON.stringify(row.metadata) }}</pre>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!discovering && discoverResult.length === 0 && discoverSvc" style="text-align: center; margin-top: 12px; color: #999">
        未发现任何可用实例
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRegistries,
  saveRegistry,
  deleteRegistry,
  testRegistryConnection,
  discoverServicePreview
} from '@/api/registry'

const registries = ref([])
const loading = ref(false)
const editVisible = ref(false)
const saving = ref(false)
const testingId = ref(null)
const discoverVisible = ref(false)
const discoverSvc = ref('')
const discoverResult = ref([])
const discovering = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  type: 'nacos',
  name: '',
  serverAddr: '',
  namespace: '',
  groupName: 'DEFAULT_GROUP',
  username: '',
  password: '',
  enabled: 1,
  registerSelf: 1,
  serviceName: ''
})

const rules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入别名', trigger: 'blur' }],
  serverAddr: [{ required: true, message: '请输入服务端地址', trigger: 'blur' }]
}

const serverAddrPlaceholder = computed(() =>
  form.type === 'nacos'
    ? '如：127.0.0.1:8848（多台逗号分隔）'
    : '如：http://127.0.0.1:8761/eureka/'
)

onMounted(() => refresh())

async function refresh() {
  loading.value = true
  try {
    const res = await listRegistries()
    registries.value = res || []
  } catch (e) {
    ElMessage.error('加载失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  editVisible.value = true
}

function openEdit(row) {
  Object.assign(form, row)
  form.password = '***' // 密码占位，用户不改则保持
  editVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: null,
    type: 'nacos',
    name: '',
    serverAddr: '',
    namespace: '',
    groupName: 'DEFAULT_GROUP',
    username: '',
    password: '',
    enabled: 1,
    registerSelf: 1,
    serviceName: ''
  })
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await saveRegistry({ ...form })
    ElMessage.success('保存成功')
    editVisible.value = false
    await refresh()
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || e))
  } finally {
    saving.value = false
  }
}

async function testConn(row) {
  testingId.value = row.id
  try {
    const r = await testRegistryConnection(row.id)
    if (r?.ok) {
      ElMessage.success(r.message || '连接正常')
    } else {
      ElMessage.warning(r?.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error('测试失败：' + (e?.message || e))
  } finally {
    testingId.value = null
  }
}

async function confirmDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除注册中心「${row.name}」？删除后原有 service:// 配置将无法解析。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteRegistry(row.id)
    ElMessage.success('删除成功')
    await refresh()
  } catch (e) {
    ElMessage.error('删除失败：' + (e?.message || e))
  }
}

function openDiscoverPreview() {
  discoverSvc.value = ''
  discoverResult.value = []
  discoverVisible.value = true
}

async function doDiscover() {
  if (!discoverSvc.value) return
  discovering.value = true
  try {
    const r = await discoverServicePreview(discoverSvc.value)
    discoverResult.value = r || []
  } catch (e) {
    ElMessage.error('发现失败：' + (e?.message || e))
  } finally {
    discovering.value = false
  }
}
</script>

<style scoped>
.registry-management { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.form-tip { margin-left: 12px; color: #999; font-size: 12px; }
</style>
