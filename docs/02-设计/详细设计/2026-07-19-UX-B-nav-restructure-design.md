# UX-B 信息架构重整 设计文档

**日期**：2026-07-19
**交付单元**：阶段六 UX-B
**前置依赖**：SYS-3（`MenuPermission` 白名单 + `SideMenu.vue` 动态显隐）、SYS-5（`InterfaceWizard.vue`）
**关联总览**：`docs/02-设计/详细设计/2026-07-19-visual-refresh-and-fixes-overview.md`
**关联规划变更**：CHG-015（UX 批次归档与编号）
**本单元完成时追加变更编号**：CHG-017（信息架构重整）

---

## 一、目标

在不新增任何后端业务能力、不修改任何页面内部逻辑的前提下，通过重整菜单分组顺序、菜单分组内层次、顶栏「个人信息」入口跳转目标，让全站信息架构与用户实际操作路径一致：**先建后测**（模板/映射/路由配好后再用「报文格式转换」验证）、**关联前置**（转换模板在字段映射/加工之前呈现）、**分组聚焦**（庞大的接口转换菜单再切两层小节），并把顶栏「个人信息」从当前空动作接入到已有的用户权限管理页。

---

## 二、需求来源与范围

### 2.1 需求来源

原始反馈：仓库根 `111.txt`（2026-07-19 用户批次），归档编号见 `docs/03-开发/问题清单.md` §2026-07-19 批次 B 组。

| 本单元编号 | 111.txt 原编号 | 简述 |
|-----------|---------------|------|
| NAV-01 | #2 | 顶栏「个人信息」点击跳转到「用户权限管理」（路径 `/system/user`） |
| NAV-02 | #5 | SideMenu「报文格式转换」由分组首位下移到「接口转换配置」分组末尾（其定位是配好模板后的验证工具） |
| NAV-03 | #7 | SideMenu「转换模板管理」由分组末位上移到「字段映射配置」之前（模板 → 映射 → 加工 → 渠道 → 端口 → 格式转换 语义顺序） |
| NAV-04 | #7 派生 | 「接口转换配置」分组内部再拆两层小节（基础配置 / 转换规则 / 发布测试），减轻一次性展开 6 条平铺菜单的认知负担 |

### 2.2 范围（Do）

- 前端 `SideMenu.vue`：调整菜单项顺序 + 嵌入二级小节标题
- 前端 `TopBar.vue`：`handleCommand('profile')` 从空跳到 `/system/user`
- 后端 `MenuPermission.java`：白名单顺序同步调整（不影响集合语义，但便于对齐前端顺序检查）
- 单测：新增 Vue Test Utils 覆盖菜单渲染顺序 + TopBar 跳转 + 后端白名单顺序
- 变更记录：`变更记录.md` 追加 CHG-017；`需求拆分与最小实现方案.md` 追加阶段六 UX-B 节；`开发计划.md` 阶段六表格 UX-B 行标记完成

### 2.3 不含（Don't）

- **不新增任何菜单路由**（所有路由 `router/index.js` 中已存在）
- **不改任何业务页面内部功能**（`FormatConvert.vue` / `FieldMapping.vue` / `TemplateList.vue` 等的模板结构、API 调用、字段全部不动）
- **不修改角色权限矩阵**（admin/user/readonly 三个角色对每条路由的可见性沿用 SYS-3 结论）
- **不做「个人信息编辑页」**（点击「个人信息」直接进入现有「用户权限管理」列表页，用户可在列表中定位自己账号编辑；未来若有独立 profile 页再另立单元）
- **不做菜单折叠状态持久化的重设计**（sidebar 折叠状态目前由 `MainLayout.vue` 内 state 维护，不落 localStorage；本单元不引入）

---

## 三、现状分析

### 3.1 当前菜单结构（`SideMenu.vue` line 20~78）

```
系统概览                    /dashboard

接口转换配置                (el-sub-menu index="convert")
├─ 报文格式转换             /convert/format          ← 位置不合理（应在末尾）
├─ 字段映射配置             /convert/field-mapping
├─ 字段加工配置             /convert/field-process
├─ 渠道模板管理             /convert/channel
├─ 端口分发路由             /convert/port-route
└─ 转换模板管理             /convert/template        ← 位置不合理（应上移到映射之前）

可视化接口开发              (el-sub-menu index="interface")
├─ 接口配置向导             /interface/wizard
├─ 数据库连接管理           /interface/db
├─ 表结构管理               /interface/table
├─ 查询接口配置             /interface/dev
├─ 插入接口配置             /interface/insert
├─ 修改接口配置             /interface/update
├─ 删除接口配置             /interface/delete
├─ 接口管理                 /interface/list
├─ 分库分表配置             /interface/shard
├─ 字段公式管理             /interface/formula
└─ 缓存查询管理             /interface/cache

系统管理                    (el-sub-menu index="system")
├─ 日志管理                 /system/log
├─ 性能统计                 /system/stats
├─ 用户权限管理             /system/user
└─ 系统配置                 /system/config

辅助工具                    (el-sub-menu index="tools")
├─ 报文调试                 /tools/debug
└─ 接口文档                 /tools/swagger
```

**问题诊断：**

1. **接口转换配置**分组一次性展开 6 条菜单，无内部层次；用户找不到「先配什么后配什么」的心智路径。
2. **报文格式转换**位于分组首位，容易被误认为「入口页」；实际上它是「模板都配好之后拿一条原始报文测一下能否走通」的验证工具，应放末尾。
3. **转换模板管理**位于分组末位；实际它是「所有字段映射 / 字段加工 / 渠道 / 端口路由的容器」，应放在字段映射之前作为一切配置的起点。
4. **可视化接口开发**分组 11 条同样平铺，但用户对该模块反馈较好（111.txt 明确 #9/#10/#11 无疑问），本单元**暂不动**它，避免范围膨胀（预留后续单元）。

### 3.2 当前 TopBar 个人信息入口（`TopBar.vue` line 17~33、66~81）

```javascript
<el-dropdown trigger="click" @command="handleCommand">
  ...
  <el-dropdown-item command="profile">个人信息</el-dropdown-item>
  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
</el-dropdown>

async function handleCommand(command) {
  if (command === 'logout') { ... }
  // command === 'profile' 时无任何分支处理 → 点击后无反应
}
```

**问题诊断**：`handleCommand` 对 `'profile'` 命令**未做任何处理**，点击后无跳转、无提示、无 error，用户体验为"死按钮"。

### 3.3 后端 `MenuPermission.java` 现状

`ADMIN_MENUS` / `USER_MENUS` / `READONLY_MENUS` 三个白名单集合的**顺序**目前遵循 SYS-3 设计文档的顺序（与前端 SideMenu 原始顺序一致）。前端 `SideMenu.vue` 判断显隐用的是 `Array.includes(path)`，**集合语义与顺序无关**，因此**功能上无需改后端**。

但为便于代码 review、便于新人对照阅读两端顺序、便于未来若引入「按后端返回顺序渲染菜单」的策略时零改动，本单元将后端白名单顺序也同步刷新，作为"顺手清洁"。

---

## 四、设计方案

### 4.1 新菜单结构

```
系统概览                                        /dashboard

接口转换配置                                    (el-sub-menu index="convert")
│
├─ ── 基础配置 ──                              (el-menu-item-group title="基础配置")
│  ├─ 转换模板管理                              /convert/template          ← 从末位上移
│  └─ 渠道模板管理                              /convert/channel
│
├─ ── 转换规则 ──                              (el-menu-item-group title="转换规则")
│  ├─ 字段映射配置                              /convert/field-mapping
│  └─ 字段加工配置                              /convert/field-process
│
└─ ── 发布测试 ──                              (el-menu-item-group title="发布测试")
   ├─ 端口分发路由                              /convert/port-route
   └─ 报文格式转换                              /convert/format            ← 从首位下移到末位

可视化接口开发                                  (el-sub-menu index="interface")
├─ 接口配置向导                                 /interface/wizard          ← 不变
├─ 数据库连接管理                               /interface/db
├─ 表结构管理                                   /interface/table
├─ 查询接口配置                                 /interface/dev
├─ 插入接口配置                                 /interface/insert
├─ 修改接口配置                                 /interface/update
├─ 删除接口配置                                 /interface/delete
├─ 接口管理                                     /interface/list
├─ 分库分表配置                                 /interface/shard
├─ 字段公式管理                                 /interface/formula
└─ 缓存查询管理                                 /interface/cache

系统管理                                        (el-sub-menu index="system")
├─ 日志管理                                     /system/log
├─ 性能统计                                     /system/stats
├─ 用户权限管理                                 /system/user
└─ 系统配置                                     /system/config

辅助工具                                        (el-sub-menu index="tools")
├─ 报文调试                                     /tools/debug
└─ 接口文档                                     /tools/swagger
```

**分组理由（用一句话说清每组是干嘛的）：**

- **基础配置**：所有转换活动的容器与外部连接方——先建"模板"这个壳，再登记"渠道"这个外部适配。
- **转换规则**：填充模板内部的两条核心规则——字段级映射（重命名/类型对齐）与字段级加工（Trim/Pad/公式）。
- **发布测试**：把配好的模板对外暴露（端口分发路由）与用真实报文验证一遍（报文格式转换）。

**关键实现：**

Element Plus 提供 `<el-menu-item-group title="...">` 组件，专门用于给 `el-sub-menu` 的子项加二级小节标题，无需自定义 CSS，且天生适配 `collapse` 折叠模式（折叠时小节标题自动隐藏，展开时显示，与本框架整体一致）。

**权限过滤对小节标题的处理：**

若整个小节内所有菜单项对当前角色均不可见（例如 readonly 角色对整个「接口转换配置」都不可见，该分组已由外层 `v-if="hasConvert"` 隐藏；但假设未来出现「组内部分可见部分不可见」），小节标题也必须隐藏。方案：每个 `el-menu-item-group` 外层加 `v-if="hasGroupBaseConfig"` 类似的 computed，判断本小节内是否有任意可见项。

```javascript
const hasGroupBaseConfig = computed(() =>
  ['/convert/template', '/convert/channel'].some(p => can(p))
)
const hasGroupTransformRules = computed(() =>
  ['/convert/field-mapping', '/convert/field-process'].some(p => can(p))
)
const hasGroupPublishTest = computed(() =>
  ['/convert/port-route', '/convert/format'].some(p => can(p))
)
```

### 4.2 TopBar 个人信息下拉的行为设计

**行为定义**：点击「个人信息」→ `router.push('/system/user')`。

**权限保护**：
- `readonly` 角色对 `/system/user` **不在白名单**（SYS-3 明确），点击后路由守卫会拦截并跳 `/dashboard`。
- 但从用户体验角度，「下拉菜单里显示了一个点了没反应/被踢回首页的选项」不好。因此：**当且仅当 `userStore.allowedMenus.includes('/system/user')` 为 true 时才渲染「个人信息」项**；readonly 角色下拉菜单只有「退出登录」。

**新代码结构：**

```vue
<template>
  <el-dropdown-menu>
    <el-dropdown-item v-if="canProfile" command="profile">
      <el-icon><User /></el-icon>个人信息
    </el-dropdown-item>
    <el-dropdown-item divided command="logout">
      <el-icon><SwitchButton /></el-icon>退出登录
    </el-dropdown-item>
  </el-dropdown-menu>
</template>

<script setup>
import { User } from '@element-plus/icons-vue'  // 新增 import（原来只 import 了 UserFilled/SwitchButton）
const canProfile = computed(() => userStore.allowedMenus.includes('/system/user'))

async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/system/user')
    return
  }
  if (command === 'logout') { /* 原逻辑不变 */ }
}
</script>
```

> 注：当前 `TopBar.vue` 已 import `UserFilled`（作 avatar 图标用），但**没有** import 下拉项使用的 `User` 图标。当前代码 `<el-icon><User /></el-icon>` 依赖运行时是否被 Element Plus 全局挂载。为稳妥起见，本单元显式 `import { User } from '@element-plus/icons-vue'`。

### 4.3 后端 `MenuPermission.java` 同步变更

**白名单顺序更新**（三个数组内元素**顺序**按新菜单调整；集合成员**不增不减**）：

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
    "/interface/db", "/interface/table", "/interface/wizard",
    "/interface/dev", "/interface/insert", "/interface/update", "/interface/delete",
    "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
    // 系统管理
    "/system/log", "/system/stats", "/system/user", "/system/config",
    // 辅助工具
    "/tools/debug", "/tools/swagger"
);

public static final List<String> USER_MENUS = Arrays.asList(
    "/dashboard",
    "/convert/template", "/convert/channel",
    "/convert/field-mapping", "/convert/field-process",
    "/convert/port-route", "/convert/format",
    "/interface/db", "/interface/table", "/interface/wizard",
    "/interface/dev", "/interface/insert", "/interface/update",
    "/interface/list", "/interface/formula", "/interface/cache",
    "/system/log", "/system/stats",
    "/tools/debug", "/tools/swagger"
);

public static final List<String> READONLY_MENUS = Arrays.asList(
    "/dashboard",
    "/interface/list", "/interface/cache",
    "/tools/debug", "/tools/swagger"
);
```

**关键约束**：
- **集合成员保持完全一致**（用 `Set` 相等断言在测试中钉住）
- 仅 List 顺序改变；`Arrays.asList().contains()` 语义不受影响，SYS-3 的 `AuthController.getMenu()` 行为与守卫行为零变化
- 新增单测 `UX-B_MenuPermissionOrderTest.java` 验证：ADMIN_MENUS 中 `/convert/template` 的下标 < `/convert/field-mapping` 的下标 < `/convert/format` 的下标；集合仍与旧集合等价

---

## 五、前端组件变更清单

| 文件 | 变更类型 | 具体改动 |
|------|---------|---------|
| `frontend/src/components/layout/SideMenu.vue` | 修改（重排 + 引入 el-menu-item-group） | 1. 「接口转换配置」el-sub-menu 内部 6 条平铺 `el-menu-item` 改为 3 个 `<el-menu-item-group title="基础配置/转换规则/发布测试">` 包裹；2. 顺序按 4.1 节调整；3. 新增 3 个 computed `hasGroupBaseConfig/hasGroupTransformRules/hasGroupPublishTest`；4. `CONVERT_PATHS` 常量顺序同步 |
| `frontend/src/components/layout/TopBar.vue` | 修改（新增跳转逻辑 + 权限渲染） | 1. `import { User } from '@element-plus/icons-vue'` 显式引入；2. 新增 `canProfile` computed；3. 「个人信息」`el-dropdown-item` 加 `v-if="canProfile"`；4. `handleCommand` 增 `if (command === 'profile') router.push('/system/user')` 分支 |
| `frontend/src/router/index.js` | 不改 | 所有路由已存在，本单元零改动 |
| `frontend/src/store/user.js` | 不改 | `allowedMenus` 行为不变 |
| `frontend/src/views/**` | 不改 | 所有业务页面文件本单元零改动 |

**Element Plus 兼容性**：`<el-menu-item-group>` 是 Element Plus 2.x 原生组件（已在项目中通过 `element-plus` 依赖引入），无需新增依赖或全局注册。

---

## 六、后端变更清单

| 文件 | 变更类型 | 具体改动 |
|------|---------|---------|
| `backend/src/main/java/com/powergateway/config/MenuPermission.java` | 修改（仅 List 元素顺序） | ADMIN_MENUS / USER_MENUS / READONLY_MENUS 三个静态 List 元素顺序按 §4.3 调整；集合成员不增不减 |
| `backend/src/main/java/com/powergateway/controller/AuthController.java` | 不改 | `getMenu()` 逻辑不变 |
| `backend/src/main/java/com/powergateway/service/*` | 不改 | 无业务逻辑变化 |
| `backend/src/main/resources/db/init.sql` | 不改 | 无表结构变化 |

---

## 七、测试设计

### 7.1 前端测试（Vue Test Utils + Vitest）

**新增测试文件**：`frontend/src/components/layout/__tests__/UX-B_SideMenu.spec.js`

| 用例 | 验证点 |
|------|--------|
| SideMenu_admin角色_接口转换分组按新顺序渲染 | 挂载 SideMenu 并 mock `useUserStore` 返回 admin 全部菜单；断言「接口转换配置」sub-menu 下第一个可见 `el-menu-item` 的文本是「转换模板管理」，最后一个是「报文格式转换」 |
| SideMenu_admin角色_接口转换分组含三个小节标题 | 断言 sub-menu 内可查询到三个 `el-menu-item-group`，`title` 分别为「基础配置」「转换规则」「发布测试」 |
| SideMenu_admin角色_基础配置小节包含转换模板管理和渠道模板管理 | 断言「基础配置」小节 slot 内子项路径依次为 `/convert/template`、`/convert/channel` |
| SideMenu_admin角色_转换规则小节包含字段映射和字段加工 | 断言「转换规则」小节子项路径依次为 `/convert/field-mapping`、`/convert/field-process` |
| SideMenu_admin角色_发布测试小节包含端口路由和报文格式转换 | 断言「发布测试」小节子项路径依次为 `/convert/port-route`、`/convert/format` |
| SideMenu_readonly角色_接口转换整组不渲染 | mock `allowedMenus` 只含 readonly 5 项；断言 index="convert" 的 sub-menu 不存在 |
| SideMenu_user角色_接口转换整组渲染_三小节全在 | user 角色对 convert 6 条全可见，断言三个小节标题全部出现 |

**新增测试文件**：`frontend/src/components/layout/__tests__/UX-B_TopBar.spec.js`

| 用例 | 验证点 |
|------|--------|
| TopBar_admin角色_点击个人信息跳转到用户权限管理 | 挂载 TopBar，mock router.push，触发 `handleCommand('profile')`，断言 `router.push` 被调用参数为 `/system/user` |
| TopBar_admin角色_下拉菜单渲染个人信息项 | 打开 dropdown，断言 `command="profile"` 的 item 存在 |
| TopBar_readonly角色_下拉菜单不渲染个人信息项 | mock allowedMenus 不含 `/system/user`；断言 `command="profile"` 的 item 不存在 |
| TopBar_退出登录逻辑不受影响 | 保留原 logout 测试，确认 confirm 弹窗、logoutApi 调用、userStore.logout、跳 `/login` 全部与 CHG-011 前行为一致 |

### 7.2 后端测试

**新增测试文件**：`backend/src/test/java/com/powergateway/config/UX_B_MenuPermissionOrderTest.java`

```java
@ActiveProfiles("test")
class UX_B_MenuPermissionOrderTest {

    @Test void adminMenus_接口转换段_模板在映射之前() {
        int idxTemplate = MenuPermission.ADMIN_MENUS.indexOf("/convert/template");
        int idxMapping  = MenuPermission.ADMIN_MENUS.indexOf("/convert/field-mapping");
        int idxFormat   = MenuPermission.ADMIN_MENUS.indexOf("/convert/format");
        assertTrue(idxTemplate < idxMapping);
        assertTrue(idxMapping < idxFormat);
    }

    @Test void adminMenus_集合成员_与SYS3设计等价() {
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
    }

    @Test void userMenus_不含delete和shard和user和config() { ... }
    @Test void readonlyMenus_只含5项() { ... }
    @Test void userMenus_接口转换段_模板在映射之前() { ... }
}
```

**关键点**：只钉住"顺序关键位次" + "集合成员完整不变"，不钉每个下标绝对值（未来新增菜单时不脆）。

### 7.3 现有回归测试

`SYS3UserTest` 关于 `getMenu_adminRole_返回全部路由` 系列用例已用集合断言，本单元顺序调整**不会**影响，只需 `mvn test` 冒烟通过即可。

---

## 八、验收标准（Clickable 场景）

1. **admin 登录** → 展开侧栏「接口转换配置」→ 第一条见到「━ 基础配置 ━」小节标题，之后是「转换模板管理」「渠道模板管理」两项。
2. 继续下拉 → 见到「━ 转换规则 ━」，之后是「字段映射配置」「字段加工配置」两项。
3. 再下拉 → 见到「━ 发布测试 ━」，之后是「端口分发路由」「报文格式转换」两项。「报文格式转换」是分组最后一项。
4. 折叠侧栏（点击顶栏折叠按钮）→ 小节标题隐藏（Element Plus 默认行为），只留图标与菜单项 tooltip，无视觉断层或空白高度异常。
5. **user 登录**：接口转换分组三小节都在，与 admin 视觉一致（`user` 白名单对 `/convert/*` 6 条全可见）。
6. **readonly 登录**：「接口转换配置」整组不显示（外层 `hasConvert` 为 false）。
7. **admin 登录**，点击右上角头像 → 下拉菜单出现「个人信息」和「退出登录」→ 点击「个人信息」→ 跳到 `/system/user` 页面，页面标题「用户权限管理」，用户列表加载。
8. **readonly 登录**，点击右上角头像 → 下拉菜单**只有**「退出登录」，无「个人信息」项。
9. 直接 URL 访问 `/convert/format`（旧位置的入口）→ 页面正常渲染（本单元不改路由）；面包屑仍显示「首页 / 报文格式转换」。
10. 侧栏点击「转换模板管理」→ 路由与页面加载与本单元前一致（`views/convert/TemplateList.vue`），零回归。
11. 后端接口 `GET /api/auth/menu` admin 角色调用 → 返回的 24 项路径集合与本单元前完全一致（顺序可能变化，前端不依赖顺序）。

---

## 九、迁移与兼容

### 9.1 localStorage 中已存的数据

| 键 | 影响 | 处理 |
|----|------|------|
| `token` | 无影响，登录态延续 | 无需处理 |
| `allowedMenus` | 无影响，本单元不改集合成员，`SideMenu.can(path)` 仍返回相同结果 | 无需处理 |
| `userInfo` | 无影响 | 无需处理 |
| `sidebar-collapsed`（若存在） | 项目当前**未持久化** sidebar 折叠状态；不受影响 | 无需处理 |

**结论**：现有用户浏览器 localStorage 中数据零迁移，升级后刷新即可看到新菜单结构。

### 9.2 已发布的对外链接 / 收藏夹

所有路由 URL 保持不变（`/convert/format`、`/convert/template` 等）；已发布链接、浏览器收藏、外部文档中引用的绝对路径全部继续有效。菜单只是"看到的顺序"变了，不是"URL"变了。

### 9.3 面包屑 / 页面 title / SEO

`route.meta.title` 每条不变，`document.title` 与 `<el-breadcrumb>` 显示文案零变化。

### 9.4 后端 API 兼容

`GET /api/auth/menu` 返回的 `List<String>` 集合成员不变、顺序可能变化。前端消费方 `userStore.setAllowedMenus(menus)` 只做 `Array.includes()` 判断，不依赖顺序，兼容零风险。

### 9.5 未来扩展预留

- 若后续要给「可视化接口开发」11 条平铺菜单也做小节拆分（对应另开单元），复用本单元的 `el-menu-item-group` 模式即可，无需再抽公共组件。
- 若后续要做独立的「个人信息编辑页」（含头像上传、密码修改、偏好设置等），届时把 TopBar 个人信息跳转目标从 `/system/user` 改为 `/profile`，MenuPermission 追加 `/profile`（三角色全可见），本单元设计对该演进零阻碍。

---

## 十、变更记录追加内容（本单元完成时写入 `变更记录.md`）

```markdown
### CHG-017 2026-07-19 UX-B · 信息架构重整

- **日期**：2026-07-19
- **影响单元**：SYS-3（菜单白名单顺序）、SideMenu.vue、TopBar.vue
- **变更类型**：UX 优化（无功能新增，无接口变化）
- **背景**：CHG-015 UX 批次 B 组 4 项（NAV-01 ～ NAV-04）
- **变更前 → 变更后**：
  - 「接口转换配置」6 条平铺 → 3 个小节（基础配置 / 转换规则 / 发布测试）
  - 「报文格式转换」分组首位 → 分组末位
  - 「转换模板管理」分组末位 → 分组第一位
  - TopBar「个人信息」空按钮 → 跳转 `/system/user`；readonly 角色下不渲染此项
  - `MenuPermission.java` 三个 List 顺序刷新对齐前端（集合成员零变）
- **影响文件**：`SideMenu.vue`、`TopBar.vue`、`MenuPermission.java` + 3 个新测试文件
- **回归验证**：`SYS3UserTest` 集合断言通过，`GET /api/auth/menu` 返回集合成员完全一致
```

---

## 十一、不在本次范围内

- 「可视化接口开发」11 条平铺菜单的分组重整（用户明确对该模块无疑问，另立单元）
- 独立个人信息编辑页 `/profile` 的开发
- 菜单折叠状态持久化到 localStorage
- 后端「按角色配置化菜单」的可视化编辑界面（权限仍在 `MenuPermission.java` 中硬编码，SYS-3 结论沿用）
- 面包屑改造成多级（当前是"首页 / 单级 title"，本单元不动）
