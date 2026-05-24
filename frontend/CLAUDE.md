# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 常用命令

```bash
npm run dev       # 启动开发服务器，端口固定 5173，/api 代理到后端 localhost:8080（端口不得变更）
npm run build     # 生产构建，输出到 dist/
npm run preview   # 本地预览生产构建
```

## 架构概览

### 请求链路

所有 HTTP 请求必须通过 `src/api/request.js` 的 axios 实例发出，不得直接使用原生 axios。该实例负责：
- 自动在请求头写入 `satoken`（从 localStorage 读取）
- 响应体解包：后端返回 `{ code, message, data }` 结构，拦截器在 code !== 200 时统一 `ElMessage.error` 并 reject
- 401 自动清除 token 并跳转登录，500 统一提示

调用示例：
```js
import request from '@/api/request'
const res = await request.post('/auth/login', { username, password })
// res 即后端 Result<T>.data，拦截器已解包
```

### 认证与状态

- token 存 `localStorage.token`，同步到 `src/store/user.js`（Pinia）
- `useUserStore` 提供：`token`、`userInfo`、`allowedMenus`、`isLoggedIn`、`username`、`role`、`setToken()`、`setUserInfo()`、`setAllowedMenus()`、`logout()`
- 登录成功后需立即调用 `GET /api/auth/menu`，将返回的路由列表写入 `userStore.setAllowedMenus(menus)`；`allowedMenus` 持久化到 `localStorage.allowedMenus`，刷新不丢失
- 路由守卫（`src/router/index.js`）：
  1. 未携带 token → 跳 `/login`
  2. 已登录访问 `/login` → 跳首页
  3. 已登录且 `allowedMenus` 已加载时，访问不在列表中的路由 → 跳 `/dashboard`（越权拦截）

### 布局结构

```
MainLayout.vue          # 整体骨架，管理侧边栏折叠状态
├── SideMenu.vue        # 深色侧边菜单，el-menu router 模式，active 与当前路由联动
│                       # 每个 el-menu-item 用 v-if="can(path)" 控制显隐（SYS-3）
│                       # el-sub-menu 用 v-if="hasXxx" computed 控制整组显隐
└── TopBar.vue          # 顶栏：折叠按钮、面包屑（读 route.meta.title）、用户下拉
```

所有业务页面作为 `MainLayout` 的子路由渲染到 `<router-view>`，需在路由定义中写 `meta: { title: '...' }`。

### 路由约定

- `/login` — 无需鉴权（`meta: { requiresAuth: false }`）
- `/` 重定向 `/dashboard`
- 所有业务路由挂在 `/` 下作为 `MainLayout` 的子路由
- 未开发的业务页面使用 `src/views/placeholder/PlaceholderView.vue`，自动读取 `route.meta.title` 显示占位

### 新增业务页面的步骤

1. 在 `src/views/` 对应目录创建 `.vue` 文件
2. 在 `src/router/index.js` 的对应子路由处替换 `PlaceholderView` 为新组件
3. 在 `src/api/` 创建对应模块的请求文件（`import request from '@/api/request'`）

### UI 规范（继承自根 CLAUDE.md）

- 列表页统一：搜索栏 + `el-table` + `el-pagination` + 导出按钮
- 删除操作必须用 `el-popconfirm` 二次确认
- 多步骤配置使用 `el-steps`，中间状态存 Pinia store 并同步 `localStorage` 防丢失
- 字段映射拖拽使用 `vue-draggable-next`（**注意**：必须用 default slot + `v-for`，**禁止**使用 `<template #item>` — `#item` 是 vuedraggable v4 的 API，在 vue-draggable-next v2.x 中被完全忽略，列表渲染为空）
- 图表使用 `vue-echarts`（已安装）

### 可复用前端组件（待实现，实现后禁止重复造轮子）

| 组件 | 计划单元 | 被复用方 |
|------|---------|---------|
| `ConditionBuilder.vue` | M2-3 | M2-5、M2-6 |
| `InterfaceWizard.vue`（9步向导） | SYS-5 | 各接口配置页 |
