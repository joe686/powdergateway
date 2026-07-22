<template>
  <el-container class="main-layout">
    <!-- 侧边菜单 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="side-aside">
      <SideMenu :collapsed="isCollapsed" />
    </el-aside>

    <el-container class="main-container">
      <!-- 顶栏 -->
      <el-header class="main-header">
        <TopBar v-model:collapsed="isCollapsed" />
      </el-header>

      <!-- 内容区 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="$route.path" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import SideMenu from './SideMenu.vue'
import TopBar from './TopBar.vue'
import { getMenuPermissions } from '@/api/auth'
import { useUserStore } from '@/store/user'

const isCollapsed = ref(false)

// FB-037: MainLayout 挂载即校验 token 有效性
// 覆盖 localStorage 残留旧 token 场景：token 存在让路由守卫放行进业务页，
// 但服务端已失效。主动拉一次 menu 探测，失败由 request.js 拦截器 401 分支自动清 store + 跳登录
onMounted(async () => {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) return
  try {
    const menus = await getMenuPermissions()
    userStore.setAllowedMenus(menus)
  } catch { /* 401 已由 request.js 处理 */ }
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

/* UX-A Wave6 修：MainLayout 三处硬编码色 → tokens 变量
   · side-aside 透明（让 SideMenu 跟随 tokens），非直接给深/白色
   · main-header 毛玻璃 backdrop-filter
   · main-content 透明让全屏背景 blob / stars 露出 */
.side-aside {
  background: transparent;
  transition: width 0.3s;
  overflow: hidden;
}

.main-container {
  overflow: hidden;
  background: transparent;
}

.main-header {
  padding: 0;
  height: 56px;
  line-height: 56px;
  background: var(--pg-glass-bg);
  backdrop-filter: blur(var(--pg-glass-blur));
  -webkit-backdrop-filter: blur(var(--pg-glass-blur));
  border-bottom: 1px solid var(--pg-glass-border);
  box-shadow: var(--pg-glass-shadow);
}

.main-content {
  background: transparent;
  overflow-y: auto;
  padding: 20px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
