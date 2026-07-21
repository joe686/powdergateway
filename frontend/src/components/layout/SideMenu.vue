<template>
  <div class="side-menu">
    <!-- Logo 区域 -->
    <div class="logo" :class="{ collapsed: collapsed }">
      <el-icon class="logo-icon"><Connection /></el-icon>
      <span v-if="!collapsed" class="logo-text">PowerGateway</span>
    </div>

    <!-- 菜单 · UX-A Wave6：去除三个硬编码 color prop 让 tokens 生效 -->
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      class="menu"
      router
    >
      <!-- 首页 -->
      <el-menu-item v-if="can('/dashboard')" index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>系统概览</template>
      </el-menu-item>

      <!-- 接口转换配置（模块一） -->
      <el-sub-menu v-if="hasConvert" index="convert" data-ux-b-submenu="convert">
        <template #title>
          <el-icon><Switch /></el-icon>
          <span>接口转换配置</span>
        </template>

        <!-- 向导入口（UX-D） -->
        <el-menu-item v-if="can('/convert/wizard')" index="/convert/wizard">接口转换配置向导</el-menu-item>

        <!-- 基础配置 -->
        <el-menu-item-group
          v-if="hasGroupBaseConfig"
          title="基础配置"
          data-ux-b-group="base"
        >
          <el-menu-item
            v-if="can('/convert/template')"
            index="/convert/template"
            :data-ux-b-convert-path="'/convert/template'"
          >转换模板管理</el-menu-item>
          <el-menu-item
            v-if="can('/convert/channel')"
            index="/convert/channel"
            :data-ux-b-convert-path="'/convert/channel'"
          >渠道模板管理</el-menu-item>
        </el-menu-item-group>

        <!-- 转换规则 -->
        <el-menu-item-group
          v-if="hasGroupTransformRules"
          title="转换规则"
          data-ux-b-group="rules"
        >
          <el-menu-item
            v-if="can('/convert/field-mapping')"
            index="/convert/field-mapping"
            :data-ux-b-convert-path="'/convert/field-mapping'"
          >字段映射配置</el-menu-item>
          <el-menu-item
            v-if="can('/convert/field-process')"
            index="/convert/field-process"
            :data-ux-b-convert-path="'/convert/field-process'"
          >字段加工配置</el-menu-item>
          <!-- UX-C FN-03 · Wave6 修：字段公式管理归属"接口转换 / 转换规则"分组
               路由保留 /interface/formula（Sub-C2 复用历史 placeholder，不破坏兼容） -->
          <el-menu-item
            v-if="can('/interface/formula')"
            index="/interface/formula"
            data-ux-c-formula
          >字段公式管理</el-menu-item>
        </el-menu-item-group>

        <!-- 发布测试 -->
        <el-menu-item-group
          v-if="hasGroupPublishTest"
          title="发布测试"
          data-ux-b-group="publish"
        >
          <el-menu-item
            v-if="can('/convert/port-route')"
            index="/convert/port-route"
            :data-ux-b-convert-path="'/convert/port-route'"
          >端口分发路由</el-menu-item>
          <el-menu-item
            v-if="can('/convert/format')"
            index="/convert/format"
            :data-ux-b-convert-path="'/convert/format'"
          >报文格式转换</el-menu-item>
        </el-menu-item-group>
      </el-sub-menu>

      <!-- 可视化接口开发（模块二）· Wave8：拆 3 小节与接口转换 popup 布局对齐 -->
      <el-sub-menu v-if="hasInterface" index="interface">
        <template #title>
          <el-icon><Monitor /></el-icon>
          <span>可视化接口开发</span>
        </template>

        <!-- 主入口 · 向导 -->
        <el-menu-item v-if="can('/interface/wizard')" index="/interface/wizard">接口配置向导</el-menu-item>

        <!-- 数据源 -->
        <el-menu-item-group
          v-if="hasGroupInterfaceDataSource"
          title="数据源"
          data-ux-b-group="interface-data-source"
        >
          <el-menu-item v-if="can('/interface/db')" index="/interface/db">数据库连接管理</el-menu-item>
          <el-menu-item v-if="can('/interface/table')" index="/interface/table">表结构管理</el-menu-item>
        </el-menu-item-group>

        <!-- 接口定义 -->
        <el-menu-item-group
          v-if="hasGroupInterfaceDefine"
          title="接口定义"
          data-ux-b-group="interface-define"
        >
          <el-menu-item v-if="can('/interface/dev')" index="/interface/dev">查询接口配置</el-menu-item>
          <el-menu-item v-if="can('/interface/insert')" index="/interface/insert">插入接口配置</el-menu-item>
          <el-menu-item v-if="can('/interface/update')" index="/interface/update">修改接口配置</el-menu-item>
          <el-menu-item v-if="can('/interface/delete')" index="/interface/delete">删除接口配置</el-menu-item>
          <el-menu-item v-if="can('/interface/shard')" index="/interface/shard">分库分表配置</el-menu-item>
          <el-menu-item v-if="can('/interface/cache')" index="/interface/cache">缓存查询管理</el-menu-item>
        </el-menu-item-group>

        <!-- 发布运维 -->
        <el-menu-item-group
          v-if="hasGroupInterfaceOps"
          title="发布运维"
          data-ux-b-group="interface-ops"
        >
          <el-menu-item v-if="can('/interface/list')" index="/interface/list">接口管理</el-menu-item>
        </el-menu-item-group>
        <!-- Wave11：接口文档、配置导入/导出 移到「辅助工具」分组下（用户反馈：共用型功能归为工具） -->
      </el-sub-menu>

      <!-- 系统管理 -->
      <el-sub-menu v-if="hasSystem" index="system">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item v-if="can('/system/log')" index="/system/log">日志管理</el-menu-item>
        <el-menu-item v-if="can('/system/stats')" index="/system/stats">性能统计</el-menu-item>
        <el-menu-item v-if="can('/system/user')" index="/system/user">用户权限管理</el-menu-item>
        <el-menu-item v-if="can('/system/config')" index="/system/config">系统配置</el-menu-item>
      </el-sub-menu>

      <!-- 辅助工具 · Wave11：接口文档、配置导入/导出 从"可视化接口开发"迁入 -->
      <el-sub-menu v-if="hasTools" index="tools">
        <template #title>
          <el-icon><Tools /></el-icon>
          <span>辅助工具</span>
        </template>
        <el-menu-item v-if="can('/tools/debug')" index="/tools/debug">报文调试</el-menu-item>
        <el-menu-item v-if="can('/interface/doc')" index="/interface/doc">接口文档</el-menu-item>
        <el-menu-item v-if="can('/interface/import-export')" index="/interface/import-export">配置导入/导出</el-menu-item>
        <el-menu-item v-if="can('/tools/registry')" index="/tools/registry">注册中心管理</el-menu-item>
        <el-menu-item v-if="can('/tools/swagger')" index="/tools/swagger">Swagger 文档</el-menu-item>
      </el-sub-menu>

      <!-- TEST-1 测试环境（仅 TESTER 角色可见） -->
      <el-sub-menu v-if="hasTestkit" index="testkit">
        <template #title>
          <el-icon><Tools /></el-icon>
          <span>测试环境</span>
        </template>
        <el-menu-item v-if="can('/testkit/demo-db')" index="/testkit/demo-db">样例数据库管理</el-menu-item>
        <el-menu-item v-if="can('/testkit/mock-rules')" index="/testkit/mock-rules">Mock 后端规则</el-menu-item>
        <el-menu-item v-if="can('/testkit/mock-history')" index="/testkit/mock-history">Mock 请求历史</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'

defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const route = useRoute()
const userStore = useUserStore()
const activeMenu = computed(() => route.path)

function can(path) {
  return userStore.allowedMenus.includes(path)
}

// Wave6 修：/interface/formula 归属接口转换分组下的"转换规则"小节（UX-C 语义归位）
var CONVERT_PATHS  = ['/convert/wizard', '/convert/template', '/convert/channel',
                      '/convert/field-mapping', '/convert/field-process',
                      '/interface/formula',
                      '/convert/port-route', '/convert/format']
// Wave11：/interface/doc、/interface/import-export 从「接口开发」路径组挪到「辅助工具」组
var INTERFACE_PATHS = ['/interface/wizard', '/interface/db', '/interface/table', '/interface/dev',
                       '/interface/insert', '/interface/update', '/interface/delete',
                       '/interface/list', '/interface/shard', '/interface/cache']
var SYSTEM_PATHS   = ['/system/log', '/system/stats', '/system/user', '/system/config']
var TOOLS_PATHS    = ['/tools/debug', '/tools/swagger', '/interface/doc', '/interface/import-export', '/tools/registry']
var TESTKIT_PATHS  = ['/testkit/demo-db', '/testkit/mock-rules', '/testkit/mock-history']

const hasConvert   = computed(function() { return CONVERT_PATHS.some(function(p) { return can(p) }) })
const hasInterface = computed(function() { return INTERFACE_PATHS.some(function(p) { return can(p) }) })
const hasSystem    = computed(function() { return SYSTEM_PATHS.some(function(p) { return can(p) }) })
const hasTools     = computed(function() { return TOOLS_PATHS.some(function(p) { return can(p) }) })
const hasTestkit   = computed(function() { return TESTKIT_PATHS.some(function(p) { return can(p) }) })

const hasGroupBaseConfig     = computed(function() { return ['/convert/template', '/convert/channel'].some(function(p) { return can(p) }) })
const hasGroupTransformRules = computed(function() { return ['/convert/field-mapping', '/convert/field-process', '/interface/formula'].some(function(p) { return can(p) }) })
const hasGroupPublishTest    = computed(function() { return ['/convert/port-route', '/convert/format'].some(function(p) { return can(p) }) })

// Wave8：可视化接口开发的 3 小节（数据源 / 接口定义 / 发布运维）
const hasGroupInterfaceDataSource = computed(function() { return ['/interface/db', '/interface/table'].some(function(p) { return can(p) }) })
const hasGroupInterfaceDefine     = computed(function() { return ['/interface/dev', '/interface/insert', '/interface/update', '/interface/delete', '/interface/shard', '/interface/cache'].some(function(p) { return can(p) }) })
const hasGroupInterfaceOps        = computed(function() { return ['/interface/list'].some(function(p) { return can(p) }) })
</script>

<style scoped>
.side-menu {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0; /* Wave6 修：允许 flex 子项收缩，配合 .menu min-height:0 使菜单能独立滚动 */
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
  min-height: 0; /* Wave6 修：默认 min-height:auto 会撑破父高度，导致 overflow-y:auto 失效 */
  border-right: none;
  overflow-y: auto;
  overflow-x: hidden;
}
/* Wave6 修：菜单独立滚动条美化 + 一屏放不下时可拖动 */
.menu::-webkit-scrollbar { width: 6px; }
.menu::-webkit-scrollbar-thumb {
  background: var(--pg-line-strong);
  border-radius: 3px;
}
.menu::-webkit-scrollbar-thumb:hover {
  background: var(--pg-primary-soft);
}
.menu::-webkit-scrollbar-track { background: transparent; }
.menu:not(.el-menu--collapse) {
  width: 220px;
}

/* UX-A UI-02 · SideMenu 视觉增强 —— 仅样式，无结构变化 */
.el-menu {
  background: transparent !important;
  border-right: none !important;
}
.el-menu-item, .el-sub-menu__title {
  border-radius: var(--pg-radius-sm);
  margin: 2px 8px !important;
  transition: background 0.2s, color 0.2s;
}
.el-menu-item:hover, .el-sub-menu__title:hover {
  background: var(--pg-hover-surface) !important;
  color: var(--pg-primary) !important;
}
.el-menu-item.is-active {
  background: var(--pg-primary-soft) !important;
  color: var(--pg-text-white) !important;
  font-weight: 600;
  position: relative;
}
.el-menu-item.is-active::before {
  content: "";
  position: absolute;
  left: -8px; top: 8px; bottom: 8px;
  width: 3px;
  border-radius: 2px;
  background: var(--pg-primary-grad);
}
[data-theme="dark"] .el-menu-item.is-active::before {
  box-shadow: 0 0 12px rgba(109, 160, 255, 0.7);
}
</style>
