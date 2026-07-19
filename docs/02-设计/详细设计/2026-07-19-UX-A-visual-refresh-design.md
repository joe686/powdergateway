# UX-A · 全站视觉重塑 + 双主题切换 · 详细设计

- **单元编号**：UX-A（阶段六 UX 批次）
- **覆盖问题**：UI-01 / UI-02 / UI-03 / UI-04 / UI-05 / UI-06
- **依赖**：无（第 1 波，作为其它 4 组的视觉底座）
- **视觉参考**：`mockups/v3-combined-switchable/index.html`（用户 2026-07-19 确认）
- **总览文档**：`docs/02-设计/详细设计/2026-07-19-visual-refresh-and-fixes-overview.md`

## 1. 目标

在不改动业务组件语义的前提下，通过覆盖 Element Plus 的 CSS 变量层，把全站视觉换成"毛玻璃 + 圆角 + 双主题"风格。所有 22 个业务页面自动继承新风格，无需逐页改。同时提供 4 种主题切换模式（手动 / 定时 / 跟随系统 / 日出日落），偏好双存储（`localStorage` + `sys_user.theme_pref`）跨设备同步。

## 2. 需求来源与范围

| 编号 | 原 111.txt | 描述 | 交付形态 |
|------|-----------|------|---------|
| UI-01 | #1 | 全站毛玻璃圆角风格 | tokens.css + element-plus-override.css |
| UI-02 | #3 | 侧边导航栏 active 高亮美化 + 系统概览维度切换器美化 | SideMenu.vue 样式重构、`el-radio-group` 替代为自定义 tabs |
| UI-03 | #4 | 系统概览 5 张 KPI 卡右对齐 | DashboardView.vue 中 `el-row :gutter="16"` + `el-col :span="5"` 改为 `<div class="kpi-grid">` + `grid-template-columns: repeat(5, minmax(0, 1fr))` |
| UI-04 | #16 | 可视化接口菜单页视觉与整体统一 | 靠 tokens 全局覆盖自然跟上，本单元验收目视抽查 |
| UI-05 | #18a | 报文调试页视觉与整体统一 | 同上 |
| UI-06 | #1 派生 | 双主题（亮/暗）+ 4 种切换模式（手动 / 定时 / 跟随系统 / 日出日落） | tokens.css 双套 + `useTheme.js` composable + ThemeToggle.vue + ThemeDrawer.vue |

## 3. 视觉规范 · Design Tokens

两套 CSS 变量，通过 HTML 根节点的 `data-theme="light" | "dark"` 属性激活。所有变量已在 `mockups/v3-combined-switchable/index.html` 里试过效果，此处是"正式化"。

### 3.1 亮色 · Aurora

```css
[data-theme="light"] {
  /* 底色与背景 blob */
  --pg-bg-base: #F4F6FB;
  --pg-bg-blob-1: #C6D4FF;      /* 左上 蓝紫 */
  --pg-bg-blob-2: #E8CCFF;      /* 右下 紫粉 */
  --pg-bg-blob-3: #D5FFEA;      /* 中央 青绿 */
  --pg-stars-opacity: 0;

  /* 玻璃卡片 */
  --pg-glass-bg: rgba(255, 255, 255, 0.66);
  --pg-glass-bg-strong: rgba(255, 255, 255, 0.82);
  --pg-glass-border: rgba(255, 255, 255, 0.75);
  --pg-glass-blur: 24px;
  --pg-glass-shadow: 0 8px 32px rgba(60, 79, 148, 0.10), 0 2px 8px rgba(60, 79, 148, 0.06);
  --pg-glow: none;

  /* 主色与语义色 */
  --pg-primary: #5B7CFA;
  --pg-primary-soft: rgba(91, 124, 250, 0.12);
  --pg-primary-grad: linear-gradient(135deg, #5B7CFA 0%, #8B5CF6 100%);
  --pg-success: #14B98A;
  --pg-warning: #F59E0B;
  --pg-danger:  #F5556B;
  --pg-purple:  #8B5CF6;

  /* 文本层级 */
  --pg-text-primary:   #1F2937;
  --pg-text-white:     #1F2937;   /* 亮色下"白色文字位"退化为深灰，保证与 dark 同名不切代码 */
  --pg-text-regular:   #4B5563;
  --pg-text-secondary: #7B8291;
  --pg-text-placeholder: #A9B0BE;

  /* 分割线 · 交互底 */
  --pg-line: rgba(0, 0, 0, 0.05);
  --pg-line-strong: rgba(0, 0, 0, 0.08);
  --pg-hover-surface: rgba(91, 124, 250, 0.06);
  --pg-track-bg: #EEF1F8;
}
```

### 3.2 暗色 · Nebula

```css
[data-theme="dark"] {
  --pg-bg-base: #0B0F1C;
  --pg-bg-blob-1: #2B3EA1;
  --pg-bg-blob-2: #7A2CB0;
  --pg-bg-blob-3: #1EC7CB;
  --pg-stars-opacity: 1;

  --pg-glass-bg: rgba(20, 25, 45, 0.55);
  --pg-glass-bg-strong: rgba(20, 25, 45, 0.75);
  --pg-glass-border: rgba(255, 255, 255, 0.10);
  --pg-glass-blur: 28px;
  --pg-glass-shadow: 0 8px 32px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(255, 255, 255, 0.04) inset;
  --pg-glow: 0 0 20px rgba(109, 160, 255, 0.15);

  --pg-primary: #6DA0FF;
  --pg-primary-soft: rgba(109, 160, 255, 0.14);
  --pg-primary-grad: linear-gradient(135deg, #6DA0FF 0%, #A879FF 100%);
  --pg-success: #22D3AA;
  --pg-warning: #FBBF24;
  --pg-danger:  #FF6B85;
  --pg-purple:  #A879FF;

  --pg-text-primary:   #F1F5F9;
  --pg-text-white:     #FFFFFF;
  --pg-text-regular:   #CBD5E1;
  --pg-text-secondary: #94A3C4;
  --pg-text-placeholder: #64748B;

  --pg-line: rgba(255, 255, 255, 0.06);
  --pg-line-strong: rgba(255, 255, 255, 0.10);
  --pg-hover-surface: rgba(109, 160, 255, 0.10);
  --pg-track-bg: rgba(255, 255, 255, 0.06);
}
```

### 3.3 无主题相关（全局固定）

```css
:root {
  --pg-radius-lg: 20px;
  --pg-radius-md: 14px;
  --pg-radius-sm: 10px;
  --pg-sidebar-w: 232px;
  --pg-topbar-h: 60px;
}
```

### 3.4 Element Plus 变量覆盖

新增 `frontend/src/styles/element-plus-override.css`，把 `--pg-*` 桥接到 Element Plus 使用的 `--el-*`：

```css
:root {
  --el-color-primary: var(--pg-primary);
  --el-color-primary-light-3: color-mix(in srgb, var(--pg-primary) 60%, white);
  --el-color-primary-light-5: color-mix(in srgb, var(--pg-primary) 40%, white);
  --el-color-primary-light-7: var(--pg-primary-soft);
  --el-color-primary-light-9: color-mix(in srgb, var(--pg-primary) 10%, white);
  --el-color-success: var(--pg-success);
  --el-color-warning: var(--pg-warning);
  --el-color-danger:  var(--pg-danger);

  --el-bg-color: transparent;             /* 让页面背景 blob 透出 */
  --el-bg-color-overlay: var(--pg-glass-bg-strong);
  --el-text-color-primary: var(--pg-text-primary);
  --el-text-color-regular: var(--pg-text-regular);
  --el-text-color-secondary: var(--pg-text-secondary);
  --el-text-color-placeholder: var(--pg-text-placeholder);
  --el-border-color: var(--pg-glass-border);
  --el-border-color-light: var(--pg-line-strong);
  --el-border-color-lighter: var(--pg-line);

  --el-border-radius-base: var(--pg-radius-sm);
  --el-border-radius-small: 6px;
  --el-border-radius-round: 999px;

  --el-box-shadow: var(--pg-glass-shadow);
  --el-box-shadow-light: 0 4px 12px rgba(60, 79, 148, 0.06);
}

/* el-card / el-dialog / el-menu 等具体组件的毛玻璃增强，全部走 CSS 变量 */
.el-card, .el-dialog, .el-drawer, .el-message-box {
  background: var(--pg-glass-bg);
  backdrop-filter: blur(var(--pg-glass-blur));
  -webkit-backdrop-filter: blur(var(--pg-glass-blur));
  border: 1px solid var(--pg-glass-border);
  box-shadow: var(--pg-glass-shadow);
}

.el-input__wrapper, .el-select__wrapper, .el-textarea__inner {
  background: var(--pg-hover-surface);
  border-radius: var(--pg-radius-sm);
  box-shadow: none;
  border: 1px solid transparent;
}
.el-input__wrapper:hover, .el-input__wrapper.is-focus {
  border-color: var(--pg-primary);
}

.el-table {
  background: transparent;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--pg-hover-surface);
  --el-table-border-color: var(--pg-line);
  --el-table-row-hover-bg-color: var(--pg-hover-surface);
}

/* 平滑主题切换动画 */
html, body, .el-card, .el-dialog, .el-menu, .el-input__wrapper {
  transition: background 0.35s ease, color 0.35s ease, border-color 0.35s ease;
}
```

## 4. 主题切换机制

### 4.1 数据模型

```ts
// localStorage.themePref / sys_user.theme_pref JSON schema
{
  mode: 'manual' | 'schedule' | 'system' | 'sun',
  theme: 'light' | 'dark',              // 最近应用主题（用于冷启动、mode=manual 时）
  schedule: {
    lightAt: '07:00',                   // mode=schedule 时生效
    darkAt:  '19:00'
  },
  storage: 'local' | 'account'          // 抽屉里"应用范围"字段，local=只本设备，account=后端同步
}
```

### 4.2 4 种模式实现

| mode | 触发时机 | 实现细节 |
|------|---------|---------|
| `manual` | 用户点击 TopBar `☀/☾` 按钮 | 直接翻转 `theme` 字段并写 localStorage + 后端 |
| `schedule` | 挂载时 + 每 60s 一次 setInterval 检查 | 比较当前时间与 `schedule.lightAt / darkAt`，落到相应区间设 theme |
| `system` | 挂载时 + `window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', ...)` | 读浏览器偏好，OS 一切换立即生效 |
| `sun` | 挂载时 + 每 5min 一次 setInterval | 用内置北京经纬度（`lat: 39.9042, lng: 116.4074`）本地算法计算今日 sunrise/sunset，落到区间设 theme。**不依赖网络、不请求定位权限、不依赖第三方 API。**（sunrise 算法用小型 pure JS 实现 <100 行） |

### 4.3 `useTheme` composable

```js
// frontend/src/composables/useTheme.js
import { ref, watch, computed, onMounted, onUnmounted } from 'vue'
import { getUserThemePref, setUserThemePref } from '@/api/user'
import { useUserStore } from '@/store/user'

const DEFAULT_PREF = {
  mode: 'system', theme: 'light',
  schedule: { lightAt: '07:00', darkAt: '19:00' },
  storage: 'account'
}
const LS_KEY = 'themePref'

// 全局单例，避免多组件订阅重复计算
let _instance = null

export function useTheme() {
  if (_instance) return _instance

  const pref = ref(loadFromLocal())
  const currentTheme = ref(pref.value.theme)
  let scheduleTimer = null
  let sunTimer = null
  let mql = null

  function loadFromLocal() {
    try {
      const s = localStorage.getItem(LS_KEY)
      return s ? { ...DEFAULT_PREF, ...JSON.parse(s) } : { ...DEFAULT_PREF }
    } catch { return { ...DEFAULT_PREF } }
  }
  function saveToLocal() {
    localStorage.setItem(LS_KEY, JSON.stringify(pref.value))
  }
  async function loadFromServer() {
    const user = useUserStore()
    if (!user.isLoggedIn) return
    try {
      const remote = await getUserThemePref()
      if (remote) {
        pref.value = { ...DEFAULT_PREF, ...remote }
        saveToLocal()
        apply()
      }
    } catch (_) { /* 后端没同步就用 local */ }
  }
  async function saveToServer() {
    if (pref.value.storage !== 'account') return
    const user = useUserStore()
    if (!user.isLoggedIn) return
    try { await setUserThemePref(pref.value) } catch (_) { /* 忽略，本地已存 */ }
  }

  function apply() {
    document.documentElement.setAttribute('data-theme', currentTheme.value)
  }

  function setTheme(t) {
    currentTheme.value = t
    pref.value.theme = t
    saveToLocal(); saveToServer(); apply()
  }

  function setMode(mode, extra = {}) {
    pref.value = { ...pref.value, mode, ...extra }
    saveToLocal(); saveToServer()
    reschedule()
  }

  function reschedule() {
    clearInterval(scheduleTimer); scheduleTimer = null
    clearInterval(sunTimer); sunTimer = null
    if (mql) { mql.removeEventListener('change', onMediaChange); mql = null }

    if (pref.value.mode === 'schedule') {
      const check = () => setTheme(inRange(now(), pref.value.schedule.lightAt, pref.value.schedule.darkAt) ? 'light' : 'dark')
      check()
      scheduleTimer = setInterval(check, 60_000)
    } else if (pref.value.mode === 'system') {
      mql = window.matchMedia('(prefers-color-scheme: dark)')
      mql.addEventListener('change', onMediaChange)
      onMediaChange({ matches: mql.matches })
    } else if (pref.value.mode === 'sun') {
      const check = () => {
        const { sunrise, sunset } = beijingSunTimes(new Date())
        setTheme(inRange(now(), sunrise, sunset) ? 'light' : 'dark')
      }
      check()
      sunTimer = setInterval(check, 5 * 60_000)
    } else {
      // manual：不启定时，theme 保持当前
      apply()
    }
  }
  function onMediaChange(e) { setTheme(e.matches ? 'dark' : 'light') }

  onMounted(async () => {
    apply()
    await loadFromServer()   // 登录时后端 pref 覆盖 local
    reschedule()
  })
  onUnmounted(() => {
    clearInterval(scheduleTimer); clearInterval(sunTimer)
    if (mql) mql.removeEventListener('change', onMediaChange)
  })

  _instance = {
    pref, currentTheme,
    setTheme,
    toggle: () => setTheme(currentTheme.value === 'dark' ? 'light' : 'dark'),
    setMode,
    setSchedule: (schedule) => setMode('schedule', { schedule })
  }
  return _instance
}

// —— 工具函数 ——
function now() { const d = new Date(); return d.getHours() * 60 + d.getMinutes() }
function toMin(hhmm) { const [h, m] = hhmm.split(':').map(Number); return h * 60 + m }
function inRange(n, a, b) {
  const [x, y] = [toMin(a), toMin(b)]
  return x <= y ? n >= x && n < y : n >= x || n < y   // 跨零点也支持
}

// 北京日出日落计算（NOAA 简化算法，误差 <2 分钟，无需联网）
function beijingSunTimes(date) {
  const lat = 39.9042, lng = 116.4074, tzOffset = 8
  const rad = Math.PI / 180
  const doy = Math.floor((date - new Date(date.getFullYear(), 0, 0)) / 86400000)
  const gamma = 2 * Math.PI / 365 * (doy - 1)
  const eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma))
  const decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
             - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
             - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma)
  const ha = Math.acos(Math.cos(90.833 * rad) / (Math.cos(lat * rad) * Math.cos(decl))
             - Math.tan(lat * rad) * Math.tan(decl)) / rad
  const solarNoon = 720 - 4 * lng - eqTime + tzOffset * 60
  const sunriseMin = Math.floor(solarNoon - 4 * ha)
  const sunsetMin  = Math.floor(solarNoon + 4 * ha)
  const fmt = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
  return { sunrise: fmt(sunriseMin), sunset: fmt(sunsetMin) }
}
```

### 4.4 UI 组件

| 组件 | 位置 | 说明 |
|------|------|------|
| `ThemeToggle.vue` | `frontend/src/components/theme/ThemeToggle.vue` | TopBar 的 60×30 圆形滑块，`☀/☾` 图标+过渡动画。挂钩 `useTheme().toggle` |
| `ThemeDrawer.vue` | `frontend/src/components/theme/ThemeDrawer.vue` | 380px 右侧抽屉，4 种模式 radio 选择器 + 定时时间输入 + 应用范围（本设备 / 账号）。挂钩 `useTheme().setMode / setSchedule` |
| TopBar `⚙` icon | `frontend/src/components/layout/TopBar.vue` | 点击打开 ThemeDrawer |

## 5. 存储 · 后端 API

### 5.1 数据库变更

新建幂等迁移脚本 `backend/src/main/resources/db/migration-sys-user-theme.sql`：

```sql
-- 幂等：只在字段不存在时添加
DROP PROCEDURE IF EXISTS pg_add_theme_pref;
DELIMITER //
CREATE PROCEDURE pg_add_theme_pref()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'theme_pref'
    ) THEN
        ALTER TABLE sys_user
        ADD COLUMN theme_pref TEXT DEFAULT NULL COMMENT '主题偏好 JSON';
    END IF;
END//
DELIMITER ;
CALL pg_add_theme_pref();
DROP PROCEDURE pg_add_theme_pref;
```

同步在 `init.sql` 的 `sys_user` 表 DDL 里添加此列（新库直接生效）。

### 5.2 API 端点

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET  | `/api/user/theme-pref` | 读取当前登录用户的主题偏好，无则返回 `null` | 登录即可 |
| PUT  | `/api/user/theme-pref` | 保存当前登录用户的主题偏好（body 为 JSON） | 登录即可 |

后端类：`SysUserThemePrefController.java`（单独 Controller 避免污染 SysUserController）；`SysUserService` 新增 `getThemePref(userId)` / `setThemePref(userId, json)` 两个方法。

**不走 `@AuditLog`**（用户偏好属于个人配置，无审计价值），**不走 `@SysLogRecord`**（同理）。

### 5.3 前端 API

`frontend/src/api/user.js` 追加：

```js
export const getUserThemePref = () => request.get('/user/theme-pref')
export const setUserThemePref = (pref) => request.put('/user/theme-pref', pref)
```

## 6. 前端文件变更清单

| 文件 | 变更 | 说明 |
|------|------|------|
| `frontend/src/styles/tokens.css` | 新增 | 亮/暗两套 CSS 变量（本文档 § 3.1 § 3.2） |
| `frontend/src/styles/element-plus-override.css` | 新增 | Element Plus 变量桥接与组件深度覆盖（§ 3.4） |
| `frontend/src/main.js` | 修改 | 顶部 `import './styles/tokens.css'` + `import './styles/element-plus-override.css'` |
| `frontend/src/composables/useTheme.js` | 新增 | 核心状态机（§ 4.3） |
| `frontend/src/components/theme/ThemeToggle.vue` | 新增 | TopBar 圆形切换按钮 |
| `frontend/src/components/theme/ThemeDrawer.vue` | 新增 | 主题设置抽屉 |
| `frontend/src/components/layout/TopBar.vue` | 修改 | 引入 ThemeToggle 和齿轮打开 ThemeDrawer |
| `frontend/src/components/layout/SideMenu.vue` | 修改（美化，不改结构） | active 高亮改光带渐变；分组标签改为小字 uppercase；hover 走 `var(--pg-hover-surface)` |
| `frontend/src/views/DashboardView.vue` | 修改（UI-03） | `el-row/el-col :span="5"` KPI 卡片改用 `<div class="kpi-grid">` + `display:grid; grid-template-columns: repeat(5, minmax(0,1fr))` |
| `frontend/src/App.vue` | 修改 | 引入全局 backdrop blob（`::before/::after/.bg-blob`）+ 星空点点（仅暗色可见） |
| `frontend/src/api/user.js` | 修改 | 追加 `getUserThemePref`/`setUserThemePref` |

**特别不改动的**：22 个业务页面的 template 结构不动，只靠 CSS 变量继承新风格。若某页样式硬编码了颜色（如 `color: #333`），本单元不改，作为 UI-04 / UI-05 抽查 5 页时发现再局部替换。

## 7. 后端文件变更清单

| 文件 | 变更 |
|------|------|
| `backend/src/main/resources/db/init.sql` | `sys_user` 加列 `theme_pref TEXT COMMENT '主题偏好 JSON'` |
| `backend/src/main/resources/db/migration-sys-user-theme.sql` | 新增，幂等迁移脚本 |
| `backend/src/main/java/com/powergateway/model/SysUser.java` | 加字段 `private String themePref;` |
| `backend/src/main/java/com/powergateway/controller/SysUserThemePrefController.java` | 新增，2 个端点 |
| `backend/src/main/java/com/powergateway/service/SysUserService.java` | 加 2 个方法 |
| `backend/src/test/java/com/powergateway/UXAThemePrefTest.java` | 新增测试类 |

## 8. 测试用例

### 8.1 后端（`UXAThemePrefTest`，`@ActiveProfiles("test")`）

- `getThemePref_未设置_返回null` — 冷启动新用户返回 null
- `setThemePref_合法JSON_持久化成功` — PUT 后 GET 拿回一致 payload
- `setThemePref_非法JSON_返回422` — 非法 body 校验
- `getThemePref_未登录_返回401`
- `themePref_跨用户不串数据` — 用户 A 设的偏好不会污染用户 B

### 8.2 前端 composable（`useTheme.spec.js`，vitest）

- `mode=manual toggle 后 currentTheme 翻转`
- `mode=schedule 09:00 时段内 currentTheme 为 light`
- `mode=schedule 20:00 时段外 currentTheme 为 dark`
- `mode=schedule 跨零点区间`（`darkAt: '01:00', lightAt: '07:00'`）行为正确
- `mode=system 模拟 matchMedia 变化后 currentTheme 联动`
- `mode=sun 传入固定日期 currentTheme 落到日出前/后区间正确`
- `setMode('account') 后调用了 setUserThemePref API`
- `saveToLocal 与 loadFromLocal 序列化对称`

### 8.3 组件（`ThemeToggle.spec.js` / `ThemeDrawer.spec.js`）

- ThemeToggle 点击调用 `useTheme().toggle`
- ThemeDrawer 打开时展示 4 个 mode-item
- 选中 schedule 模式后时间输入框可编辑
- 保存按钮触发 API 调用

### 8.4 视觉冒烟（人工，非自动化）

用主题切换 + 抽样 5 个页面（Dashboard / InterfaceList / FieldMapping / Debug / SysConfig）目视验证：
- 亮/暗切换 <500ms，无闪烁
- 卡片圆角、模糊、阴影一致
- 表格 header 背景在暗色下不刺眼
- 输入框 focus 边框颜色 = `--pg-primary`
- Dashboard 5 张 KPI 卡最右缘与下方 chart 卡最右缘对齐（UI-03 验收）

## 9. 验收标准

| # | 场景 | 通过条件 |
|---|------|---------|
| 1 | 冷启动（清空 localStorage 且用户未登录） | 默认 `mode=system`, `theme=light 或 dark`（跟随 OS） |
| 2 | 登录 → 若后端 pref 存在 → 应用 pref | localStorage 被后端 pref 覆盖 |
| 3 | 未登录切换主题 | 只写 localStorage，不 API 报错 |
| 4 | 登录切换主题 + `storage=account` | localStorage 与后端同步 |
| 5 | 切 mode=manual → 定时器停止 | 无 setInterval 泄漏（DevTools Performance 检查） |
| 6 | 切 mode=schedule 07:00-19:00 | 8:00 是亮色、20:00 是暗色 |
| 7 | 切 mode=system 且 OS 为暗 | 立即暗色；OS 切亮时立即亮色 |
| 8 | 切 mode=sun 冬至日 | sunrise ≈ 07:33、sunset ≈ 16:53（北京冬至） |
| 9 | Dashboard 5 张 KPI 卡右对齐（UI-03） | 5 卡最右像素 = 下方 chart-card 最右像素 |
| 10 | SideMenu active 光带（UI-02） | 视觉出现 3px 渐变光条 |
| 11 | 22 个业务页面（UI-01/04/05） | 抽查 5 页无色差污染，无未继承新 token 的硬编码色 |
| 12 | 主题切换动画流畅（UI-06） | 亮暗切换 backdrop-filter 无闪烁，`transition` 0.35s 过渡到位 |
| 13 | 定位默认北京（UI-06） | 抽屉 sun 模式项展示"当前定位：北京"且不可修改 |
| 14 | 无网络时 sun 模式仍工作（UI-06） | 断网测试，sun 模式仍正确切换（本地算法） |

## 10. 实施顺序建议

1. **DB 迁移 + 后端 2 端点**（TDD，红绿蓝）—— 半小时
2. **tokens.css + element-plus-override.css** —— 目视对比 mockup
3. **App.vue 引入背景 blob** —— 目视验证
4. **useTheme.js composable** + 单元测试 —— 4 种模式逐个 TDD
5. **ThemeToggle.vue + ThemeDrawer.vue** —— 挂到 TopBar
6. **SideMenu.vue 美化** —— 光带高亮 + 分组标签
7. **DashboardView.vue UI-03 修复** —— KPI 卡 grid
8. **UI-04/UI-05 抽查 5 页 + 硬编码色替换**（如果发现）
9. **端到端冒烟 + 移交下一波（UX-B/C/E）**

## 11. 与其它 UX 组的耦合

- **UX-B（信息架构）会改 TopBar.vue**：本单元在 TopBar 加 ThemeToggle + 齿轮时，先把插入位置留 `<slot name="topbar-actions">` 或明确的注释块，避免 UX-B 修 TopBar 时误删本单元代码。合并策略：本单元先合入 main，UX-B 在 main 之上开发。
- **UX-C/D/E 新增页面直接继承 token**，本单元不干扰。
- **暗色下 ECharts 颜色需二次调优**：Dashboard 里的折线图/柱图/饼图配色在暗色下若不够鲜亮，本单元预留改动位（`v-chart :option` 的 series color 走 `var(--pg-primary)` 等 CSS 变量而非硬编码）。若不完美，作为 QA 项在 UI-06 验收阶段列 5-10 分钟微调。

## 12. 迁移与回滚

- **迁移**：`migration-sys-user-theme.sql` 幂等，可反复执行；`init.sql` 已含新字段（新库自动）。
- **回滚**：若本单元合并后前端出现严重视觉退化，回滚只需 `git revert` 本次 commit + 保留 `theme_pref` 列（列存在不影响业务）。
- **老用户 theme_pref = NULL**：`useTheme` 默认 `mode=system, theme=light`，与冷启动等价。

## 13. 未纳入

- 抽屉中"应用范围"允许"跟随企业默认"（读 sys_config）—— 需要多角色/多租户，YAGNI，不做。
- 手机端响应式布局适配 —— 本项目不面向移动端，不做。
- 明暗切换过程中 backdrop-filter 性能优化（低端机可能卡） —— 若客户反馈再启用 `will-change: backdrop-filter`。
- 图表主题跟随（ECharts 内置 theme 切换）—— 若冒烟发现暗色下图表刺眼，作为快速跟进项而非本单元交付项。
