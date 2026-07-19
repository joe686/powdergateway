# UX-B · 信息架构重整 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过重排「接口转换配置」菜单顺序、嵌入三级小节标题、修复顶栏「个人信息」跳转与角色可见性，让全站信息架构与用户实际操作路径一致，覆盖 NAV-01 ~ NAV-04 四项问题。

**Architecture:** 前端 `SideMenu.vue` 内部把「接口转换配置」6 条平铺菜单包裹进 3 个 `<el-menu-item-group>`，顺序按「基础配置 → 转换规则 → 发布测试」重排；前端 `TopBar.vue` 为「个人信息」下拉项接入 `router.push('/system/user')` 并按 `userStore.allowedMenus.includes('/system/user')` 控制显隐；后端 `MenuPermission.java` 三个 List 顺序与前端对齐（集合成员零变）。

**Tech Stack:** Vue 3 `<script setup>`、Element Plus 2.7、Vitest 1.x + @vue/test-utils 2.x + happy-dom（新增单测依赖）、Pinia、JUnit 5

---

## Global Constraints

- 所有对话、注释、文档一律**中文**
- 前端严格通过 `src/api/request.js`，禁止原生 axios
- 前端 `vue-draggable-next` 只能用 default slot + v-for，禁 `<template #item>`（本单元不涉及拖拽，但作为项目通用约束保留）
- 后端配置库无 `@DS` 注解、审计库 `@DS("audit")`、业务库运行时 `DynamicDataSourceContextHolder.push(dbId)`（本单元不涉及审计库/业务库切换）
- 所有测试类必加 `@ActiveProfiles("test")`（后端）
- 每个 Task 独立可测试、独立可评审、独立 `git commit`
- TDD 严格 Red → Green → Refactor，禁止跳步
- **完成后**：追加 CHG-016 到 `docs/03-开发/变更记录.md`，把 `问题清单.md` 中 NAV-01 ~ NAV-04 从「待解决」搬到「已解决」
- 端口：后端 8080、前端 5173，不得变更
- Windows 10 + Git Bash 环境，awk/sed 谨慎使用（存在路径解析兼容问题）

---

## 文件清单预览

| 操作 | 文件 | 用途 |
|------|------|------|
| 修改 | `frontend/package.json` | 新增 vitest / @vue/test-utils / happy-dom 及 `test` script |
| 创建 | `frontend/vitest.config.js` | Vitest 配置（happy-dom 环境、别名） |
| 创建 | `frontend/src/tests/setup.js` | 全局 mock（ResizeObserver 等） |
| 修改 | `frontend/src/components/layout/SideMenu.vue` | 引入 `el-menu-item-group`、重排、新增 3 个 has* computed |
| 修改 | `frontend/src/components/layout/TopBar.vue` | `canProfile` computed、`command='profile'` 分支、`User` 图标显式引入 |
| 修改 | `backend/src/main/java/com/powergateway/config/MenuPermission.java` | ADMIN/USER/READONLY 三个 List 元素顺序同步刷新 |
| 创建 | `frontend/src/components/layout/__tests__/UX-B_SideMenu.spec.js` | 7 个前端 SideMenu 用例 |
| 创建 | `frontend/src/components/layout/__tests__/UX-B_TopBar.spec.js` | 4 个前端 TopBar 用例 |
| 创建 | `backend/src/test/java/com/powergateway/config/UxBMenuPermissionOrderTest.java` | 5 个后端顺序 + 集合等价用例 |
| 修改 | `docs/03-开发/变更记录.md` | 追加 CHG-016 |
| 修改 | `docs/03-开发/问题清单.md` | NAV-01 ~ NAV-04 由「待解决」搬至「已解决」 |
| 修改 | `docs/01-需求/需求拆分与最小实现方案.md` | 追加阶段六 UX-B 单元段 |
| 修改 | `docs/03-开发/开发计划.md` | 阶段六表格 UX-B 行标记完成 |

---

## Task 1: 前端单元测试基础设施

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/vitest.config.js`
- Create: `frontend/src/tests/setup.js`
- Create: `frontend/src/tests/smoke.spec.js`（一次性冒烟，验证配置可跑）

**Interfaces:**
- Consumes: 无（本 Task 是前端测试栈从零搭建）
- Produces:
  - `npm run test` 命令：运行 `vitest run`
  - Vitest 全局函数 `describe / it / expect / vi`（globals: true）
  - happy-dom 环境（`document / window / localStorage` 可用）
  - `@` 路径别名解析（与 `vite.config.js` 保持一致）
  - `ResizeObserver` global mock（Element Plus 组件依赖，未 mock 会报错）

- [ ] **Step 1: 写失败冒烟测试**

创建 `frontend/src/tests/smoke.spec.js`：

```js
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

describe('UX-B 前端测试栈冒烟', () => {
  it('happy-dom 环境可挂载最小组件', () => {
    const Hello = defineComponent({ setup: () => () => h('span', { class: 'hi' }, '你好') })
    const wrapper = mount(Hello)
    expect(wrapper.find('.hi').text()).toBe('你好')
  })

  it('happy-dom 提供 localStorage', () => {
    localStorage.setItem('k', 'v')
    expect(localStorage.getItem('k')).toBe('v')
    localStorage.removeItem('k')
  })
})
```

- [ ] **Step 2: 运行冒烟测试，确认失败（vitest 未安装）**

Run: `cd frontend && npm run test`
Expected: FAIL —— `npm ERR! Missing script: "test"` 或 `sh: vitest: command not found`

- [ ] **Step 3: 安装依赖 + 写配置**

安装：
```bash
cd frontend
npm install --save-dev vitest@^1.6.0 @vue/test-utils@^2.4.0 happy-dom@^14.0.0
```

修改 `frontend/package.json`，`scripts` 段追加：
```json
    "test": "vitest run",
    "test:watch": "vitest"
```

创建 `frontend/vitest.config.js`：
```js
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'happy-dom',
    globals: true,
    setupFiles: ['./src/tests/setup.js']
  }
})
```

创建 `frontend/src/tests/setup.js`：
```js
// Element Plus 内部组件（el-menu / el-dropdown 等）依赖 ResizeObserver，
// happy-dom 未内置该 API，需在全局 polyfill 以避免挂载即抛错。
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = ResizeObserverMock
}

// happy-dom 缺少 matchMedia，Element Plus 主题切换等间接调用会报错
if (typeof globalThis.matchMedia === 'undefined') {
  globalThis.matchMedia = () => ({
    matches: false,
    media: '',
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent() { return false }
  })
}
```

- [ ] **Step 4: 运行冒烟测试确认通过**

Run: `cd frontend && npm run test`
Expected: PASS —— 输出 `Test Files  1 passed (1)` 且 `Tests  2 passed (2)`

- [ ] **Step 5: 提交**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vitest.config.js frontend/src/tests/setup.js frontend/src/tests/smoke.spec.js
git commit -m "chore(UX-B): 引入 vitest + vue-test-utils 前端单测栈"
```

---

## Task 2: 后端 MenuPermission 顺序 Red-Green

**Files:**
- Create: `backend/src/test/java/com/powergateway/config/UxBMenuPermissionOrderTest.java`
- Modify: `backend/src/main/java/com/powergateway/config/MenuPermission.java`

**Interfaces:**
- Consumes: `com.powergateway.config.MenuPermission.ADMIN_MENUS / USER_MENUS / READONLY_MENUS`（现有 `public static final List<String>` 字段，签名不变）
- Produces:
  - `MenuPermission.ADMIN_MENUS` 顺序变为：`/dashboard → /convert/template → /convert/channel → /convert/field-mapping → /convert/field-process → /convert/port-route → /convert/format → /interface/db → ... → /tools/swagger`（集合成员完全不变）
  - `MenuPermission.USER_MENUS` 顺序按同样规则调整（保持"user 白名单不含 /interface/delete、/interface/shard、/system/user、/system/config"的既有集合语义）
  - `MenuPermission.READONLY_MENUS` 顺序保持不变（该 List 内没有 /convert/* 元素，顺序无实际调整需求，但测试也覆盖它以保证集合等价断言存在）

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/powergateway/config/UxBMenuPermissionOrderTest.java`：

```java
package com.powergateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@DisplayName("UX-B 菜单白名单顺序与集合等价")
class UxBMenuPermissionOrderTest {

    @Test
    @DisplayName("ADMIN_MENUS: 接口转换段 模板 < 映射 < 报文格式转换")
    void adminMenus_接口转换段_模板在映射之前_报文格式在末尾() {
        int idxTemplate = MenuPermission.ADMIN_MENUS.indexOf("/convert/template");
        int idxMapping  = MenuPermission.ADMIN_MENUS.indexOf("/convert/field-mapping");
        int idxFormat   = MenuPermission.ADMIN_MENUS.indexOf("/convert/format");
        assertTrue(idxTemplate >= 0 && idxMapping >= 0 && idxFormat >= 0, "三条路径必须都存在");
        assertTrue(idxTemplate < idxMapping, "/convert/template 应在 /convert/field-mapping 之前");
        assertTrue(idxMapping < idxFormat, "/convert/field-mapping 应在 /convert/format 之前");
    }

    @Test
    @DisplayName("ADMIN_MENUS: 集合成员与 SYS-3 原白名单严格等价")
    void adminMenus_集合成员_与SYS3设计等价() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "/dashboard",
            "/convert/format", "/convert/field-mapping", "/convert/field-process",
            "/convert/channel", "/convert/port-route", "/convert/template",
            "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
            "/interface/insert", "/interface/update", "/interface/delete",
            "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
            "/system/log", "/system/stats", "/system/user", "/system/config",
            "/tools/debug", "/tools/swagger"
        ));
        assertEquals(expected, new HashSet<>(MenuPermission.ADMIN_MENUS));
        assertEquals(24, MenuPermission.ADMIN_MENUS.size(), "ADMIN 白名单元素个数不能变");
    }

    @Test
    @DisplayName("USER_MENUS: 接口转换段 模板 < 映射 < 报文格式转换")
    void userMenus_接口转换段_模板在映射之前_报文格式在末尾() {
        int idxTemplate = MenuPermission.USER_MENUS.indexOf("/convert/template");
        int idxMapping  = MenuPermission.USER_MENUS.indexOf("/convert/field-mapping");
        int idxFormat   = MenuPermission.USER_MENUS.indexOf("/convert/format");
        assertTrue(idxTemplate >= 0 && idxMapping >= 0 && idxFormat >= 0);
        assertTrue(idxTemplate < idxMapping);
        assertTrue(idxMapping < idxFormat);
    }

    @Test
    @DisplayName("USER_MENUS: 集合成员与 SYS-3 原白名单严格等价")
    void userMenus_集合成员_与SYS3设计等价() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "/dashboard",
            "/convert/format", "/convert/field-mapping", "/convert/field-process",
            "/convert/channel", "/convert/port-route", "/convert/template",
            "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
            "/interface/insert", "/interface/update",
            "/interface/list", "/interface/formula", "/interface/cache",
            "/system/log", "/system/stats",
            "/tools/debug", "/tools/swagger"
        ));
        assertEquals(expected, new HashSet<>(MenuPermission.USER_MENUS));
    }

    @Test
    @DisplayName("READONLY_MENUS: 5 项集合原样保留")
    void readonlyMenus_只含5项() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "/dashboard",
            "/interface/list", "/interface/cache",
            "/tools/debug", "/tools/swagger"
        ));
        assertEquals(expected, new HashSet<>(MenuPermission.READONLY_MENUS));
        assertEquals(5, MenuPermission.READONLY_MENUS.size());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd backend && mvn -q test -Dtest=UxBMenuPermissionOrderTest`
Expected: FAIL —— `adminMenus_接口转换段_模板在映射之前_报文格式在末尾` 与 `userMenus_接口转换段_...` 两个用例 AssertionFailedError，因为当前 `ADMIN_MENUS` 中 `/convert/format` 在 `/convert/template` 之前（现状 `indexOf("/convert/format")=1`，`indexOf("/convert/template")=6`）。

- [ ] **Step 3: 最小实现（改后端 MenuPermission）**

将 `backend/src/main/java/com/powergateway/config/MenuPermission.java` 第 14~40 行的三个 List 定义**整体替换**为：

```java
    public static final List<String> ADMIN_MENUS = Arrays.asList(
        "/dashboard",
        // 接口转换配置 · 基础配置
        "/convert/template", "/convert/channel",
        // 接口转换配置 · 转换规则
        "/convert/field-mapping", "/convert/field-process",
        // 接口转换配置 · 发布测试
        "/convert/port-route", "/convert/format",
        // 可视化接口开发
        "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
        "/interface/insert", "/interface/update", "/interface/delete",
        "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
        // 系统管理
        "/system/log", "/system/stats", "/system/user", "/system/config",
        // 辅助工具
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> USER_MENUS = Arrays.asList(
        "/dashboard",
        // 接口转换配置 · 基础配置
        "/convert/template", "/convert/channel",
        // 接口转换配置 · 转换规则
        "/convert/field-mapping", "/convert/field-process",
        // 接口转换配置 · 发布测试
        "/convert/port-route", "/convert/format",
        // 可视化接口开发（user 不含 delete / shard）
        "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
        "/interface/insert", "/interface/update",
        "/interface/list", "/interface/formula", "/interface/cache",
        // 系统管理（user 不含 user / config）
        "/system/log", "/system/stats",
        // 辅助工具
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> READONLY_MENUS = Arrays.asList(
        "/dashboard",
        "/interface/list", "/interface/cache",
        "/tools/debug", "/tools/swagger"
    );
```

（`LOG_MENU_CONFIG_KEY` / `LOG_MENU_PATH` 两个常量保持原样，不动。）

- [ ] **Step 4: 运行测试确认通过**

Run: `cd backend && mvn -q test -Dtest=UxBMenuPermissionOrderTest`
Expected: PASS —— `Tests run: 5, Failures: 0, Errors: 0`

再跑一次 SYS-3 回归，确保集合断言不受顺序变化影响：
Run: `cd backend && mvn -q test -Dtest=SYS3UserTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/config/MenuPermission.java backend/src/test/java/com/powergateway/config/UxBMenuPermissionOrderTest.java
git commit -m "refactor(UX-B): MenuPermission 白名单顺序对齐新菜单结构（NAV-02/03）"
```

---

## Task 3: SideMenu.vue 重排 + 引入 el-menu-item-group

**Files:**
- Create: `frontend/src/components/layout/__tests__/UX-B_SideMenu.spec.js`
- Modify: `frontend/src/components/layout/SideMenu.vue`

**Interfaces:**
- Consumes:
  - `useUserStore().allowedMenus`（`ref<string[]>`，SYS-3 已定义）
  - Element Plus `<el-menu-item-group title="...">`（Element Plus 2.7 自带，无需新增依赖）
- Produces:
  - SideMenu 内「接口转换配置」sub-menu 内部有 3 个 `<el-menu-item-group>`，`title` 分别为「基础配置」「转换规则」「发布测试」
  - 每个小节内菜单顺序：基础配置 = `[/convert/template, /convert/channel]`；转换规则 = `[/convert/field-mapping, /convert/field-process]`；发布测试 = `[/convert/port-route, /convert/format]`
  - 新增 3 个 computed：`hasGroupBaseConfig` / `hasGroupTransformRules` / `hasGroupPublishTest`，逻辑 `.some(p => can(p))`
  - `CONVERT_PATHS` 数组顺序刷新为新顺序

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/components/layout/__tests__/UX-B_SideMenu.spec.js`：

```js
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import SideMenu from '@/components/layout/SideMenu.vue'
import { useUserStore } from '@/store/user'

const ADMIN_MENUS = [
  '/dashboard',
  '/convert/template', '/convert/channel',
  '/convert/field-mapping', '/convert/field-process',
  '/convert/port-route', '/convert/format',
  '/interface/db', '/interface/table', '/interface/wizard', '/interface/dev',
  '/interface/insert', '/interface/update', '/interface/delete',
  '/interface/list', '/interface/shard', '/interface/formula', '/interface/cache',
  '/system/log', '/system/stats', '/system/user', '/system/config',
  '/tools/debug', '/tools/swagger'
]
const USER_MENUS = [
  '/dashboard',
  '/convert/template', '/convert/channel',
  '/convert/field-mapping', '/convert/field-process',
  '/convert/port-route', '/convert/format',
  '/interface/db', '/interface/table', '/interface/wizard', '/interface/dev',
  '/interface/insert', '/interface/update',
  '/interface/list', '/interface/formula', '/interface/cache',
  '/system/log', '/system/stats',
  '/tools/debug', '/tools/swagger'
]
const READONLY_MENUS = [
  '/dashboard',
  '/interface/list', '/interface/cache',
  '/tools/debug', '/tools/swagger'
]

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div/>' } }]
  })
}

async function mountWith(allowedMenus) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.setAllowedMenus(allowedMenus)
  const router = makeRouter()
  router.push('/dashboard')
  await router.isReady()
  return mount(SideMenu, {
    global: { plugins: [pinia, router, ElementPlus] }
  })
}

describe('UX-B SideMenu 结构与顺序', () => {
  beforeEach(() => localStorage.clear())

  it('admin 角色：接口转换分组含 3 个小节标题（基础配置/转换规则/发布测试）', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const groupTitles = wrapper.findAll('.el-menu-item-group__title').map(el => el.text().trim())
    expect(groupTitles).toContain('基础配置')
    expect(groupTitles).toContain('转换规则')
    expect(groupTitles).toContain('发布测试')
  })

  it('admin 角色：接口转换分组第一个菜单项是「转换模板管理」，最后是「报文格式转换」', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const convertItems = wrapper.findAll('[data-ux-b-convert-path]')
    const paths = convertItems.map(el => el.attributes('data-ux-b-convert-path'))
    expect(paths[0]).toBe('/convert/template')
    expect(paths[paths.length - 1]).toBe('/convert/format')
  })

  it('admin 角色：基础配置小节包含 [/convert/template, /convert/channel]，顺序固定', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const g = wrapper.find('[data-ux-b-group="base"]')
    expect(g.exists()).toBe(true)
    const paths = g.findAll('[data-ux-b-convert-path]').map(e => e.attributes('data-ux-b-convert-path'))
    expect(paths).toEqual(['/convert/template', '/convert/channel'])
  })

  it('admin 角色：转换规则小节包含 [/convert/field-mapping, /convert/field-process]，顺序固定', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const g = wrapper.find('[data-ux-b-group="rules"]')
    expect(g.exists()).toBe(true)
    const paths = g.findAll('[data-ux-b-convert-path]').map(e => e.attributes('data-ux-b-convert-path'))
    expect(paths).toEqual(['/convert/field-mapping', '/convert/field-process'])
  })

  it('admin 角色：发布测试小节包含 [/convert/port-route, /convert/format]，顺序固定', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const g = wrapper.find('[data-ux-b-group="publish"]')
    expect(g.exists()).toBe(true)
    const paths = g.findAll('[data-ux-b-convert-path]').map(e => e.attributes('data-ux-b-convert-path'))
    expect(paths).toEqual(['/convert/port-route', '/convert/format'])
  })

  it('readonly 角色：整个接口转换分组不渲染', async () => {
    const wrapper = await mountWith(READONLY_MENUS)
    expect(wrapper.find('[data-ux-b-submenu="convert"]').exists()).toBe(false)
  })

  it('user 角色：接口转换分组渲染，3 个小节全在', async () => {
    const wrapper = await mountWith(USER_MENUS)
    expect(wrapper.find('[data-ux-b-submenu="convert"]').exists()).toBe(true)
    expect(wrapper.find('[data-ux-b-group="base"]').exists()).toBe(true)
    expect(wrapper.find('[data-ux-b-group="rules"]').exists()).toBe(true)
    expect(wrapper.find('[data-ux-b-group="publish"]').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm run test -- UX-B_SideMenu`
Expected: FAIL —— 全部 7 个用例失败，因为当前 `SideMenu.vue` 无 `data-ux-b-*` 属性也无 `el-menu-item-group`。

- [ ] **Step 3: 最小实现（重写 SideMenu.vue 接口转换分组）**

修改 `frontend/src/components/layout/SideMenu.vue`：

**改动 A**：将模板中第 26~37 行「接口转换配置」`<el-sub-menu>` 整段**替换**为：

```html
      <!-- 接口转换配置（模块一） -->
      <el-sub-menu v-if="hasConvert" index="convert" data-ux-b-submenu="convert">
        <template #title>
          <el-icon><Switch /></el-icon>
          <span>接口转换配置</span>
        </template>

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
```

**改动 B**：把 `<script setup>` 中第 103~104 行 `CONVERT_PATHS` 数组顺序改为新顺序，并在其后追加 3 个 computed：

```js
var CONVERT_PATHS  = ['/convert/template', '/convert/channel',
                      '/convert/field-mapping', '/convert/field-process',
                      '/convert/port-route', '/convert/format']
```

在第 114 行 `const hasTools` 定义之后追加：

```js
const hasGroupBaseConfig     = computed(function() { return ['/convert/template', '/convert/channel'].some(function(p) { return can(p) }) })
const hasGroupTransformRules = computed(function() { return ['/convert/field-mapping', '/convert/field-process'].some(function(p) { return can(p) }) })
const hasGroupPublishTest    = computed(function() { return ['/convert/port-route', '/convert/format'].some(function(p) { return can(p) }) })
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd frontend && npm run test -- UX-B_SideMenu`
Expected: PASS —— `Tests  7 passed (7)`

再全量跑一次前端测试防退化：
Run: `cd frontend && npm run test`
Expected: PASS —— smoke + SideMenu 共 9 个用例全绿

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/layout/SideMenu.vue frontend/src/components/layout/__tests__/UX-B_SideMenu.spec.js
git commit -m "feat(UX-B): SideMenu 接口转换分组重排为 3 小节（NAV-02/03/04）"
```

---

## Task 4: TopBar.vue 个人信息接入 + 角色可见性

**Files:**
- Create: `frontend/src/components/layout/__tests__/UX-B_TopBar.spec.js`
- Modify: `frontend/src/components/layout/TopBar.vue`

**Interfaces:**
- Consumes:
  - `useUserStore().allowedMenus`（判断 `/system/user` 是否可见）
  - `vue-router` 的 `useRouter().push`
- Produces:
  - `canProfile` computed：`userStore.allowedMenus.includes('/system/user')`
  - 「个人信息」`<el-dropdown-item>` 加 `v-if="canProfile"`
  - `handleCommand('profile')` 分支：`router.push('/system/user')` 并 `return`
  - `User` 图标显式 `import`（原代码用了 `<User />` 但没 import，靠隐式全局挂载）

- [ ] **Step 1: 写失败测试**

创建 `frontend/src/components/layout/__tests__/UX-B_TopBar.spec.js`：

```js
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessageBox } from 'element-plus'
import TopBar from '@/components/layout/TopBar.vue'
import { useUserStore } from '@/store/user'

// mock 后端登出接口，避免真实网络
vi.mock('@/api/auth', () => ({
  logout: vi.fn().mockResolvedValue(null)
}))

const ADMIN_MENUS = [
  '/dashboard', '/system/user', '/system/log', '/tools/debug'
]
const READONLY_MENUS = [
  '/dashboard', '/interface/list', '/tools/debug'
]

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/login', component: { template: '<div/>' } },
      { path: '/system/user', meta: { title: '用户权限管理' }, component: { template: '<div/>' } },
      { path: '/:pathMatch(.*)*', component: { template: '<div/>' } }
    ]
  })
  return router
}

async function mountWith(allowedMenus) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.setAllowedMenus(allowedMenus)
  store.setUserInfo({ username: 'tester', role: 'admin' })
  const router = makeRouter()
  router.push('/')
  await router.isReady()
  const wrapper = mount(TopBar, {
    props: { collapsed: false },
    global: { plugins: [pinia, router, ElementPlus] }
  })
  return { wrapper, router }
}

describe('UX-B TopBar 个人信息入口', () => {
  beforeEach(() => localStorage.clear())

  it('admin 角色：点击「个人信息」跳转到 /system/user', async () => {
    const { wrapper, router } = await mountWith(ADMIN_MENUS)
    const pushSpy = vi.spyOn(router, 'push')
    // 直接调用暴露方法，避开 el-dropdown 触发 DOM 层依赖
    await wrapper.vm.handleCommand('profile')
    await flushPromises()
    expect(pushSpy).toHaveBeenCalledWith('/system/user')
  })

  it('admin 角色：canProfile 为 true，下拉菜单渲染个人信息项', async () => {
    const { wrapper } = await mountWith(ADMIN_MENUS)
    expect(wrapper.vm.canProfile).toBe(true)
  })

  it('readonly 角色：canProfile 为 false，下拉菜单不渲染个人信息项', async () => {
    const { wrapper } = await mountWith(READONLY_MENUS)
    expect(wrapper.vm.canProfile).toBe(false)
  })

  it('退出登录逻辑保持：confirm 通过后调用 userStore.logout 并跳 /login', async () => {
    const { wrapper, router } = await mountWith(ADMIN_MENUS)
    const store = useUserStore()
    const logoutSpy = vi.spyOn(store, 'logout')
    const pushSpy = vi.spyOn(router, 'push')
    // stub confirm 直接 resolve
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValueOnce('confirm')
    await wrapper.vm.handleCommand('logout')
    await flushPromises()
    expect(logoutSpy).toHaveBeenCalled()
    expect(pushSpy).toHaveBeenCalledWith('/login')
  })
})
```

> **测试策略说明**：`handleCommand` 与 `canProfile` 通过 `<script setup>` + `defineExpose` 暴露给测试直接调用，避开 Element Plus dropdown 的 DOM/焦点依赖（happy-dom 下不稳定）。这就是 Step 3 里需要在 TopBar 中加 `defineExpose` 的原因。

- [ ] **Step 2: 运行测试确认失败**

Run: `cd frontend && npm run test -- UX-B_TopBar`
Expected: FAIL —— 用例 1 `expect(pushSpy).toHaveBeenCalledWith('/system/user')` 失败（当前 handleCommand 对 profile 无分支）；用例 2/3 `wrapper.vm.canProfile` undefined（当前无此 computed 且无 defineExpose）。

- [ ] **Step 3: 最小实现（改 TopBar.vue）**

修改 `frontend/src/components/layout/TopBar.vue`：

**改动 A**：把第 24~31 行 `<el-dropdown-menu>` 内的两个 dropdown item 中的「个人信息」加 `v-if`：

```html
          <el-dropdown-menu>
            <el-dropdown-item v-if="canProfile" command="profile">
              <el-icon><User /></el-icon>个人信息
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
```

**改动 B**：把第 42 行 `import { UserFilled } from '@element-plus/icons-vue'` 改为：

```js
import { UserFilled, User, SwitchButton, Fold, Expand, ArrowDown } from '@element-plus/icons-vue'
```

（`User` 是新增；`SwitchButton / Fold / Expand / ArrowDown` 原代码模板里用到但没显式 import，一并补齐避免测试环境 happy-dom 下 `[Vue warn] Failed to resolve component`）

**改动 C**：在第 60 行 `const currentTitle = ...` 之后追加：

```js
const canProfile = computed(() => userStore.allowedMenus.includes('/system/user'))
```

**改动 D**：把第 66~81 行 `handleCommand` 函数**替换**为：

```js
async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/system/user')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return // 用户取消
    }
    try {
      await logoutApi()
    } catch (e) {
      // 忽略后端登出失败，本地状态照常清除
    }
    userStore.logout()
    router.push('/login')
  }
}
```

**改动 E**：在 `<script setup>` 末尾追加 `defineExpose` 供单测调用：

```js
defineExpose({ handleCommand, canProfile })
```

- [ ] **Step 4: 运行测试确认通过**

Run: `cd frontend && npm run test -- UX-B_TopBar`
Expected: PASS —— `Tests  4 passed (4)`

再全量跑一次前端测试防退化：
Run: `cd frontend && npm run test`
Expected: PASS —— smoke + SideMenu + TopBar 共 13 个用例全绿

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/layout/TopBar.vue frontend/src/components/layout/__tests__/UX-B_TopBar.spec.js
git commit -m "feat(UX-B): TopBar 个人信息接入 /system/user 并按角色可见（NAV-01）"
```

---

## Task 5: 端到端手工回归（不改代码，只跑与截图）

**Files:**
- 无代码修改；本 Task 仅执行手工回归确认交付质量

**Interfaces:**
- Consumes: Task 2 / 3 / 4 的交付（后端启动 + 前端启动）
- Produces: 无源码产物，只产出「验收通过」结论，供合并前信心

- [ ] **Step 1: 启动后端**

```bash
cd backend && mvn spring-boot:run
```

后台运行，等待 `Started PowergatewayApplication` 日志。

- [ ] **Step 2: 启动前端**

新终端：
```bash
cd frontend && npm run dev
```

等待 `Local: http://localhost:5173/`。

- [ ] **Step 3: admin 场景走查**

浏览器打开 `http://localhost:5173`，用 `admin / Admin@123` 登录，逐项验证：

1. 展开左侧「接口转换配置」→ 第一条是「━ 基础配置 ━」小节标题，之下依次「转换模板管理」「渠道模板管理」
2. 继续 → 「━ 转换规则 ━」，之下依次「字段映射配置」「字段加工配置」
3. 继续 → 「━ 发布测试 ━」，之下依次「端口分发路由」「报文格式转换」；「报文格式转换」是分组末尾
4. 折叠侧栏（顶栏折叠按钮）→ 小节标题隐藏，只留图标 + 菜单项 tooltip，无空白高度异常
5. 点右上角头像 → 下拉菜单出现「个人信息」和「退出登录」
6. 点「个人信息」→ 跳到 `/system/user`，面包屑「首页 / 用户权限管理」，用户列表加载
7. 直接输入 URL `/convert/format` → 页面正常渲染（路由未改）
8. 侧栏点「转换模板管理」→ 加载 `TemplateList.vue`，功能与本单元前一致

- [ ] **Step 4: readonly 场景走查**

用 readonly 角色账号登录（若不存在则先由 admin 创建：账号 `readonly_ux_b` / `Read@123` / 角色 readonly）：

1. 侧栏「接口转换配置」整组不显示
2. 右上角下拉菜单**只有**「退出登录」，无「个人信息」项

- [ ] **Step 5: 后端接口回归**

用 admin 的 satoken 调用：

```bash
curl -s -H "satoken: <admin-token>" http://localhost:8080/api/auth/menu | python -m json.tool
```

Expected: 返回 `data` 数组长度 24，集合成员与本单元前完全一致（顺序变化不影响前端 `.includes()` 判断）。

- [ ] **Step 6: 关闭后端 / 前端进程**

各终端 Ctrl+C。

- [ ] **Step 7: 无代码变更，跳过 commit**

本 Task 无源码产出，无需 `git commit`。若走查中发现回归 bug，回到 Task 3 或 4 修正后重跑对应单测。

---

## Task 6: 全量测试与文档收尾

**Files:**
- Modify: `docs/03-开发/变更记录.md`（追加 CHG-016）
- Modify: `docs/03-开发/问题清单.md`（NAV-01 ~ 04 由「待解决」搬至「已解决」）
- Modify: `docs/01-需求/需求拆分与最小实现方案.md`（追加阶段六 UX-B 单元段）
- Modify: `docs/03-开发/开发计划.md`（阶段六表格 UX-B 行标记完成）

**Interfaces:**
- Consumes: Task 1 ~ 5 全部交付完成
- Produces: 项目文档三件套（变更记录 / 问题清单 / 需求拆分 / 开发计划）与本单元实现同步

- [ ] **Step 1: 全量测试**

Run:
```bash
cd backend && mvn -q test
cd ../frontend && npm run test
```

Expected: 后端全绿（既有 326+ 用例 + 本单元新增 5 用例）；前端 13 用例全绿。

- [ ] **Step 2: 追加 CHG-016 到变更记录**

在 `docs/03-开发/变更记录.md` **末尾**（`CHG-015` 段落之后）追加：

```markdown
---

### CHG-016 2026-07-19 UX-B · 信息架构重整

- **日期**：2026-07-19
- **影响单元**：SYS-3（菜单白名单顺序）、SideMenu.vue、TopBar.vue
- **变更类型**：UX 优化（无功能新增，无接口签名变化）
- **背景**：CHG-015 UX 批次 B 组 4 项（NAV-01 ~ NAV-04）
- **变更前 → 变更后**：
  - 「接口转换配置」6 条平铺 → 3 个小节（基础配置 / 转换规则 / 发布测试）
  - 「报文格式转换」分组首位 → 分组末位
  - 「转换模板管理」分组末位 → 分组第一位
  - TopBar「个人信息」空按钮 → 跳转 `/system/user`；readonly 角色下不渲染此项
  - `MenuPermission.java` 三个 List 顺序刷新对齐前端（集合成员零变）
- **影响文件**：
  - `frontend/src/components/layout/SideMenu.vue`
  - `frontend/src/components/layout/TopBar.vue`
  - `backend/src/main/java/com/powergateway/config/MenuPermission.java`
  - 新增 `frontend/src/components/layout/__tests__/UX-B_SideMenu.spec.js`（7 用例）
  - 新增 `frontend/src/components/layout/__tests__/UX-B_TopBar.spec.js`（4 用例）
  - 新增 `backend/src/test/java/com/powergateway/config/UxBMenuPermissionOrderTest.java`（5 用例）
  - 新增 `frontend/vitest.config.js` / `frontend/src/tests/setup.js` / `frontend/src/tests/smoke.spec.js`
  - 修改 `frontend/package.json`：新增 vitest / @vue/test-utils / happy-dom devDeps
- **需求文档更新**：
  - `docs/01-需求/需求拆分与最小实现方案.md` 追加阶段六 UX-B 单元段
  - `docs/03-开发/开发计划.md` 阶段六表格 UX-B 行标记完成
- **回归验证**：`SYS3UserTest` 集合断言通过，`GET /api/auth/menu` 返回集合成员完全一致
- **原因**：问题清单 2026-07-19 批次 B 组 NAV-01 ~ NAV-04
```

- [ ] **Step 3: 问题清单 NAV-01 ~ 04 从「待解决」移入「已解决」**

在 `docs/03-开发/问题清单.md` 中：

1. 找到 §「2026-07-19 批次（源自 `111.txt`）—— 全站体验重塑与功能补齐」下的「B 组 · 信息架构重整」表格（约 142~149 行），在表格上方追加一行状态标签：

```markdown
> **状态**：已完成（CHG-016，2026-07-19）
```

2. 在文件的「## 已解决」段落（约第 3 行下方）追加：

```markdown
### 2026-07-19 UX-B · 信息架构重整（已解决 · CHG-016）

- **NAV-01**：顶栏「个人信息」→ 跳转 `/system/user`；readonly 角色下不渲染该菜单项
- **NAV-02**：「报文格式转换」下移到「接口转换配置」分组末位
- **NAV-03**：「转换模板管理」上移到「字段映射配置」之前
- **NAV-04**：「接口转换配置」内部拆为 3 个小节（基础配置 / 转换规则 / 发布测试）
- **实现文件**：`frontend/src/components/layout/SideMenu.vue`、`TopBar.vue`、`backend/src/main/java/com/powergateway/config/MenuPermission.java`
- **回归验证**：后端 `UxBMenuPermissionOrderTest`（5 用例）+ 前端 `UX-B_SideMenu.spec.js`（7 用例）+ `UX-B_TopBar.spec.js`（4 用例）全绿
```

- [ ] **Step 4: 需求拆分文档追加阶段六 UX-B 单元段**

在 `docs/01-需求/需求拆分与最小实现方案.md` 末尾（阶段六段落中，若已存在阶段六目录则加子节，若不存在则新建阶段六段落）追加：

```markdown
### 阶段六 · UX-B 信息架构重整

**范围（Do）**：
- SideMenu 内「接口转换配置」6 条平铺菜单包裹进 3 个 `el-menu-item-group`：基础配置、转换规则、发布测试
- 分组内菜单按新语义顺序排列：模板 → 渠道 → 映射 → 加工 → 端口路由 → 格式转换
- TopBar 顶栏「个人信息」下拉项由无跳转改为 `router.push('/system/user')`；readonly 角色下不渲染此项
- 后端 `MenuPermission` 三个 List 顺序同步刷新（集合成员零变化）

**不含（Don't）**：
- 不新增任何路由（所有路径 SYS-3 已注册）
- 不改任何业务页面内部逻辑
- 不修改角色权限矩阵（沿用 SYS-3 结论）
- 不做独立「个人信息编辑页」
- 不重设计「可视化接口开发」11 条平铺菜单（用户反馈良好，另立单元）

**实现方案**：见 `docs/02-设计/详细设计/2026-07-19-UX-B-nav-restructure-design.md`

**验收标准**：见同上详细设计 §八「验收标准（Clickable 场景）」11 条
```

- [ ] **Step 5: 开发计划表格 UX-B 行标记完成**

在 `docs/03-开发/开发计划.md` 中找到阶段六 UX-B 行，将其状态列由「进行中/待开始」改为 `✅ 已完成（2026-07-19，CHG-016）`。

- [ ] **Step 6: 最终提交**

```bash
git add docs/03-开发/变更记录.md docs/03-开发/问题清单.md docs/01-需求/需求拆分与最小实现方案.md docs/03-开发/开发计划.md
git commit -m "docs(UX-B): 追加 CHG-016 与文档三件套同步（NAV-01/02/03/04 已解决）"
```

---

## 验收门槛回顾

| # | 验收项 | 验证方式 |
|---|--------|---------|
| 1 | 前端单测栈可运行 | Task 1 Step 4 `npm run test` PASS |
| 2 | 后端菜单白名单顺序 + 集合等价 | Task 2 Step 4 `mvn test -Dtest=UxBMenuPermissionOrderTest` 5 用例 PASS |
| 3 | SideMenu 三小节结构 + 顺序 + 角色可见性 | Task 3 Step 4 `UX-B_SideMenu.spec.js` 7 用例 PASS |
| 4 | TopBar 个人信息跳转 + 角色可见性 + 退出登录不退化 | Task 4 Step 4 `UX-B_TopBar.spec.js` 4 用例 PASS |
| 5 | admin/readonly 端到端手工场景 8 项全部符合预期 | Task 5 Step 3 / Step 4 |
| 6 | `GET /api/auth/menu` admin 返回 24 项集合成员完全一致 | Task 5 Step 5 |
| 7 | 后端全量 `mvn test` 全绿（含 SYS3UserTest 回归） | Task 6 Step 1 |
| 8 | CHG-016 + 问题清单 + 需求拆分 + 开发计划 四文档同步落地 | Task 6 Step 2 / 3 / 4 / 5 |

---

## 关键顺序要点

1. **Task 1 必须最先**：前端单测栈从零搭建，是 Task 3 / 4 的前置基础。
2. **Task 2 与 Task 3 无强依赖**：后端 MenuPermission 与前端 SideMenu 是两个独立文件，理论可并行；但顺序上建议先做后端，因为后端顺序变更提供了"前端应该长什么样"的规范锚点。
3. **Task 4 独立于 Task 3**：TopBar 与 SideMenu 无共享文件；可与 Task 3 并行分派给不同 subagent，但共享同一测试栈（Task 1 产物），仍必须在 Task 1 之后。
4. **Task 5 手工回归必须在 Task 2 / 3 / 4 全部合入之后**：涉及后端 + 前端联动。
5. **Task 6 文档收尾必须最末**：CHG-016 描述里要引用所有前置 Task 的最终文件清单与测试用例数量。
6. **若采用 subagent 并行策略**：Task 1 由主 agent 完成后，Task 2 / 3 / 4 可并行分派（3 subagents），合并回主线时按 2 → 3 → 4 顺序 rebase 避免测试文件冲突，最后主 agent 执行 Task 5 / 6。
