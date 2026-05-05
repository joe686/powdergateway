<template>
  <div class="side-menu">
    <!-- Logo 区域 -->
    <div class="logo" :class="{ collapsed: collapsed }">
      <el-icon class="logo-icon"><Connection /></el-icon>
      <span v-if="!collapsed" class="logo-text">PowerGateway</span>
    </div>

    <!-- 菜单 -->
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      background-color="#001529"
      text-color="#ffffffa6"
      active-text-color="#ffffff"
      class="menu"
      router
    >
      <!-- 首页 -->
      <el-menu-item index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>系统概览</template>
      </el-menu-item>

      <!-- 接口转换配置（模块一） -->
      <el-sub-menu index="convert">
        <template #title>
          <el-icon><Switch /></el-icon>
          <span>接口转换配置</span>
        </template>
        <el-menu-item index="/convert/format">报文格式转换</el-menu-item>
        <el-menu-item index="/convert/field-mapping">字段映射配置</el-menu-item>
        <el-menu-item index="/convert/field-process">字段加工配置</el-menu-item>
        <el-menu-item index="/convert/channel">渠道模板管理</el-menu-item>
        <el-menu-item index="/convert/port-route">端口分发路由</el-menu-item>
        <el-menu-item index="/convert/template">转换模板管理</el-menu-item>
      </el-sub-menu>

      <!-- 可视化接口开发（模块二） -->
      <el-sub-menu index="interface">
        <template #title>
          <el-icon><Monitor /></el-icon>
          <span>可视化接口开发</span>
        </template>
        <el-menu-item index="/interface/db">数据库连接管理</el-menu-item>
        <el-menu-item index="/interface/table">表结构管理</el-menu-item>
        <el-menu-item index="/interface/dev">查询接口配置</el-menu-item>
        <el-menu-item index="/interface/insert">插入接口配置</el-menu-item>
        <el-menu-item index="/interface/update">修改接口配置</el-menu-item>
        <el-menu-item index="/interface/delete">删除接口配置</el-menu-item>
        <el-menu-item index="/interface/shard">分库分表配置</el-menu-item>
        <el-menu-item index="/interface/formula">字段公式管理</el-menu-item>
        <el-menu-item index="/interface/cache">缓存查询管理</el-menu-item>
      </el-sub-menu>

      <!-- 系统管理 -->
      <el-sub-menu index="system">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item index="/system/log">日志管理</el-menu-item>
        <el-menu-item index="/system/stats">性能统计</el-menu-item>
        <el-menu-item index="/system/user">用户权限管理</el-menu-item>
        <el-menu-item index="/system/config">系统配置</el-menu-item>
      </el-sub-menu>

      <!-- 辅助工具 -->
      <el-sub-menu index="tools">
        <template #title>
          <el-icon><Tools /></el-icon>
          <span>辅助工具</span>
        </template>
        <el-menu-item index="/tools/debug">报文调试</el-menu-item>
        <el-menu-item index="/tools/swagger">接口文档</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.side-menu {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 10px;
  border-bottom: 1px solid #ffffff1a;
  overflow: hidden;
  white-space: nowrap;
}

.logo.collapsed {
  justify-content: center;
  padding: 0;
}

.logo-icon {
  font-size: 22px;
  color: #1890ff;
  flex-shrink: 0;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 1px;
}

.menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu:not(.el-menu--collapse) {
  width: 220px;
}
</style>
