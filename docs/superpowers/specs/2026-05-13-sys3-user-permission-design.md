# SYS-3 用户权限管理 设计文档

**日期**：2026-05-13  
**交付单元**：SYS-3  
**前置依赖**：P0-3（sys_user 表）、P0-4（Sa-Token 登录）

---

## 一、背景与目标

SYS-3 为 PowerGateway 增加三级角色权限控制（admin / user / readonly），通过菜单可见性区分用户能力范围。后端不做接口级拦截，控制点在前端菜单显隐 + 路由守卫。

---

## 二、角色菜单权限矩阵

权限逻辑在后端代码中硬编码（`MenuPermission` 常量类），`sys_config` 开关可在运行时叠加覆盖。

| 菜单路由 | admin | user | readonly |
|---------|-------|------|---------|
| /dashboard | ✅ | ✅ | ✅ |
| /convert/format | ✅ | ✅ | ❌ |
| /convert/field-mapping | ✅ | ✅ | ❌ |
| /convert/field-process | ✅ | ✅ | ❌ |
| /convert/channel | ✅ | ✅ | ❌ |
| /convert/port-route | ✅ | ✅ | ❌ |
| /convert/template | ✅ | ✅ | ❌ |
| /interface/db | ✅ | ✅ | ❌ |
| /interface/table | ✅ | ✅ | ❌ |
| /interface/dev | ✅ | ✅ | ❌ |
| /interface/insert | ✅ | ✅ | ❌ |
| /interface/update | ✅ | ✅ | ❌ |
| /interface/delete | ✅ | ❌ | ❌ |
| /interface/list | ✅ | ✅ | ✅ |
| /interface/shard | ✅ | ❌ | ❌ |
| /interface/formula | ✅ | ✅ | ❌ |
| /interface/cache | ✅ | ✅ | ✅ |
| /system/log | ✅ | ✅ | ❌ |
| /system/stats | ✅ | ✅ | ❌ |
| /system/user | ✅ | ❌ | ❌ |
| /system/config | ✅ | ❌ | ❌ |
| /tools/debug | ✅ | ✅ | ✅ |
| /tools/swagger | ✅ | ✅ | ✅ |

### sys_config 开关叠加

| config_key | 影响菜单 | 说明 |
|-----------|---------|------|
| `log_menu_enabled` | /system/log | 值为 `false` 时三角色均隐藏 |

---

## 三、后端设计

### 3.1 新增 MenuPermission（`config/MenuPermission.java`）

纯静态常量类，定义三个角色的路由白名单：

```java
public class MenuPermission {
    public static final List<String> ADMIN_MENUS = Arrays.asList(...全部路由...);
    public static final List<String> USER_MENUS  = Arrays.asList(...排除 delete/shard/user/config...);
    public static final List<String> READONLY_MENUS = Arrays.asList(
        "/dashboard", "/interface/list", "/interface/cache",
        "/tools/debug", "/tools/swagger"
    );
}
```

### 3.2 GET /api/auth/menu（追加到 AuthController）

逻辑：
1. `StpUtil.getLoginIdAsLong()` → `SysUserMapper.selectById()` → 取 `role`
2. 根据 role 选取对应白名单列表（副本）
3. 若 `SysConfigMapper.selectById("log_menu_enabled").configValue` 为 `"false"`，从列表中移除 `/system/log`
4. 返回 `Result<List<String>>`

`SysConfigMapper` 已存在（P0-3 已有），直接复用。

### 3.3 用户 CRUD — `/api/user/**`

**新增文件：**

| 文件 | 内容 |
|------|------|
| `model/dto/UserSaveRequest.java` | `id?`, `username`, `password?`, `role`, `status` |
| `model/dto/UserVO.java` | `id`, `username`, `role`, `status`, `createTime`（无 password） |
| `service/UserService.java` | CRUD 业务逻辑 |
| `controller/UserController.java` | REST 层 |

**接口清单：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/user/list | 分页列表，支持 username 模糊查询 |
| POST | /api/user/save | 新增（必填 password，BCrypt 加密）/ 更新（password 空=不改） |
| DELETE | /api/user/{id} | 逻辑删除（`deleted=1`） |

**业务约束：**
- 删除自己：`StpUtil.getLoginIdAsLong() == id` → 抛 `BusinessException(400, "不能删除当前登录账号")`
- 删除最后一个 admin：先 `COUNT(*) WHERE role='admin' AND deleted=0`，若 ≤1 → 抛 `BusinessException(400, "至少保留一个管理员账号")`
- 新增时用户名唯一校验（`sys_user.username UNIQUE` 约束会兜底，但先做应用层校验给友好提示）
- 密码最短 6 位
- `role` 只允许：`admin`、`user`、`readonly`

**不新增数据库表**，`sys_user` 已有所有需要字段。

---

## 四、前端设计

### 4.1 userStore 扩展（`store/user.js`）

新增：
```js
const allowedMenus = ref(JSON.parse(localStorage.getItem('allowedMenus') || '[]'))

function setAllowedMenus(menus) {
  allowedMenus.value = menus
  localStorage.setItem('allowedMenus', JSON.stringify(menus))
}
// logout() 中补充清空：
localStorage.removeItem('allowedMenus')
allowedMenus.value = []
```

持久化到 localStorage，避免刷新后闪烁重拉。

### 4.2 登录流程（`LoginView.vue` / auth.js）

登录成功后，紧接着调用 `GET /api/auth/menu`，将结果 `setAllowedMenus(menus)`，然后跳转首页。

新增 API 函数到 `api/auth.js`：
```js
export function getMenuPermissions() {
  return request.get('/auth/menu')
}
```

### 4.3 SideMenu.vue 改动

- 从 `useUserStore` 解构 `allowedMenus`
- 每个 `el-menu-item` 加 `v-if="allowedMenus.includes('/xxx')`
- `el-sub-menu` 若子项全不可见则整组隐藏（用计算属性判断该组是否有任何可见项）

### 4.4 路由守卫补充（`router/index.js`）

在现有守卫 `router.beforeEach` 中补充：

```js
// 已登录且菜单权限已加载时，拦截越权直接 URL 访问
if (userStore.isLoggedIn
    && to.meta.requiresAuth !== false
    && to.path !== '/dashboard'
    && userStore.allowedMenus.length > 0
    && !userStore.allowedMenus.includes(to.path)) {
  next('/dashboard')
  return
}
```

### 4.5 UserList.vue（`views/system/UserList.vue`）

- 搜索栏（用户名模糊）+ 查询按钮 + 新建按钮
- `el-table` 列：用户名、角色（`el-tag` 颜色区分）、状态、创建时间、操作（编辑/删除）
- 新建/编辑 `el-dialog`：
  - 用户名（新增时必填，更新时只读）
  - 密码（新增必填 ≥6位，更新时留空=不改，`type="password"`，`show-password`）
  - 角色 `el-select`（admin/user/readonly）
  - 状态 `el-switch`（启用/禁用）
- 删除用 `el-popconfirm` 确认

`router/index.js` 中 `/system/user` 路由已存在（目前指向 PlaceholderView），替换为 `UserList.vue`。

---

## 五、测试设计

### 后端测试（`SYS3UserTest.java`）

| 用例 | 验证点 |
|------|--------|
| getMenu_adminRole_返回全部路由 | allowedMenus 包含 /interface/delete 和 /system/user |
| getMenu_userRole_排除敏感路由 | 不含 /interface/delete、/interface/shard、/system/user |
| getMenu_readonlyRole_最小路由 | 只含 /dashboard、/interface/list 等5项 |
| getMenu_logMenuDisabled_隐藏日志菜单 | sys_config 设 log_menu_enabled=false 后 /system/log 不在列表 |
| saveUser_新增_密码BCrypt存储 | 保存后查库 password 不等于明文 |
| saveUser_用户名重复_抛异常 | 重复 username 返回 400 |
| saveUser_密码过短_抛异常 | password.length < 6 返回 400 |
| deleteUser_删自己_抛异常 | 当前登录用户 id 返回 400 |
| deleteUser_删最后一个admin_抛异常 | 只剩一个 admin 时返回 400 |
| listUser_分页查询_返回UserVO | 无 password 字段 |

---

## 六、验收标准

1. `readonly` 角色用户登录后，侧边栏不显示「删除接口配置」和「分库分表配置」菜单项
2. `user` 角色用户登录后，不显示「用户权限管理」「系统配置」「删除接口配置」「分库分表配置」
3. `admin` 角色用户登录后，所有菜单可见
4. 直接在地址栏输入无权路由，自动跳转到 `/dashboard`
5. 用户 CRUD 全流程正常，密码脱敏，BCrypt 加密存储
6. `log_menu_enabled=false` 时日志菜单对所有角色隐藏

---

## 七、router 路由替换说明

以下两条路由在 `router/index.js` 中已存在但指向 PlaceholderView，SYS-3 中替换为实际组件：

| 路由 | 替换为 |
|------|--------|
| `/system/user` | `views/system/UserList.vue` |

---

## 八、不在本次范围内

- 接口级 `@SaCheckRole` 保护（前端菜单隐藏即为控制点）
- 角色对应菜单的可视化配置界面（权限在代码中维护）
- 密码找回功能
- 用户头像/个人信息页
