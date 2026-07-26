# SYS-3 权限体系升级 · 设计文档

## 元信息

| 项 | 值 |
|---|---|
| 代号 | SYS-3-U（SYS-3 Upgrade） |
| 类型 | 已交付单元的**大范围重构**（对齐 CHG 规约） |
| 目标版本 | v0.5.0（FN-BIZ 大版本前置依赖，随同一 tag 发布） |
| 依赖前置 | 无（SYS-3 已交付；本文档描述升级方案） |
| 触发反馈 | FB-041 · CR-002（详见 [反馈簿](../../06-项目管理/反馈簿.md#fb-041) / [待办与缺陷池](../../06-项目管理/待办与缺陷池.md#cr-002-fn-biz-业务菜单生成能力--sys-3-升级)） |
| 关联设计 | [2026-07-26-FN-BIZ-business-menu-generation-design.md](./2026-07-26-FN-BIZ-business-menu-generation-design.md)（主功能） |
| 状态 | 待评审 · 待排期 |

---

## 一、背景与动机

现有 SYS-3（[原设计](./2026-05-13-sys3-user-permission-design.md)）采用"三固定角色 + 硬编码菜单白名单"模型：

- `sys_user.role` 单值列（枚举 admin/tester/user）
- `MenuPermission.java` 常量 Map 存"角色 → 允许菜单 code 列表"
- 前端 `SideMenu.vue` 硬编码菜单结构 + v-if 判断

**引入 FN-BIZ 业务菜单生成能力后**，此模型无法满足以下诉求（详见 FB-041 用户第 3 点披露）：

1. **菜单权限分级**（一级 + 二级），授一级自动带二级
2. **分级授权**：高权限用户可给低权限用户按需授权（不仅限 admin）
3. **业务应用自建角色**：如 CRM 应用自动派生 CRM_USER 角色，无法预枚举
4. **统一菜单模型**：内置菜单 + testkit 模块菜单 + 业务生成菜单必须走同一套权限查询

因此 SYS-3 需从"三角色白名单常量"升级为"角色/菜单/授权关系全 DB 驱动"。

---

## 二、目标 / 非目标

### 目标

| 序 | 目标 |
|---|---|
| G1 | 菜单全量迁入 DB 表 `sys_menu`（NATIVE + TESTKIT + BIZ_APP + BIZ 四种来源统一） |
| G2 | 角色支持自定义（在内置 admin/tester/user 之外允许运行时新建） |
| G3 | 一用户多角色（`sys_user.role` 单值 → 多对多中间表） |
| G4 | 授权继承：授一级菜单自动带子菜单（存储层"只存父不存子"，查询时展开） |
| G5 | 分级授权：`sys_role.can_grant=1` 的角色可授"自己已拥有菜单子集"给其他角色 |
| G6 | 完全迁移零权限漂移：升级前后三内置角色可见菜单集合逐条断言相等 |
| G7 | 授权动作全审计（sys_log + business_op_log 双写，含 `granted_by` 链路追溯） |

### 非目标

- ❌ 不做"字段级"权限（列可见性）—— 仅到菜单粒度
- ❌ 不做"按钮级"权限（页面内 CRUD 按钮可见性）—— 由业务菜单模板固定
- ❌ 不引入外部 IDP / SSO
- ❌ 不做多租户
- ❌ 不做"临时授权"（时效性授权）—— 权限一旦授予永久有效直到 revoke

---

## 三、数据模型

### 3.1 sys_menu · 统一菜单表

```sql
CREATE TABLE sys_menu (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  code          VARCHAR(128) NOT NULL       COMMENT '菜单编码全局唯一',
  name          VARCHAR(128) NOT NULL       COMMENT '菜单显示名（admin 可手改）',
  icon          VARCHAR(64)                 COMMENT '图标',
  parent_id     BIGINT                      COMMENT '父菜单 id，NULL=一级',
  sort_order    INT          DEFAULT 0,

  type          VARCHAR(16)  NOT NULL       COMMENT 'CATEGORY(一级分组) | PAGE(可访问)',
  origin        VARCHAR(16)  NOT NULL       COMMENT 'NATIVE | TESTKIT | BIZ_APP | BIZ',

  badge         VARCHAR(16)                 COMMENT '[业务]/[测试]/[NEW]',
  badge_color   VARCHAR(16)                 COMMENT 'primary/success/warning/danger/info',

  route_path    VARCHAR(255),
  component     VARCHAR(255)                COMMENT 'Vue 组件路径；BIZ 类型统一为 features/biz-menu/BusinessInterfacePage.vue',

  -- 业务菜单专属，其他 origin 空
  interface_id  BIGINT                      COMMENT '关联 interface_config.id',
  app_menu_id   BIGINT                      COMMENT '所属业务应用（另一行 sys_menu，origin=BIZ_APP）',
  status        VARCHAR(16)  DEFAULT 'NORMAL' COMMENT 'NORMAL/EXPIRED/DISABLED/ORPHAN',
  metadata_hash VARCHAR(64)                 COMMENT '接口元数据快照 hash',
  display_props TEXT                        COMMENT 'JSON: admin 手改属性，重新生成保护',

  is_builtin    TINYINT      DEFAULT 0      COMMENT '1=内置不可删',
  visible       TINYINT      DEFAULT 1,
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      DEFAULT 0,

  UNIQUE KEY uk_code (code),
  KEY idx_parent (parent_id),
  KEY idx_interface (interface_id),
  KEY idx_app_menu (app_menu_id),
  KEY idx_origin_status (origin, status)
);
```

**type × origin 矩阵**：

| origin \ type | CATEGORY | PAGE |
|---|---|---|
| NATIVE | 系统概览/接口转换/可视化接口/辅助工具/系统管理（5 大一级分组） | 系统概览页/缓存管理页/用户管理页 等 |
| TESTKIT | 测试工具（一级分组） | MockServerRules/DemoDbManage/MockServerHistory |
| BIZ_APP | 业务应用根节点（CRM/订单/财务）—— 由 admin 建 | — |
| BIZ | 业务菜单分组（"客户相关"等，可选，admin 手建） | 业务菜单项（客户查询/新增/…），由 FN-BIZ 生成中心生成 |

**badge 默认派生**：origin=TESTKIT→`[测试]/warning`；origin=BIZ_APP→`[应用]/primary`；origin=BIZ→`[业务]/success`；NATIVE 无。admin 可手改，重新生成不覆盖（同 display_props 保护机制）。

### 3.2 sys_role · 角色表

```sql
CREATE TABLE sys_role (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  code         VARCHAR(64)  NOT NULL,
  name         VARCHAR(128) NOT NULL,
  description  VARCHAR(255),
  is_builtin   TINYINT      DEFAULT 0      COMMENT '1=内置不可删',
  can_grant    TINYINT      DEFAULT 0      COMMENT '1=允许分级授权',
  auto_created TINYINT      DEFAULT 0      COMMENT '1=业务应用创建时自动派生',
  linked_app_menu_id BIGINT               COMMENT 'auto_created=1 时关联的业务应用节点',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT      DEFAULT 0,
  UNIQUE KEY uk_code (code)
);
```

内置数据：
- `admin` (is_builtin=1, can_grant=1)
- `tester` (is_builtin=1)
- `user` (is_builtin=1)

### 3.3 sys_user_role & sys_role_menu · 多对多关联

```sql
CREATE TABLE sys_user_role (
  user_id       BIGINT NOT NULL,
  role_id       BIGINT NOT NULL,
  granted_by    BIGINT       COMMENT '授权人 user_id（NULL=初始化）',
  granted_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, role_id),
  KEY idx_role (role_id)
);

CREATE TABLE sys_role_menu (
  role_id       BIGINT NOT NULL,
  menu_id       BIGINT NOT NULL,
  granted_by    BIGINT       COMMENT '授权人 user_id',
  granted_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (role_id, menu_id),
  KEY idx_menu (menu_id)
);
```

### 3.4 sys_user 改造

```sql
-- 删除 role 单值列（迁移完成后最后一步执行）
ALTER TABLE sys_user DROP COLUMN role;
```

**注意**：迁移过程中先写 sys_user_role，再删原列。回滚脚本要保留列的重建方案（见 §五 迁移方案）。

---

## 四、授权继承 & 分级授权

### 4.1 授权继承（存储层"只存父不存子"）

**规则**：sys_role_menu 只记录被显式授权的菜单 id；父菜单被授权即隐式带全部子菜单。

**查询展开算法**：

```
输入：userId
输出：可见菜单树

1. userRoleIds = SELECT role_id FROM sys_user_role WHERE user_id=?
2. explicitMenuIds = SELECT menu_id FROM sys_role_menu WHERE role_id IN userRoleIds
3. allMenuIds = 递归 explicitMenuIds 的所有子节点 union explicitMenuIds
4. menuTree = SELECT * FROM sys_menu WHERE id IN allMenuIds AND deleted=0 AND visible=1
5. 按 parent_id/sort_order 组装树返回
```

**优点**：
- 父菜单 revoke → 子菜单自动隐（无需级联删数据）
- 新增子菜单无需回填授权行
- 存储量小

**代价**：
- 每次拉菜单要递归展开 —— 但菜单量级 (~50-100 行)，一次查询完全可接受

### 4.2 分级授权四层校验

`RoleService.grantMenus(currentUser, targetRoleId, menuIds)` 严格顺序执行：

```
校验层 1: currentUser 至少有一个角色 can_grant=1？
   否 → 403 "无授权能力"

校验层 2: 展开 currentUser 全部可见菜单集合 mySet

校验层 3: menuIds ⊆ mySet？
   否 → 400 "无权授予未拥有菜单: [...]"

校验层 4: targetRole.is_builtin=1 AND currentUser 非 admin？
   是 → 400 "内置角色仅 admin 可改"

通过 → INSERT ON DUPLICATE KEY sys_role_menu
     + granted_by = currentUser.id
     + 写 sys_log + business_op_log
     + 广播 RoleMenuChangedEvent → SSE 推送在线用户重刷菜单
```

**审计回溯**：`granted_by` 字段可反查授权链，未来支持"授权者离职时自动收回其授出权限"扩展。

---

## 五、迁移方案（一次性 Migration）

**关键约束**：必须**零权限漂移**。升级前后三内置角色可见菜单集合逐条断言相等。

### 5.1 迁移六步

```
Step 1  建表：sys_menu / sys_role / sys_user_role / sys_role_menu
Step 2  内置菜单迁入：
         - 从 MenuPermission.java 常量表读取 5 大一级 + 全部二级
         - INSERT INTO sys_menu (origin=NATIVE, is_builtin=1, ...)
Step 3  testkit 菜单迁入：
         - 从 frontend/src/modules/testkit/router.js 读取 3 页面 + 1 分组
         - INSERT INTO sys_menu (origin=TESTKIT, is_builtin=1, ...)
Step 4  内置角色初始化：
         - INSERT INTO sys_role (admin, tester, user, is_builtin=1)
         - 按 MenuPermission.java 白名单反演成 sys_role_menu 行（父菜单授权即可，子菜单靠继承）
Step 5  用户角色迁移：
         - INSERT INTO sys_user_role (user_id, role_id) SELECT id, role_id_by_code FROM sys_user
Step 6  删除 sys_user.role 列（最后一步，前面全部成功后执行）
```

### 5.2 迁移前后对账脚本

```sql
-- 迁移前基线（存到临时表）
CREATE TABLE _pre_migration_menu_snapshot AS
SELECT user_id, GROUP_CONCAT(menu_code ORDER BY menu_code) AS visible_menus
FROM (
  -- 用旧 MenuPermission 逻辑计算每个用户的可见菜单集合
  ...
) t GROUP BY user_id;

-- 迁移后
CREATE TABLE _post_migration_menu_snapshot AS
SELECT user_id, GROUP_CONCAT(menu_code ORDER BY menu_code) AS visible_menus
FROM (
  SELECT DISTINCT su.id AS user_id, sm.code AS menu_code
  FROM sys_user su
  JOIN sys_user_role sur ON sur.user_id = su.id
  JOIN sys_role_menu srm ON srm.role_id = sur.role_id
  JOIN sys_menu sm ON sm.id = srm.menu_id
  -- 递归展开子菜单
  ...
) t GROUP BY user_id;

-- 对账：应返回 0 行
SELECT * FROM _pre_migration_menu_snapshot pre
LEFT JOIN _post_migration_menu_snapshot post ON pre.user_id = post.user_id
WHERE pre.visible_menus != post.visible_menus OR post.visible_menus IS NULL;
```

任一行差异 → 迁移失败，整体回滚。

### 5.3 回滚脚本

- 保留 `ALTER TABLE sys_user ADD COLUMN role VARCHAR(32);` + 反向迁移脚本
- 保留 `MenuPermission.java` 代码不删除，仅停用注入
- 一键回滚：drop 新四表 → 恢复列 → 重新注入 MenuPermission

---

## 六、后端 API

| 方法 & 路径 | 用途 |
|---|---|
| `GET /api/menu/tree` | 拉当前用户完整菜单树（后端已按角色并集过滤、按 status 打徽章） |
| `GET/POST/PUT/DELETE /api/menu` | 菜单 CRUD（内置菜单只读，业务菜单可改） |
| `GET/POST/PUT/DELETE /api/role` | 角色 CRUD（内置不可删） |
| `POST /api/role/{roleId}/menus` | 给角色批量授菜单，body: `{menuIds:[]}` |
| `DELETE /api/role/{roleId}/menus/{menuId}` | revoke 单菜单 |
| `POST /api/user/{userId}/roles` | 给用户批量派角色 |
| `POST /api/role/{roleId}/grantable` | admin 开通某角色的分级授权能力（`can_grant=1`） |

**弃用**：`GET /api/auth/menu`（SYS-3 旧接口）→ 保留 3 版本后移除。

---

## 七、前端页面

| 路径 | 组件 | 变更 |
|---|---|---|
| `/system/users` | `UserList.vue` | 表格新增"角色（多选）"列，替代原单选 |
| `/system/roles` | `RoleList.vue` | 🆕 角色 CRUD + 右侧抽屉授菜单矩阵 |
| `/system/menus` | `MenuManagement.vue` | 🆕 菜单表管理（按 origin 分标签页：内置/测试/业务应用/业务） |
| 侧栏 `SideMenu.vue` | | 硬编码菜单树移除，改为登录后拉 `GET /api/menu/tree` 动态渲染 |
| 路由守卫 `router/index.js` | | 已有的越权拦截逻辑保留，判断依据从常量改为 store.menuTree |

---

## 八、测试策略

### 8.1 后端测试增量（预估 +40-50 用例）

| 类 | 用例数 | 覆盖 |
|---|---|---|
| `SysMenuServiceTest` | ~8 | CRUD + 内置菜单保护 + 递归子菜单查询 |
| `SysRoleServiceTest` | ~6 | CRUD + 内置角色保护 + can_grant 切换 |
| `AuthMenuTreeTest` | ~10 | 授权继承展开 / 多角色并集 / status 徽章 / visible=0 过滤 |
| `RoleGrantTest` | ~15 | 分级授权 4 层校验的每一条失败路径 + 成功路径 + 边界（越权/授内置） |
| `MigrationTest` | ~8 | 六步迁移原子性 / 对账脚本零漂移 / 回滚 |

### 8.2 前端测试增量（预估 +15 用例）

- `SideMenu.spec.js`：动态菜单树渲染 / 徽章展示 / 状态样式
- `RoleList.spec.js`：授权矩阵抽屉 / 越权提示
- `UserList.spec.js`：角色多选保存

### 8.3 手工验证

**必测项**：
1. 迁移前 admin/tester/user 三账号登录截图侧栏
2. 迁移后同三账号登录截图侧栏
3. 对账脚本必须零差异
4. 创建 CRM_USER 角色 → 授菜单 → CRM_USER 账号登录看到预期菜单
5. `can_grant=1` 用户尝试越权授菜单 → 400
6. revoke 父菜单后子菜单自动消失

---

## 九、风险

| 序 | 风险 | 缓解 |
|---|---|---|
| R1 | 迁移脚本漏迁菜单致权限收窄 | 对账脚本 + admin 手工 diff |
| R2 | 分级授权规则被绕过 | 4 层校验单测 100% 覆盖失败路径 |
| R3 | 递归子菜单查询性能问题 | 菜单量级 <200 行，一次查询 <10ms 可接受 |
| R4 | 前端菜单硬编码残留（拉到 API 之外还有旧逻辑） | 全仓 Grep `MenuPermission` / `allowedMenus` 硬编码引用清零 |
| R5 | testkit 独立模块菜单挂载改动破坏现有功能 | testkit 模块迁移后独立回归测试 |
| R6 | 升级过程中在线用户 stale menu | 首次上线打全站维护通知，用户重登刷 |

---

## 十、交付物清单

### 代码

- `backend/src/main/java/com/powergateway/model/`
  - `SysMenu.java` / `SysRole.java` / `SysUserRole.java` / `SysRoleMenu.java`
- `backend/src/main/java/com/powergateway/dao/` 对应 4 个 Mapper
- `backend/src/main/java/com/powergateway/service/`
  - `SysMenuService.java`（含 `expandChildren` 递归查询）
  - `SysRoleService.java`（含 4 层校验）
  - `AuthMenuService.java`（拉当前用户菜单树）
- `backend/src/main/java/com/powergateway/controller/`
  - `SysMenuController.java` / `SysRoleController.java`（新增）
  - `AuthController.java`（新增 `/api/menu/tree`）
- `backend/src/main/resources/db/migration/V2026072601__sys_menu_upgrade.sql`（迁移脚本）
- `backend/src/main/resources/db/migration/V2026072602__seed_native_testkit_menus.sql`（数据初始化）
- `frontend/src/api/menu.js` / `role.js`（新增）
- `frontend/src/views/system/RoleList.vue` / `MenuManagement.vue`（新增）
- `frontend/src/views/system/UserList.vue`（改造）
- `frontend/src/components/layout/SideMenu.vue`（动态化改造）

### 文档

- 本设计文档
- [变更记录 CHG-XXX](../../03-开发/变更记录.md)（v0.5.0 发版时补 CHG 编号）
- [反馈簿 FB-041](../../06-项目管理/反馈簿.md#fb-041)
- [待办与缺陷池 CR-002](../../06-项目管理/待办与缺陷池.md#cr-002)
- 更新 [`docs/01-需求/需求拆分与最小实现方案.md`](../../01-需求/需求拆分与最小实现方案.md) SYS-3 单元段
- 更新 [`docs/03-开发/开发计划.md`](../../03-开发/开发计划.md) SYS-3 行

### 测试

- 后端 +40~50 用例
- 前端 +15 用例
- 迁移对账脚本
- 手工验证 checklist

---

## 十一、开放问题

| Q | 建议 |
|---|---|
| 是否支持"临时授权"（时效性）？ | 非目标，v1.0 之前不做 |
| 是否支持"授权者离职自动收回其授出权限"？ | 已通过 `granted_by` 字段预留能力，v0.5.0 不实现 |
| 分级授权是否要"审批流"（授权前需上级批准）？ | 非目标，简化模型 |
| 角色能否嵌套（角色包含其他角色）？ | 非目标，扁平模型 |
