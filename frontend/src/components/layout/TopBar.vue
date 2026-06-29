<template>
  <div class="top-bar">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="top-left">
      <el-icon class="collapse-btn" @click="toggleCollapse">
        <Fold v-if="!collapsed" />
        <Expand v-else />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentTitle">{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧：用户信息 -->
    <div class="top-right">
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" :icon="UserFilled" />
          <span class="username">{{ username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>个人信息
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { logout as logoutApi } from '@/api/auth'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:collapsed'])

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const username = computed(() => userStore.username || '管理员')
const currentTitle = computed(() => route.meta?.title || '')

function toggleCollapse() {
  emit('update:collapsed', !props.collapsed)
}

async function handleCommand(command) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    try {
      await logoutApi()
    } catch (e) {
      // 忽略后端登出失败，本地状态照常清除
    }
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.top-bar {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #409eff;
}

.top-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #303133;
}
</style>
