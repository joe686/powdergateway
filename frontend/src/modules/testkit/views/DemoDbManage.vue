<template>
  <div class="testkit-demo-db">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>样例数据库管理</span>
          <div>
            <el-button :loading="loadingStats" @click="refreshStats">刷新统计</el-button>
            <el-button type="primary" :loading="initing" @click="doInit">初始化</el-button>
            <el-button :loading="resetting" @click="confirmReset">重置（清空重灌）</el-button>
            <el-button type="danger" :loading="dropping" @click="confirmDrop">删除（DROP）</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!testkitReachable" type="warning" :closable="false" style="margin-bottom:12px">
        无法连接 pg-testkit（8081 端口）。请确认 pg-testkit 服务已启动：<code>java -jar pg-testkit.jar</code>
      </el-alert>

      <el-table :data="tableStats" v-loading="loadingStats" size="small">
        <el-table-column prop="tableName" label="表名" min-width="200" />
        <el-table-column prop="rowCount" label="行数" width="140" />
        <el-table-column prop="description" label="说明" />
      </el-table>

      <div style="margin-top: 16px; color: #999; font-size: 12px">
        样例接口以 DEMO_ 前缀命名，可在「可视化接口开发 → 接口管理」查看
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { initDemoDb, resetDemoDb, dropDemoDb, getDemoDbStats } from '../api/testkit'

const tableStats = ref([])
const loadingStats = ref(false)
const initing = ref(false)
const resetting = ref(false)
const dropping = ref(false)
const testkitReachable = ref(true)

onMounted(() => refreshStats())

async function refreshStats() {
  loadingStats.value = true
  try {
    const stats = await getDemoDbStats()
    tableStats.value = stats?.tables || []
    testkitReachable.value = true
  } catch (e) {
    testkitReachable.value = false
    tableStats.value = []
  } finally {
    loadingStats.value = false
  }
}

async function doInit() {
  initing.value = true
  try {
    await initDemoDb()
    ElMessage.success('初始化完成')
    await refreshStats()
  } catch (e) {
    ElMessage.error('初始化失败：' + (e?.message || e))
  } finally {
    initing.value = false
  }
}

async function confirmReset() {
  try {
    await ElMessageBox.confirm('重置将清空所有 demo 表并重灌 Faker 生成的数据，确定继续？', '重置确认', { type: 'warning' })
  } catch { return }
  resetting.value = true
  try {
    await resetDemoDb()
    ElMessage.success('重置完成')
    await refreshStats()
  } catch (e) {
    ElMessage.error('重置失败：' + (e?.message || e))
  } finally {
    resetting.value = false
  }
}

async function confirmDrop() {
  try {
    await ElMessageBox.confirm('DROP 将删除整个样例业务库，数据不可恢复！确定继续？', '危险操作', {
      type: 'error',
      confirmButtonText: '我确认删除',
      cancelButtonText: '取消'
    })
  } catch { return }
  dropping.value = true
  try {
    await dropDemoDb()
    ElMessage.success('DROP 完成')
    await refreshStats()
  } catch (e) {
    ElMessage.error('DROP 失败：' + (e?.message || e))
  } finally {
    dropping.value = false
  }
}
</script>

<style scoped>
.testkit-demo-db { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
