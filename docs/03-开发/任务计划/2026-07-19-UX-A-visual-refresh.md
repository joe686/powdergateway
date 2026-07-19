# UX-A · 全站视觉重塑 + 双主题切换 · 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全站切换到毛玻璃圆角风格，落地 `mockups/v3-combined-switchable/index.html` 定稿的 V3 双主题（亮色 Aurora + 暗色 Nebula），提供 4 种主题切换模式（手动 / 定时 / 跟随系统 / 日出日落），偏好双存储（localStorage + `sys_user.theme_pref`）跨设备同步。

**Architecture:** 视觉层不改任何业务组件，只覆盖 Element Plus CSS 变量层（`--el-*` 桥接到 `--pg-*` design tokens），22 个业务页面自动继承。主题状态用 `useTheme` composable 单例管理，`data-theme` 属性驱动 CSS 切换，4 种模式在 composable 内部通过 setInterval / matchMedia / 本地 NOAA 算法 实现。存储采用"localStorage 优先 + 登录后从后端同步"。

**Tech Stack:** Vue 3, Element Plus, CSS Custom Properties, Spring Boot 2.7, MyBatis-Plus, JUnit 4

## Global Constraints

- 所有对话/注释/文档中文
- 前端严格用 `src/api/request.js`，不能直接用原生 axios
- vue-draggable-next 只用 default slot + v-for（本单元不动 draggable，但避免误改）
- 后端 `sys_user` 属**配置库**，无 `@DS` 注解
- 表结构 utf8mb4 + 中文 COMMENT
- 后端测试类必加 `@ActiveProfiles("test")`
- TDD Red-Green-Refactor
- 前端保留渐进式主题切换动画 0.35s，避免 backdrop-filter 闪烁
- **不改 `TopBar.vue` template 已有结构**，只在指定 slot 位置插入 ThemeToggle 与齿轮（为 UX-B 让路，防合并冲突）
- 完成后追加 `CHG-016` 到 `docs/03-开发/变更记录.md`，问题清单 UI-01 ~ UI-06 从"待解决"搬到"已解决"

## 参考

- **对应 spec**：`docs/02-设计/详细设计/2026-07-19-UX-A-visual-refresh-design.md`（本计划所有决策依据）
- **视觉参考**：`mockups/v3-combined-switchable/index.html`（用户 2026-07-19 确认）
- **总览**：`docs/02-设计/详细设计/2026-07-19-visual-refresh-and-fixes-overview.md`

---

### Task 1: 后端 —— `sys_user.theme_pref` 列迁移

**Files:**
- Create: `backend/src/main/resources/db/migration-sys-user-theme.sql`
- Modify: `backend/src/main/resources/db/init.sql`（`sys_user` DDL 加列，注释）

**Interfaces:**
- Consumes: 无
- Produces: `sys_user` 表拥有 `theme_pref TEXT DEFAULT NULL COMMENT '主题偏好 JSON'` 列

- [ ] **Step 1: 写幂等迁移脚本**

创建 `backend/src/main/resources/db/migration-sys-user-theme.sql`：

```sql
-- UX-A: sys_user.theme_pref 幂等迁移
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

- [ ] **Step 2: 手动执行迁移到本地配置库**

Run: 用 MySQL 客户端连 `powergateway_config` 后执行 `SOURCE backend/src/main/resources/db/migration-sys-user-theme.sql;`
Expected: 无错误。`DESC sys_user;` 显示 `theme_pref` 列存在。

- [ ] **Step 3: 二次执行验证幂等**

Run: 再次 `SOURCE backend/src/main/resources/db/migration-sys-user-theme.sql;`
Expected: 无错误、`theme_pref` 列不变（Procedure 内 IF NOT EXISTS 跳过 ALTER）

- [ ] **Step 4: 同步更新 `init.sql`**

在 `init.sql` 的 `sys_user` DDL 中，在末尾 `PRIMARY KEY (id)` 前添加：

```sql
    theme_pref TEXT DEFAULT NULL COMMENT '主题偏好 JSON',
```

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/resources/db/migration-sys-user-theme.sql backend/src/main/resources/db/init.sql
git commit -m "feat(UX-A): sys_user 加 theme_pref 列并附幂等迁移脚本"
```

---

### Task 2: 后端 —— `SysUser` 实体加字段

**Files:**
- Modify: `backend/src/main/java/com/powergateway/model/SysUser.java`

**Interfaces:**
- Consumes: `sys_user.theme_pref` 列（Task 1）
- Produces: `SysUser.themePref` 字段，MyBatis-Plus 自动映射

- [ ] **Step 1: 加字段与 getter/setter**

在 `SysUser.java` 类体中添加：

```java
/** 主题偏好 JSON（UX-A） */
private String themePref;
```

`@Data` 注解会自动生成 getter/setter；如未用 Lombok 则手写。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl backend`（或直接在 backend 目录 `mvn compile`）
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/powergateway/model/SysUser.java
git commit -m "feat(UX-A): SysUser 加 themePref 字段"
```

---

### Task 3: 后端 —— Service 层增 2 方法 + 测试

**Files:**
- Modify: `backend/src/main/java/com/powergateway/service/SysUserService.java`
- Create: `backend/src/test/java/com/powergateway/UXAThemePrefServiceTest.java`

**Interfaces:**
- Consumes: `SysUser.themePref`（Task 2）
- Produces:
  - `String SysUserService.getThemePref(Long userId)` —— 返回 JSON 字符串或 null
  - `void SysUserService.setThemePref(Long userId, String pref)` —— 存 JSON 字符串

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/powergateway/UXAThemePrefServiceTest.java`：

```java
package com.powergateway;

import com.powergateway.service.SysUserService;
import com.powergateway.model.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UXAThemePrefServiceTest {

    @Autowired
    private SysUserService sysUserService;

    @Test
    void getThemePref_未设置_返回null() {
        SysUser u = new SysUser();
        u.setUsername("t1");
        u.setPassword("x");
        sysUserService.save(u);
        assertThat(sysUserService.getThemePref(u.getId())).isNull();
    }

    @Test
    void setThemePref_合法JSON_持久化并可读回() {
        SysUser u = new SysUser();
        u.setUsername("t2");
        u.setPassword("x");
        sysUserService.save(u);
        String json = "{\"mode\":\"system\",\"theme\":\"dark\"}";
        sysUserService.setThemePref(u.getId(), json);
        assertThat(sysUserService.getThemePref(u.getId())).isEqualTo(json);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=UXAThemePrefServiceTest -pl backend`
Expected: 编译报错（未定义 `getThemePref` / `setThemePref` 方法）

- [ ] **Step 3: 实现 Service 方法**

在 `SysUserService.java` 中添加：

```java
public String getThemePref(Long userId) {
    SysUser u = getById(userId);
    return u == null ? null : u.getThemePref();
}

public void setThemePref(Long userId, String pref) {
    SysUser u = new SysUser();
    u.setId(userId);
    u.setThemePref(pref);
    updateById(u);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=UXAThemePrefServiceTest -pl backend`
Expected: 2 tests, 0 failures

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/service/SysUserService.java backend/src/test/java/com/powergateway/UXAThemePrefServiceTest.java
git commit -m "feat(UX-A): SysUserService.getThemePref/setThemePref + 测试"
```

---

### Task 4: 后端 —— Controller 2 端点 + 集成测试

**Files:**
- Create: `backend/src/main/java/com/powergateway/controller/SysUserThemePrefController.java`
- Create: `backend/src/test/java/com/powergateway/UXAThemePrefControllerTest.java`

**Interfaces:**
- Consumes: `SysUserService.getThemePref / setThemePref`（Task 3）
- Produces:
  - `GET  /api/user/theme-pref` → `Result<String>` 返回 JSON 字符串或 null
  - `PUT  /api/user/theme-pref` body: JSON 字符串 → `Result<Void>`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/powergateway/UXAThemePrefControllerTest.java`：

```java
package com.powergateway;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UXAThemePrefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getThemePref_未登录_返回401() throws Exception {
        mockMvc.perform(get("/api/user/theme-pref"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void getThemePref_已登录_未设置_返回null() throws Exception {
        StpUtil.login(1L);          // admin 用户
        mockMvc.perform(get("/api/user/theme-pref")
                       .header("satoken", StpUtil.getTokenValue()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void putThemePref_合法JSON_写入成功() throws Exception {
        StpUtil.login(1L);
        String body = "{\"mode\":\"schedule\",\"schedule\":{\"lightAt\":\"07:00\",\"darkAt\":\"19:00\"}}";
        mockMvc.perform(put("/api/user/theme-pref")
                       .header("satoken", StpUtil.getTokenValue())
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(body))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/user/theme-pref")
                       .header("satoken", StpUtil.getTokenValue()))
               .andExpect(jsonPath("$.data").value(body));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=UXAThemePrefControllerTest -pl backend`
Expected: 3 tests fail with 404 (Controller 未实现)

- [ ] **Step 3: 实现 Controller**

创建 `SysUserThemePrefController.java`：

```java
package com.powergateway.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.common.Result;
import com.powergateway.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class SysUserThemePrefController {

    @Autowired
    private SysUserService sysUserService;

    @GetMapping("/theme-pref")
    @SaCheckLogin
    public Result<String> get() {
        Long uid = Long.valueOf(StpUtil.getLoginId().toString());
        return Result.success(sysUserService.getThemePref(uid));
    }

    @PutMapping("/theme-pref")
    @SaCheckLogin
    public Result<Void> put(@RequestBody String body) {
        Long uid = Long.valueOf(StpUtil.getLoginId().toString());
        sysUserService.setThemePref(uid, body);
        return Result.success();
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -Dtest=UXAThemePrefControllerTest -pl backend`
Expected: 3 tests, 0 failures

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/controller/SysUserThemePrefController.java backend/src/test/java/com/powergateway/UXAThemePrefControllerTest.java
git commit -m "feat(UX-A): 主题偏好 GET/PUT 接口 + MockMvc 测试"
```

---

### Task 5: 前端 —— `api/user.js` 追加 2 方法

**Files:**
- Modify: `frontend/src/api/user.js`

**Interfaces:**
- Consumes: 后端 `/api/user/theme-pref`（Task 4）
- Produces:
  - `getUserThemePref() : Promise<string | null>`
  - `setUserThemePref(pref: object) : Promise<void>` —— 内部 `JSON.stringify(pref)` 后 PUT 原文

- [ ] **Step 1: 追加代码**

在 `frontend/src/api/user.js` 末尾添加：

```js
// UX-A 主题偏好
export const getUserThemePref = async () => {
  const raw = await request.get('/user/theme-pref')
  return raw ? JSON.parse(raw) : null
}
export const setUserThemePref = (pref) =>
  request.put('/user/theme-pref', JSON.stringify(pref), {
    headers: { 'Content-Type': 'application/json' }
  })
```

- [ ] **Step 2: 手动核验**

Run: 启动 backend + frontend，用浏览器 DevTools Console 执行：
```js
const { getUserThemePref, setUserThemePref } = await import('/src/api/user.js')
await setUserThemePref({ mode: 'system', theme: 'light' })
await getUserThemePref()   // 应返回 {mode:'system', theme:'light'}
```
Expected: 两次调用 200 OK，返回值正确

- [ ] **Step 3: 提交**

```bash
git add frontend/src/api/user.js
git commit -m "feat(UX-A): api/user.js 追加 getUserThemePref/setUserThemePref"
```

---

### Task 6: 前端 —— `tokens.css` 双主题 CSS 变量

**Files:**
- Create: `frontend/src/styles/tokens.css`

**Interfaces:**
- Consumes: 无
- Produces: 全局 CSS 变量 `--pg-*`，通过 `html[data-theme="light|dark"]` 激活

- [ ] **Step 1: 写入 tokens.css**

参照 spec § 3.1 § 3.2 § 3.3 的完整 CSS，创建 `frontend/src/styles/tokens.css`：

```css
/* UX-A · Design Tokens · V3 双主题（Aurora 亮 + Nebula 暗） */

[data-theme="light"] {
  --pg-bg-base: #F4F6FB;
  --pg-bg-blob-1: #C6D4FF;
  --pg-bg-blob-2: #E8CCFF;
  --pg-bg-blob-3: #D5FFEA;
  --pg-stars-opacity: 0;

  --pg-glass-bg: rgba(255, 255, 255, 0.66);
  --pg-glass-bg-strong: rgba(255, 255, 255, 0.82);
  --pg-glass-border: rgba(255, 255, 255, 0.75);
  --pg-glass-blur: 24px;
  --pg-glass-shadow: 0 8px 32px rgba(60, 79, 148, 0.10), 0 2px 8px rgba(60, 79, 148, 0.06);
  --pg-glow: none;

  --pg-primary: #5B7CFA;
  --pg-primary-soft: rgba(91, 124, 250, 0.12);
  --pg-primary-grad: linear-gradient(135deg, #5B7CFA 0%, #8B5CF6 100%);
  --pg-success: #14B98A;
  --pg-warning: #F59E0B;
  --pg-danger:  #F5556B;
  --pg-purple:  #8B5CF6;

  --pg-text-primary:   #1F2937;
  --pg-text-white:     #1F2937;
  --pg-text-regular:   #4B5563;
  --pg-text-secondary: #7B8291;
  --pg-text-placeholder: #A9B0BE;

  --pg-line: rgba(0, 0, 0, 0.05);
  --pg-line-strong: rgba(0, 0, 0, 0.08);
  --pg-hover-surface: rgba(91, 124, 250, 0.06);
  --pg-track-bg: #EEF1F8;

  --pg-blob-blur: 80px;
  --pg-blob-opacity: 0.75;
}

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

  --pg-blob-blur: 120px;
  --pg-blob-opacity: 0.5;
}

:root {
  --pg-radius-lg: 20px;
  --pg-radius-md: 14px;
  --pg-radius-sm: 10px;
  --pg-sidebar-w: 232px;
  --pg-topbar-h: 60px;
}

html, body {
  background: var(--pg-bg-base);
  color: var(--pg-text-primary);
  transition: background 0.35s ease, color 0.35s ease;
}
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/styles/tokens.css
git commit -m "feat(UX-A): 新增 tokens.css 双主题 CSS 变量"
```

---

### Task 7: 前端 —— `element-plus-override.css` Element 变量桥接

**Files:**
- Create: `frontend/src/styles/element-plus-override.css`

**Interfaces:**
- Consumes: `--pg-*` 变量（Task 6）
- Produces: 覆盖 `--el-*` 变量 + `.el-card / .el-dialog / .el-input / .el-table` 深度样式

- [ ] **Step 1: 写入 override.css**

参照 spec § 3.4 完整代码，创建 `frontend/src/styles/element-plus-override.css`：

```css
/* UX-A · Element Plus 变量桥接 + 组件深度覆盖 */

:root {
  --el-color-primary: var(--pg-primary);
  --el-color-primary-light-3: color-mix(in srgb, var(--pg-primary) 60%, white);
  --el-color-primary-light-5: color-mix(in srgb, var(--pg-primary) 40%, white);
  --el-color-primary-light-7: var(--pg-primary-soft);
  --el-color-primary-light-9: color-mix(in srgb, var(--pg-primary) 10%, white);
  --el-color-success: var(--pg-success);
  --el-color-warning: var(--pg-warning);
  --el-color-danger:  var(--pg-danger);

  --el-bg-color: transparent;
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
  transition: border-color 0.2s;
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

/* 平滑过渡 */
.el-card, .el-dialog, .el-menu, .el-input__wrapper, .el-button {
  transition: background 0.35s ease, color 0.35s ease, border-color 0.35s ease;
}
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/styles/element-plus-override.css
git commit -m "feat(UX-A): element-plus-override.css 桥接 --pg-* 到 --el-*"
```

---

### Task 8: 前端 —— `main.js` 引入两个 CSS

**Files:**
- Modify: `frontend/src/main.js`

**Interfaces:**
- Consumes: `tokens.css`（Task 6）、`element-plus-override.css`（Task 7）
- Produces: 应用启动即加载新样式

- [ ] **Step 1: 加 import**

在 `frontend/src/main.js` 中的 Element Plus 样式 import 之后（`import 'element-plus/dist/index.css'` 那一行）追加：

```js
import '@/styles/tokens.css'
import '@/styles/element-plus-override.css'
```

**顺序重要**：`tokens.css` 必须在 `element-plus-override.css` 之前（override 依赖 `--pg-*`）。两者都要在 Element Plus 默认样式之后（覆盖顺序）。

- [ ] **Step 2: 启动前端验证**

Run: 在 `frontend` 目录 `npm run dev`
Expected: 页面能正常渲染，浏览器 DevTools Elements 面板 `<html>` 元素默认无 `data-theme` 属性（下一 Task 加），但 `--el-color-primary` 应等于 `#5B7CFA`（Aurora 主色）—— 因为 Task 6 里 `[data-theme="light"]` 未激活时使用 fallback。

**若发现 `--el-color-primary` 未生效**：手动在 DevTools Console 执行 `document.documentElement.setAttribute('data-theme', 'light')`，页面主色应变为 `#5B7CFA`（Element Plus 默认蓝）。

- [ ] **Step 3: 提交**

```bash
git add frontend/src/main.js
git commit -m "feat(UX-A): main.js 引入 tokens.css 与 element-plus-override.css"
```

---

### Task 9: 前端 —— `App.vue` 背景渐变 blob + 星空

**Files:**
- Modify: `frontend/src/App.vue`

**Interfaces:**
- Consumes: `--pg-bg-*` 变量（Task 6）
- Produces: 全屏背景装饰层（3 个 blob + 星空点），主题切换时自动跟随

- [ ] **Step 1: 加背景元素与样式**

在 `App.vue` 的 `<template>` 顶部（`<router-view>` 之前）添加：

```vue
<div class="pg-bg-stars" aria-hidden="true"></div>
<div class="pg-bg-blob" aria-hidden="true"></div>
```

在 `<style>`（若无则新增 `<style>` 无 scoped 修饰符）添加：

```css
body::before, body::after, .pg-bg-blob {
  content: "";
  position: fixed;
  border-radius: 50%;
  filter: blur(var(--pg-blob-blur));
  z-index: 0;
  opacity: var(--pg-blob-opacity);
  pointer-events: none;
  transition: background 0.5s ease, opacity 0.5s ease;
}
body::before {
  width: 620px; height: 620px;
  background: var(--pg-bg-blob-1);
  top: -140px; left: -100px;
}
body::after {
  width: 520px; height: 520px;
  background: var(--pg-bg-blob-2);
  bottom: -160px; right: -80px;
}
.pg-bg-blob {
  width: 380px; height: 380px;
  background: var(--pg-bg-blob-3);
  top: 40%; left: 55%;
}
.pg-bg-stars {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  opacity: var(--pg-stars-opacity);
  transition: opacity 0.5s ease;
  background-image:
    radial-gradient(1px 1px at 15% 20%, rgba(255,255,255,0.5) 50%, transparent),
    radial-gradient(1px 1px at 40% 65%, rgba(255,255,255,0.4) 50%, transparent),
    radial-gradient(1px 1px at 70% 30%, rgba(255,255,255,0.5) 50%, transparent),
    radial-gradient(1px 1px at 85% 80%, rgba(255,255,255,0.3) 50%, transparent),
    radial-gradient(1px 1px at 25% 85%, rgba(255,255,255,0.4) 50%, transparent);
}
/* 保证主内容盖过背景 */
#app > *:not(.pg-bg-stars):not(.pg-bg-blob) {
  position: relative;
  z-index: 1;
}
```

- [ ] **Step 2: 浏览器目视验证**

在浏览器 DevTools Console 执行 `document.documentElement.setAttribute('data-theme', 'light')` 与 `'dark'` 各切一次
Expected:
- 亮色：白底 + 淡紫蓝渐变 blob 可见
- 暗色：深空背景 + 星空点点 + 强色 blob

- [ ] **Step 3: 提交**

```bash
git add frontend/src/App.vue
git commit -m "feat(UX-A): App.vue 全屏渐变 blob 背景 + 暗色星空"
```

---

### Task 10: 前端 —— `useTheme` composable

**Files:**
- Create: `frontend/src/composables/useTheme.js`

**Interfaces:**
- Consumes: `getUserThemePref / setUserThemePref`（Task 5）
- Produces:
  - `useTheme() → { pref: Ref, currentTheme: Ref, setTheme(t), toggle(), setMode(mode, extra), setSchedule(sch) }`
  - 单例，多组件调用返回同一状态
  - 挂载时调用 `apply()` 设 `html[data-theme]`，登录时从后端拉一次覆盖 localStorage

- [ ] **Step 1: 写文件**

创建 `frontend/src/composables/useTheme.js`，完整代码参见 spec § 4.3。核心结构：

```js
import { ref, onMounted, onUnmounted } from 'vue'
import { getUserThemePref, setUserThemePref } from '@/api/user'
import { useUserStore } from '@/store/user'

const DEFAULT_PREF = {
  mode: 'system', theme: 'light',
  schedule: { lightAt: '07:00', darkAt: '19:00' },
  storage: 'account'
}
const LS_KEY = 'themePref'
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
  function saveToLocal() { localStorage.setItem(LS_KEY, JSON.stringify(pref.value)) }

  async function loadFromServer() {
    const user = useUserStore()
    if (!user.isLoggedIn) return
    try {
      const remote = await getUserThemePref()
      if (remote) { pref.value = { ...DEFAULT_PREF, ...remote }; saveToLocal(); apply() }
    } catch {}
  }
  async function saveToServer() {
    if (pref.value.storage !== 'account') return
    const user = useUserStore()
    if (!user.isLoggedIn) return
    try { await setUserThemePref(pref.value) } catch {}
  }

  function apply() { document.documentElement.setAttribute('data-theme', currentTheme.value) }

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
      check(); scheduleTimer = setInterval(check, 60_000)
    } else if (pref.value.mode === 'system') {
      mql = window.matchMedia('(prefers-color-scheme: dark)')
      mql.addEventListener('change', onMediaChange)
      onMediaChange({ matches: mql.matches })
    } else if (pref.value.mode === 'sun') {
      const check = () => {
        const { sunrise, sunset } = beijingSunTimes(new Date())
        setTheme(inRange(now(), sunrise, sunset) ? 'light' : 'dark')
      }
      check(); sunTimer = setInterval(check, 5 * 60_000)
    } else { apply() }
  }
  function onMediaChange(e) { setTheme(e.matches ? 'dark' : 'light') }

  onMounted(async () => { apply(); await loadFromServer(); reschedule() })
  onUnmounted(() => {
    clearInterval(scheduleTimer); clearInterval(sunTimer)
    if (mql) mql.removeEventListener('change', onMediaChange)
  })

  _instance = {
    pref, currentTheme, setTheme,
    toggle: () => setTheme(currentTheme.value === 'dark' ? 'light' : 'dark'),
    setMode,
    setSchedule: (schedule) => setMode('schedule', { schedule })
  }
  return _instance
}

// 工具函数
function now() { const d = new Date(); return d.getHours() * 60 + d.getMinutes() }
function toMin(s) { const [h, m] = s.split(':').map(Number); return h * 60 + m }
function inRange(n, a, b) {
  const [x, y] = [toMin(a), toMin(b)]
  return x <= y ? n >= x && n < y : n >= x || n < y
}

// 北京日出日落（NOAA 简化算法，误差 <2 分钟，无需联网）
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

- [ ] **Step 2: 浏览器手工验证 4 模式**

启动前端 → DevTools Console：

```js
const { useTheme } = await import('/src/composables/useTheme.js')
const t = useTheme()
t.setMode('manual'); t.setTheme('dark');    // 页面立即暗
t.toggle();                                 // 立即亮
t.setSchedule({ lightAt: '00:00', darkAt: '23:59' });  // schedule 模式，几乎全天亮
t.setMode('system');                        // 跟随 OS
console.log(t.currentTheme.value);          // 打印当前主题
t.setMode('sun'); console.log(t.pref.value); // sun 模式，检查 pref
```

Expected: 每步页面切换正确、`localStorage.themePref` 有对应 JSON。

- [ ] **Step 3: 手工验证日出算法**

Console 执行（不导出，需临时 patch）：

```js
// 直接用一个已知日期测试（2026 冬至）
const doy = Math.floor((new Date(2026, 11, 22) - new Date(2026, 0, 0)) / 86400000)
console.log('冬至 doy =', doy)  // 应为约 356
// 冬至北京 sunrise≈07:33, sunset≈16:53（可与网上查询对比）
```

- [ ] **Step 4: 提交**

```bash
git add frontend/src/composables/useTheme.js
git commit -m "feat(UX-A): useTheme composable 支持 4 种主题模式"
```

---

### Task 11: 前端 —— `ThemeToggle.vue` 圆形滑块

**Files:**
- Create: `frontend/src/components/theme/ThemeToggle.vue`

**Interfaces:**
- Consumes: `useTheme().toggle / currentTheme`（Task 10）
- Produces: 60×30 单文件组件 `<ThemeToggle />`

- [ ] **Step 1: 写组件**

```vue
<template>
  <div class="pg-theme-toggle" :title="'切换到' + (currentTheme === 'dark' ? '亮色' : '暗色')" @click="toggle">
    <div class="pg-theme-thumb">
      <span v-if="currentTheme === 'dark'">☾</span>
      <span v-else>☀</span>
    </div>
  </div>
</template>

<script setup>
import { useTheme } from '@/composables/useTheme'
const { currentTheme, toggle } = useTheme()
</script>

<style scoped>
.pg-theme-toggle {
  position: relative;
  width: 60px; height: 30px;
  border-radius: 999px;
  background: var(--pg-hover-surface);
  border: 1px solid var(--pg-glass-border);
  cursor: pointer;
  padding: 3px;
  transition: background 0.3s;
}
.pg-theme-toggle:hover { background: var(--pg-primary-soft); }
.pg-theme-thumb {
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--pg-primary-grad);
  box-shadow: 0 2px 8px rgba(91, 124, 250, 0.4);
  display: grid; place-items: center;
  font-size: 12px; color: white;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}
[data-theme="dark"] .pg-theme-thumb { transform: translateX(30px); }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/components/theme/ThemeToggle.vue
git commit -m "feat(UX-A): ThemeToggle 圆形主题切换按钮"
```

---

### Task 12: 前端 —— `ThemeDrawer.vue` 主题设置抽屉

**Files:**
- Create: `frontend/src/components/theme/ThemeDrawer.vue`

**Interfaces:**
- Consumes: `useTheme().pref / setMode / setSchedule`（Task 10）
- Produces:
  - `<ThemeDrawer v-model:visible="drawerVisible" />`
  - 内部：4 种模式 radio + 定时时段输入 + 应用范围（localStorage / account）

- [ ] **Step 1: 写组件**

```vue
<template>
  <el-drawer v-model="innerVisible" size="380px" :with-header="false" direction="rtl">
    <div class="pg-drawer">
      <div class="pg-drawer-head">
        <h2>主题设置</h2>
        <span class="pg-drawer-close" @click="innerVisible = false">×</span>
      </div>
      <div class="pg-drawer-body">

        <div class="pg-section-title">切换模式</div>
        <div class="pg-mode-list">
          <div class="pg-mode" :class="{ active: pref.mode === 'manual' }" @click="setMode('manual')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">☀ / ☾ 手动切换</div>
              <div class="pg-mode-desc">顶栏按钮点一下就切。</div>
            </div>
          </div>

          <div class="pg-mode" :class="{ active: pref.mode === 'schedule' }" @click="setMode('schedule')">
            <div class="pg-radio" />
            <div style="flex:1">
              <div class="pg-mode-name">⏰ 定时切换</div>
              <div class="pg-mode-desc">按固定时间点自动切主题。</div>
              <div v-if="pref.mode === 'schedule'" class="pg-schedule-inputs">
                <div>切亮色：<el-time-picker v-model="lightAt" format="HH:mm" size="small" @change="onScheduleChange" /></div>
                <div>切暗色：<el-time-picker v-model="darkAt" format="HH:mm" size="small" @change="onScheduleChange" /></div>
              </div>
            </div>
          </div>

          <div class="pg-mode" :class="{ active: pref.mode === 'system' }" @click="setMode('system')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">⚙ 跟随系统</div>
              <div class="pg-mode-desc">读取操作系统 prefers-color-scheme。</div>
            </div>
          </div>

          <div class="pg-mode" :class="{ active: pref.mode === 'sun' }" @click="setMode('sun')">
            <div class="pg-radio" />
            <div style="flex:1">
              <div class="pg-mode-name">☼ 日出日落</div>
              <div class="pg-mode-desc">按当前定位的日出日落时间切换。</div>
              <div v-if="pref.mode === 'sun'" class="pg-location-hint">
                当前定位：<b>北京</b>（不可修改）
              </div>
            </div>
          </div>
        </div>

        <div class="pg-section-title">应用范围</div>
        <div class="pg-mode-list">
          <div class="pg-mode" :class="{ active: pref.storage === 'local' }" @click="setStorage('local')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">仅本设备（localStorage）</div>
              <div class="pg-mode-desc">换设备需重设。</div>
            </div>
          </div>
          <div class="pg-mode" :class="{ active: pref.storage === 'account' }" @click="setStorage('account')">
            <div class="pg-radio" />
            <div>
              <div class="pg-mode-name">跟随账号（后端存储）</div>
              <div class="pg-mode-desc">保存到用户配置，换设备同步。</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useTheme } from '@/composables/useTheme'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible'])
const innerVisible = computed({
  get: () => props.visible,
  set: v => emit('update:visible', v)
})

const { pref, setMode: apiSetMode, setSchedule } = useTheme()

// 时间 picker 需要 Date 对象
const lightAt = ref(strToDate(pref.value.schedule.lightAt))
const darkAt = ref(strToDate(pref.value.schedule.darkAt))

function strToDate(hhmm) {
  const [h, m] = hhmm.split(':').map(Number)
  const d = new Date(); d.setHours(h, m, 0, 0); return d
}
function dateToStr(d) {
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
function setMode(mode) { apiSetMode(mode) }
function onScheduleChange() {
  setSchedule({ lightAt: dateToStr(lightAt.value), darkAt: dateToStr(darkAt.value) })
}
function setStorage(s) { apiSetMode(pref.value.mode, { storage: s }) }
</script>

<style scoped>
.pg-drawer { height: 100%; display: flex; flex-direction: column; }
.pg-drawer-head { padding: 18px 20px; border-bottom: 1px solid var(--pg-line); display: flex; justify-content: space-between; align-items: center; }
.pg-drawer-head h2 { margin: 0; font-size: 16px; color: var(--pg-text-white); }
.pg-drawer-close { cursor: pointer; color: var(--pg-text-secondary); font-size: 22px; padding: 0 6px; }
.pg-drawer-body { padding: 18px 20px; overflow-y: auto; flex: 1; }
.pg-section-title { font-size: 12px; color: var(--pg-text-placeholder); letter-spacing: 1px; margin: 22px 0 10px; font-weight: 500; }
.pg-section-title:first-child { margin-top: 0; }
.pg-mode-list { display: flex; flex-direction: column; gap: 8px; }
.pg-mode { padding: 12px 14px; border-radius: 12px; border: 1.5px solid var(--pg-line-strong); background: var(--pg-hover-surface); cursor: pointer; display: flex; gap: 12px; align-items: flex-start; }
.pg-mode.active { border-color: var(--pg-primary); background: var(--pg-primary-soft); }
.pg-radio { flex-shrink: 0; width: 16px; height: 16px; border-radius: 50%; border: 2px solid var(--pg-text-placeholder); margin-top: 2px; position: relative; }
.pg-mode.active .pg-radio { border-color: var(--pg-primary); }
.pg-mode.active .pg-radio::after { content: ""; position: absolute; inset: 2px; border-radius: 50%; background: var(--pg-primary); }
.pg-mode-name { font-size: 14px; font-weight: 600; color: var(--pg-text-white); }
.pg-mode-desc { font-size: 12px; color: var(--pg-text-secondary); margin-top: 4px; }
.pg-schedule-inputs { margin-top: 10px; padding: 10px 12px; background: var(--pg-hover-surface); border-radius: 10px; display: flex; flex-direction: column; gap: 8px; font-size: 12.5px; }
.pg-location-hint { margin-top: 8px; font-size: 12px; color: var(--pg-text-secondary); }
.pg-location-hint b { color: var(--pg-text-white); }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/components/theme/ThemeDrawer.vue
git commit -m "feat(UX-A): ThemeDrawer 主题设置抽屉"
```

---

### Task 13: 前端 —— `TopBar.vue` 挂入 ThemeToggle + 齿轮

**Files:**
- Modify: `frontend/src/components/layout/TopBar.vue`

**Interfaces:**
- Consumes: `ThemeToggle`（Task 11）、`ThemeDrawer`（Task 12）
- Produces: 顶栏右侧新增 ☀/☾ 切换与 ⚙ 齿轮打开抽屉

- [ ] **Step 1: 加 import 与状态**

在 TopBar.vue `<script setup>` 中加：

```js
import ThemeToggle from '@/components/theme/ThemeToggle.vue'
import ThemeDrawer from '@/components/theme/ThemeDrawer.vue'
import { ref } from 'vue'
const themeDrawerVisible = ref(false)
```

- [ ] **Step 2: 加 template 元素**

在现有 template "用户下拉" 元素之前（右侧区域），加：

```vue
<!-- UX-A: 主题切换 —— 为 UX-B 让位，用注释标记本区域为 UX-A slot -->
<!-- UX-A THEME START -->
<ThemeToggle style="margin-right: 8px" />
<span class="topbar-icon" title="主题设置" style="cursor:pointer; margin-right: 12px" @click="themeDrawerVisible = true">⚙</span>
<ThemeDrawer v-model:visible="themeDrawerVisible" />
<!-- UX-A THEME END -->
```

**注释标记 `<!-- UX-A THEME START/END -->` 是与 UX-B 的合并契约**：UX-B 修改 TopBar 时禁止改动这两个注释之间的内容。

- [ ] **Step 3: 浏览器目视验证**

Run: `npm run dev` 后访问 http://localhost:5173/dashboard
Expected:
- 顶栏右侧出现圆形 ☀ 图标（亮色）或 ☾（暗色）
- 图标右侧齿轮 ⚙ 图标
- 点击 ☀/☾ 亮暗立即切换
- 点击 ⚙ 从右侧滑出主题设置抽屉，4 种模式可切
- 切模式后 localStorage 中 `themePref` 有新值

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/layout/TopBar.vue
git commit -m "feat(UX-A): TopBar 挂入 ThemeToggle 与主题设置抽屉入口"
```

---

### Task 14: 前端 —— `SideMenu.vue` active 光带 + 分组标签美化（UI-02）

**Files:**
- Modify: `frontend/src/components/layout/SideMenu.vue`

**Interfaces:**
- Consumes: `--pg-primary-grad / --pg-hover-surface`（Task 6）
- Produces: 侧栏 active 项 3px 渐变光带 + 分组标签 uppercase 小字

- [ ] **Step 1: 加/改样式**

在 SideMenu.vue `<style>` 末尾追加（**不改现有 template 结构**）：

```css
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
```

- [ ] **Step 2: 浏览器目视验证**

Run: 访问 http://localhost:5173/dashboard
Expected:
- 当前 active 菜单项左侧出现 3px 蓝紫渐变光带
- hover 项浅色底
- 侧栏背景透明（能看到全局 blob 背景）

- [ ] **Step 3: 提交**

```bash
git add frontend/src/components/layout/SideMenu.vue
git commit -m "feat(UX-A/UI-02): SideMenu active 光带 + 透底"
```

---

### Task 15: 前端 —— `DashboardView.vue` KPI 5 卡改 grid（UI-03）

**Files:**
- Modify: `frontend/src/views/DashboardView.vue`

**Interfaces:**
- Consumes: `--pg-*` tokens
- Produces: 5 张 KPI 卡片改 `display: grid; grid-template-columns: repeat(5, minmax(0, 1fr))`，最右卡与下方 chart-card 最右像素严格对齐

- [ ] **Step 1: 改 template 与 style**

找到现有 `<el-row :gutter="16" class="stat-row">` 块（约 13-27 行），整块替换为：

```vue
<!-- UX-A UI-03: KPI 5 卡改 grid 均分，与下方模块最右像素严格对齐 -->
<div class="kpi-grid">
  <div v-for="card in statCards" :key="card.title" class="kpi-card">
    <div>
      <div class="stat-value">{{ card.value }}</div>
      <div class="stat-title">{{ card.title }}</div>
    </div>
    <el-icon class="stat-icon" :style="{ color: card.color }">
      <component :is="card.icon" />
    </el-icon>
  </div>
</div>
```

在 `<style>` 中加入：

```css
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.kpi-card {
  background: var(--pg-glass-bg);
  backdrop-filter: blur(var(--pg-glass-blur));
  border: 1px solid var(--pg-glass-border);
  border-radius: var(--pg-radius-md);
  box-shadow: var(--pg-glass-shadow);
  padding: 18px 20px;
  display: flex; align-items: center; justify-content: space-between;
  transition: transform 0.2s, box-shadow 0.2s;
}
.kpi-card:hover { transform: translateY(-2px); }
.stat-value { font-size: 24px; font-weight: 700; letter-spacing: -0.5px; color: var(--pg-text-white); }
.stat-title { font-size: 12.5px; color: var(--pg-text-secondary); margin-top: 6px; }
.stat-icon { font-size: 26px; }
```

- [ ] **Step 2: 浏览器目视验证 UI-03**

Run: 访问 http://localhost:5173/dashboard
Expected: 5 张 KPI 卡片均分整行，最右卡右边像素 = 下方"调用趋势"卡的右边像素（用 DevTools 选 element 对比 `getBoundingClientRect().right`）

- [ ] **Step 3: 提交**

```bash
git add frontend/src/views/DashboardView.vue
git commit -m "feat(UX-A/UI-03): Dashboard 5 卡改 grid 与下方模块严格对齐"
```

---

### Task 16: 抽查 5 个页面视觉一致性（UI-04 / UI-05）+ 硬编码色替换

**Files:**
- Modify（按发现替换）: `frontend/src/views/interface/InterfaceList.vue`、`frontend/src/views/convert/FieldMapping.vue`、`frontend/src/views/tools/Debug.vue`、`frontend/src/views/system/SysConfig.vue`（共 4 页 + Dashboard 已完成）

**Interfaces:**
- Consumes: `--pg-*` tokens
- Produces: 抽查 5 个页面无硬编码色（`#333`、`rgba(...)`）污染

- [ ] **Step 1: 浏览 5 个页面切亮暗**

分别访问以下 URL，切主题两次目视检查：
- `/dashboard`
- `/interface/list`
- `/convert/mapping`
- `/tools/debug`
- `/system/config`

Expected: 5 个页面亮/暗切换均无异常（无深色底浮出白字、无浅色底浮出黑字、无卡片背景不透明）

- [ ] **Step 2: 若发现硬编码色，替换**

用 Grep 搜每个页面 `.vue` 里的 `<style>` 部分：

```
Grep pattern: color:\s*#[0-9a-fA-F]{3,6}|background:\s*#[0-9a-fA-F]{3,6}
```

发现的硬编码色对照替换：
- 深灰 `#333 / #666 / #909399` → `var(--pg-text-primary / --pg-text-regular / --pg-text-secondary)`
- 白背景 `#fff / white` → `var(--pg-glass-bg)`
- 蓝主色 `#409eff` → `var(--pg-primary)`
- 灰边框 `#dcdfe6 / #ebeef5` → `var(--pg-line-strong / --pg-line)`

- [ ] **Step 3: 目视二次验证**

Run: 再切一次亮暗
Expected: 无硬编码色残留

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/
git commit -m "feat(UX-A/UI-04-05): 抽查 5 页替换硬编码色为 pg-tokens"
```

**若无硬编码色发现，跳过 Step 2、3、4，直接 Step 5 空提交跳过。**

---

### Task 17: 变更记录 CHG-016 + 问题清单条目搬迁

**Files:**
- Modify: `docs/03-开发/变更记录.md`（追加 CHG-016）
- Modify: `docs/03-开发/问题清单.md`（UI-01 ~ UI-06 从"待解决"搬到"已解决"）

**Interfaces:**
- Consumes: 无
- Produces: 项目管理文档同步

- [ ] **Step 1: 追加 CHG-016**

在 `docs/03-开发/变更记录.md` 中 CHG-015 之前（时间倒序）插入：

```markdown
### CHG-016 UX-A 全站视觉重塑 + 双主题切换实现

- **日期**：2026-07-XX（本单元交付日）
- **影响单元**：UX-A（新增，阶段六第 1 波）
- **变更类型**：范围新增（全站视觉重塑 + 主题偏好持久化）
- **变更内容**：
  - 后端：`sys_user` 加 `theme_pref TEXT` 列 + `SysUserThemePrefController` 2 端点（GET/PUT `/api/user/theme-pref`）
  - 前端：新增 `frontend/src/styles/tokens.css`（双主题）+ `element-plus-override.css`（Element Plus 变量桥接）
  - 前端：新增 `frontend/src/composables/useTheme.js`（4 模式：manual / schedule / system / sun）
  - 前端：新增 `ThemeToggle.vue` + `ThemeDrawer.vue`
  - 前端：`App.vue` 加背景 blob + 星空；`TopBar.vue` 挂入主题切换；`SideMenu.vue` active 光带；`DashboardView.vue` KPI 改 grid（UI-03）
- **影响文件**：详见任务计划 `docs/03-开发/任务计划/2026-07-19-UX-A-visual-refresh.md`
- **需求文档更新**：`需求拆分与最小实现方案.md` UX-A 节
- **原因**：问题清单 UI-01 ~ UI-06（源自 111.txt 全站视觉反馈）
- **设计文档**：`docs/02-设计/详细设计/2026-07-19-UX-A-visual-refresh-design.md`
- **验证**：22 个业务页面抽查 5 页目视一致；`sys_user.theme_pref` 存取端到端通；4 种模式在浏览器 Console 手工验证均正确切换

---
```

- [ ] **Step 2: 搬迁问题清单条目**

在 `docs/03-开发/问题清单.md`：
1. 在"已解决"章节顶部新增小节 `## 已解决（UX-A 批次 2026-07-XX）`
2. 从"2026-07-19 批次 → A 组"表格中提取 UI-01 ~ UI-06 的行，粘到新小节，每行加"已解决"标记
3. 从"待解决 → A 组"表格中删除 UI-01 ~ UI-06

- [ ] **Step 3: 提交**

```bash
git add "docs/03-开发/变更记录.md" "docs/03-开发/问题清单.md"
git commit -m "docs(UX-A): 追加 CHG-016 + 搬迁 UI-01~06 到已解决"
```

---

### Task 18: 端到端冒烟

**Files:** 无代码变更

**Interfaces:** 无

- [ ] **Step 1: 启动全栈**

Run: 项目根 `scripts\start.bat`
Expected: backend/frontend/pg-testkit 全部就绪

- [ ] **Step 2: 冒烟场景**

浏览器访问 http://localhost:5173，按以下顺序操作并观察：

1. **登录 admin / Admin@123** —— 无异常
2. **未登录状态**（先 logout）刷新页面 —— 无 API 401 报错、无 console 错误
3. **登录后切主题** —— 顶栏 ☀ → ☾ 立即生效
4. **打开抽屉切 mode=schedule** 设 lightAt=00:00 / darkAt=23:59 —— 主题保持 light
5. **抽屉切 mode=system** —— 主题与 Windows 系统一致（Win10 默认 light）
6. **抽屉切 mode=sun** —— 抽屉底部显示"当前定位：北京（不可修改）"
7. **强制刷新** —— 主题保持（localStorage 生效）
8. **切换到另一个浏览器登录同一账号**（用无痕窗口）—— 主题从后端加载，与另一浏览器一致
9. **访问 5 页各自切一次亮暗** —— 视觉一致

- [ ] **Step 3: 关闭**

Run: `scripts\stop.bat`

- [ ] **Step 4: 提交冒烟结论到 CHG-016**

若冒烟发现问题，回到对应 Task 修复；若通过，在 CHG-016 "验证" 一行追加冒烟通过时间戳。

```bash
git add "docs/03-开发/变更记录.md"
git commit -m "docs(UX-A): CHG-016 追加冒烟验收结果"
```

---

## 全计划自审清单（执行前 review）

- [x] **spec 覆盖率**：UI-01 (Task 6-15 tokens) / UI-02 (Task 14) / UI-03 (Task 15) / UI-04 UI-05 (Task 16) / UI-06 (Task 1-12 + Task 18) — 全覆盖
- [x] **无 TBD / TODO / 待定**：Task 16 中"若无硬编码色发现"是唯一条件跳过，属正常验收
- [x] **类型一致性**：`getUserThemePref` 返回类型（Task 5 = `Promise<object|null>`）、`useTheme().pref.value` 结构（Task 10 = DEFAULT_PREF 形状）在 Task 12 使用时一致
- [x] **文件路径精确**：所有 Files 均绝对/相对路径明确
- [x] **每 Task 独立可 review**：每个 Task 都有独立 commit + 可验证的 deliverable
- [x] **与其它组零冲突**：TopBar 用 `<!-- UX-A THEME START/END -->` 标记合并契约；tokens 与 override 是新文件；SideMenu / Dashboard 修改点与 UX-B 无重叠

## 实施顺序说明

Task 1 → 2 → 3 → 4 是后端依赖链（必须顺序执行）。
Task 5 依赖 Task 4 后端 API 就绪。
Task 6 → 7 → 8 → 9 是前端 CSS 基础，与 Task 10-15 并行可行（但同一 subagent 执行时建议顺序）。
Task 10 依赖 Task 5、6。
Task 11 → 12 → 13 是主题 UI 三件套，依赖 Task 10。
Task 14 → 15 是页面视觉修复，依赖 Task 6-7 tokens。
Task 16 是抽查，依赖以上所有。
Task 17 是文档收尾。
Task 18 是冒烟验收，最后。

**总计 18 个 Task**，若一个开发者串行执行预计 6-8 小时；若 subagent 并行（Task 6-9 CSS 与 Task 1-4 后端并行）预计 4-5 小时。
