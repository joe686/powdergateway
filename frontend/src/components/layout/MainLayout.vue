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
import { ref } from 'vue'
import SideMenu from './SideMenu.vue'
import TopBar from './TopBar.vue'

const isCollapsed = ref(false)
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.side-aside {
  background-color: #001529;
  transition: width 0.3s;
  overflow: hidden;
}

.main-container {
  overflow: hidden;
}

.main-header {
  padding: 0;
  height: 56px;
  line-height: 56px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.main-content {
  background-color: #f0f2f5;
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
