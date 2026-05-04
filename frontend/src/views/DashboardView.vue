<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="never" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-title">{{ card.title }}</div>
            </div>
            <el-icon class="stat-icon" :style="{ color: card.color }">
              <component :is="card.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="welcome-card">
      <el-empty description="欢迎使用 PowerGateway 可视化接口开发平台">
        <template #image>
          <el-icon style="font-size: 80px; color: #c0c4cc;"><Connection /></el-icon>
        </template>
        <el-button type="primary" @click="$router.push('/interface/dev')">
          开始配置接口
        </el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const statCards = ref([
  { title: '已发布接口', value: 0, icon: 'Share', color: '#1890ff' },
  { title: '转换模板', value: 0, icon: 'Document', color: '#52c41a' },
  { title: '数据库连接', value: 0, icon: 'Coin', color: '#faad14' },
  { title: '今日调用', value: 0, icon: 'TrendCharts', color: '#f5222d' }
])
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.stat-title {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.stat-icon {
  font-size: 48px;
  opacity: 0.2;
}

.welcome-card {
  border-radius: 8px;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
