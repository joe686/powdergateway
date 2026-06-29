# SYS-3 用户权限管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现三级角色（admin/user/readonly）菜单权限控制 + 用户 CRUD 管理页面。

**Architecture:** 后端 `MenuPermission` 静态常量类维护角色白名单，`GET /api/auth/menu` 叠加 sys_config 开关后返回路由列表；前端 userStore 存储列表，SideMenu.vue 用 v-if 控制可见性，路由守卫拦截直接 URL 访问；UserController/UserService 提供用户 CRUD。

**Tech Stack:** Spring Boot 2.7 / Sa-Token / BCrypt / Vue 3 / Element Plus / Pinia

---

## 文件清单

| 操作 | 文件 |
|------|------|
| 新建 | `backend/src/main/java/com/powergateway/config/MenuPermission.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/UserSaveRequest.java` |
| 新建 | `backend/src/main/java/com/powergateway/model/dto/UserVO.java` |
| 新建 | `backend/src/main/java/com/powergateway/service/UserService.java` |
| 新建 | `backend/src/main/java/com/powergateway/controller/UserController.java` |
| 新建 | `backend/src/test/java/com/powergateway/SYS3UserTest.java` |
| 新建 | `frontend/src/api/user.js` |
| 新建 | `frontend/src/views/system/UserList.vue` |
| 修改 | `backend/src/main/java/com/powergateway/service/AuthService.java` |
| 修改 | `backend/src/main/java/com/powergateway/controller/AuthController.java` |
| 修改 | `frontend/src/store/user.js` |
| 修改 | `frontend/src/api/auth.js` |
| 修改 | `frontend/src/views/LoginView.vue` |
| 修改 | `frontend/src/components/layout/SideMenu.vue` |
| 修改 | `frontend/src/router/index.js` |

---

## Task 1: 后端 DTO + MenuPermission 常量类

**Files:**
- Create: `backend/src/main/java/com/powergateway/config/MenuPermission.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/UserSaveRequest.java`
- Create: `backend/src/main/java/com/powergateway/model/dto/UserVO.java`

- [ ] **Step 1: 创建 MenuPermission.java**

```java
package com.powergateway.config;

import java.util.Arrays;
import java.util.List;

/**
 * 角色菜单权限白名单（SYS-3）。
 * 权限在此处硬编码，sys_config 开关可在运行时叠加覆盖。
 */
public class MenuPermission {

    public static final List<String> ADMIN_MENUS = Arrays.asList(
        "/dashboard",
        "/convert/format", "/convert/field-mapping", "/convert/field-process",
        "/convert/channel", "/convert/port-route", "/convert/template",
        "/interface/db", "/interface/table", "/interface/dev",
        "/interface/insert", "/interface/update", "/interface/delete",
        "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
        "/system/log", "/system/stats", "/system/user", "/system/config",
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> USER_MENUS = Arrays.asList(
        "/dashboard",
        "/convert/format", "/convert/field-mapping", "/convert/field-process",
        "/convert/channel", "/convert/port-route", "/convert/template",
        "/interface/db", "/interface/table", "/interface/dev",
        "/interface/insert", "/interface/update",
        "/interface/list", "/interface/formula", "/interface/cache",
        "/system/log", "/system/stats",
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> READONLY_MENUS = Arrays.asList(
        "/dashboard",
        "/interface/list", "/interface/cache",
        "/tools/debug", "/tools/swagger"
    );

    /** sys_config 日志菜单开关的 key */
    public static final String LOG_MENU_CONFIG_KEY = "log_menu_enabled";
    /** 受开关控制的菜单路由 */
    public static final String LOG_MENU_PATH = "/system/log";
}
```

- [ ] **Step 2: 创建 UserSaveRequest.java**

```java
package com.powergateway.model.dto;

import lombok.Data;

/**
 * 用户保存请求 DTO（SYS-3）。
 * id 为 null 时新增，非 null 时更新。
 * 更新时 password 为空则不修改密码。
 */
@Data
public class UserSaveRequest {
    /** null = 新增，非 null = 更新 */
    private Long id;
    /** 用户名，新增时必填 */
    private String username;
    /** 密码，新增时必填且 ≥6位；更新时为空=不改 */
    private String password;
    /** 角色：admin / user / readonly */
    private String role;
    /** 状态：1=启用，0=禁用 */
    private Integer status;
}
```

- [ ] **Step 3: 创建 UserVO.java**

```java
package com.powergateway.model.dto;

import com.powergateway.model.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应 VO（SYS-3），不含 password 字段。
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String role;
    private Integer status;
    private LocalDateTime createTime;

    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
cd backend && mvn compile -q
```

期望：无报错。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/powergateway/config/MenuPermission.java \
        backend/src/main/java/com/powergateway/model/dto/UserSaveRequest.java \
        backend/src/main/java/com/powergateway/model/dto/UserVO.java
git commit -m "feat(SYS-3): add MenuPermission, UserSaveRequest, UserVO"
```

---

## Task 2: GET /api/auth/menu（TDD）

**Files:**
- Create: `backend/src/test/java/com/powergateway/SYS3UserTest.java`（仅菜单测试部分，用户 CRUD 测试 Task 3 追加）
- Modify: `backend/src/main/java/com/powergateway/service/AuthService.java`
- Modify: `backend/src/main/java/com/powergateway/controller/AuthController.java`

- [ ] **Step 1: 创建测试文件（菜单测试部分）**

```java
package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.model.SysConfig;
import com.powergateway.model.SysUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-3 用户权限管理")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS3UserTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private SysConfigMapper sysConfigMapper;
    @Autowired private ObjectMapper objectMapper;

    private String adminToken;
    private Long testUserRoleId;
    private Long testReadonlyRoleId;

    private static final String TEST_PASSWORD = "Test@123";
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeAll
    void setUp() {
        SysUser userRoleUser = new SysUser();
        userRoleUser.setUsername("sys3_user_" + System.currentTimeMillis());
        userRoleUser.setPassword(encoder.encode(TEST_PASSWORD));
        userRoleUser.setRole("user");
        userRoleUser.setStatus(1);
        sysUserMapper.insert(userRoleUser);
        testUserRoleId = userRoleUser.getId();

        SysUser readonlyUser = new SysUser();
        readonlyUser.setUsername("sys3_ro_" + System.currentTimeMillis());
        readonlyUser.setPassword(encoder.encode(TEST_PASSWORD));
        readonlyUser.setRole("readonly");
        readonlyUser.setStatus(1);
        sysUserMapper.insert(readonlyUser);
        testReadonlyRoleId = readonlyUser.getId();
    }

    @BeforeEach
    void loginAsAdmin() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        adminToken = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @AfterAll
    void cleanup() {
        if (testUserRoleId != null)    sysUserMapper.deleteById(testUserRoleId);
        if (testReadonlyRoleId != null) sysUserMapper.deleteById(testReadonlyRoleId);
    }

    // ─── GET /api/auth/menu ─────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("getMenu_admin角色_包含全部菜单")
    void getMenu_adminRole_containsAllMenus() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/auth/menu").header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        List<String> menus = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assertThat(menus).contains(
            "/interface/delete", "/interface/shard", "/system/user", "/system/config", "/system/log"
        );
    }

    @Test @Order(2)
    @DisplayName("getMenu_user角色_排除删除和管理菜单")
    void getMenu_userRole_excludesSensitiveMenus() throws Exception {
        SysUser testUser = sysUserMapper.selectById(testUserRoleId);
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + testUser.getUsername() + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andReturn();
        String userToken = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.data.token");

        MvcResult r = mockMvc.perform(get("/api/auth/menu").header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        List<String> menus = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assertThat(menus).doesNotContain(
            "/interface/delete", "/interface/shard", "/system/user", "/system/config"
        );
        assertThat(menus).contains("/dashboard", "/interface/list", "/system/log");
    }

    @Test @Order(3)
    @DisplayName("getMenu_readonly角色_最小菜单集")
    void getMenu_readonlyRole_minimalMenus() throws Exception {
        SysUser readonlyUser = sysUserMapper.selectById(testReadonlyRoleId);
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + readonlyUser.getUsername() + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andReturn();
        String readonlyToken = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.data.token");

        MvcResult r = mockMvc.perform(get("/api/auth/menu").header("satoken", readonlyToken))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        List<String> menus = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
        assertThat(menus).containsExactlyInAnyOrder(
            "/dashboard", "/interface/list", "/interface/cache",
            "/tools/debug", "/tools/swagger"
        );
    }

    @Test @Order(4)
    @DisplayName("getMenu_log菜单关闭_system/log不在任何角色列表")
    void getMenu_logMenuDisabled_systemLogHidden() throws Exception {
        SysConfig existing = sysConfigMapper.selectById("log_menu_enabled");
        SysConfig cfg = new SysConfig();
        cfg.setConfigKey("log_menu_enabled");
        cfg.setConfigValue("false");
        cfg.setDescription("日志菜单开关");
        if (existing != null) {
            sysConfigMapper.updateById(cfg);
        } else {
            sysConfigMapper.insert(cfg);
        }
        try {
            MvcResult r = mockMvc.perform(get("/api/auth/menu").header("satoken", adminToken))
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();
            List<String> menus = JsonPath.read(r.getResponse().getContentAsString(), "$.data");
            assertThat(menus).doesNotContain("/system/log");
        } finally {
            if (existing != null) {
                sysConfigMapper.updateById(existing);
            } else {
                sysConfigMapper.deleteById("log_menu_enabled");
            }
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败（/api/auth/menu 未实现）**

```bash
cd backend && mvn test -Dtest=SYS3UserTest#getMenu_adminRole_containsAllMenus -q 2>&1 | tail -5
```

期望：FAIL（404 或 compilation error）

- [ ] **Step 3: 修改 AuthService.java — 注入 SysConfigMapper 并添加 getMenuForCurrentUser()**

在 AuthService 的 `@Autowired` 字段区追加：

```java
    @Autowired
    private SysConfigMapper sysConfigMapper;
```

在 `getCurrentUserInfo()` 方法后追加：

```java
    public List<String> getMenuForCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);

        List<String> menus;
        switch (user.getRole()) {
            case "admin":    menus = new ArrayList<>(MenuPermission.ADMIN_MENUS); break;
            case "user":     menus = new ArrayList<>(MenuPermission.USER_MENUS);  break;
            case "readonly": menus = new ArrayList<>(MenuPermission.READONLY_MENUS); break;
            default:         menus = new ArrayList<>(MenuPermission.READONLY_MENUS); break;
        }

        SysConfig logConfig = sysConfigMapper.selectById(MenuPermission.LOG_MENU_CONFIG_KEY);
        if (logConfig != null && "false".equalsIgnoreCase(logConfig.getConfigValue())) {
            menus.remove(MenuPermission.LOG_MENU_PATH);
        }

        return menus;
    }
```

同时在文件顶部追加 import（若缺失）：

```java
import com.powergateway.config.MenuPermission;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.SysConfig;
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 4: 修改 AuthController.java — 追加 /menu 端点**

在 AuthController 末尾（最后一个 `}` 前）追加：

```java
    @Operation(summary = "获取当前用户可见菜单列表（SYS-3）")
    @GetMapping("/menu")
    public Result<List<String>> menu() {
        return Result.success(authService.getMenuForCurrentUser());
    }
```

同时追加 import：

```java
import java.util.List;
```

- [ ] **Step 5: 运行菜单测试，确认全绿**

```bash
cd backend && mvn test -Dtest="SYS3UserTest#getMenu_adminRole_containsAllMenus+getMenu_userRole_excludesSensitiveMenus+getMenu_readonlyRole_minimalMenus+getMenu_logMenuDisabled_systemLogHidden" -q
```

期望：BUILD SUCCESS，4 个测试通过。

- [ ] **Step 6: 提交**

```bash
git add backend/src/test/java/com/powergateway/SYS3UserTest.java \
        backend/src/main/java/com/powergateway/service/AuthService.java \
        backend/src/main/java/com/powergateway/controller/AuthController.java
git commit -m "feat(SYS-3): add GET /api/auth/menu with role-based filtering (TDD, 4 tests)"
```

---

## Task 3: UserService + UserController（TDD）

**Files:**
- Modify: `backend/src/test/java/com/powergateway/SYS3UserTest.java`（追加用户 CRUD 测试）
- Create: `backend/src/main/java/com/powergateway/service/UserService.java`
- Create: `backend/src/main/java/com/powergateway/controller/UserController.java`

- [ ] **Step 1: 在 SYS3UserTest.java 末尾（最后一个 `}` 前）追加用户 CRUD 测试**

```java
    // ─── User CRUD ──────────────────────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("saveUser_新增_密码BCrypt存储且无明文")
    void saveUser_new_passwordBCryptEncrypted() throws Exception {
        String username = "newuser_" + System.currentTimeMillis();
        String body = "{\"username\":\"" + username + "\",\"password\":\"Abc123\",\"role\":\"user\",\"status\":1}";
        MvcResult r = mockMvc.perform(post("/api/user/save")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Long id = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
        SysUser saved = sysUserMapper.selectById(id);
        assertThat(saved.getPassword()).isNotEqualTo("Abc123");
        assertThat(encoder.matches("Abc123", saved.getPassword())).isTrue();
        sysUserMapper.deleteById(id);
    }

    @Test @Order(11)
    @DisplayName("saveUser_用户名重复_400")
    void saveUser_duplicateUsername_400() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"Abc123\",\"role\":\"user\",\"status\":1}";
        mockMvc.perform(post("/api/user/save")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(12)
    @DisplayName("saveUser_密码少于6位_400")
    void saveUser_shortPassword_400() throws Exception {
        String body = "{\"username\":\"shortpwd_user\",\"password\":\"123\",\"role\":\"user\",\"status\":1}";
        mockMvc.perform(post("/api/user/save")
                        .header("satoken", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(13)
    @DisplayName("deleteUser_删除自己_400")
    void deleteUser_selfDelete_400() throws Exception {
        // admin is currently logged in; get admin's DB id
        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, "admin"));
        mockMvc.perform(delete("/api/user/" + admin.getId())
                        .header("satoken", adminToken))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(14)
    @DisplayName("deleteUser_删除最后一个admin_400")
    void deleteUser_lastAdmin_400() throws Exception {
        // Login as user-role user (not admin), try to delete the only admin
        SysUser testUser = sysUserMapper.selectById(testUserRoleId);
        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + testUser.getUsername() + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andReturn();
        String userToken = JsonPath.read(loginRes.getResponse().getContentAsString(), "$.data.token");

        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, "admin"));
        mockMvc.perform(delete("/api/user/" + admin.getId())
                        .header("satoken", userToken))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test @Order(15)
    @DisplayName("listUser_返回UserVO无password字段")
    void listUser_responseHasNoPassword() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/user/list").header("satoken", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("\"password\"");
    }

    @Test @Order(16)
    @DisplayName("updateUser_修改角色_密码空时不覆盖")
    void updateUser_emptyPassword_passwordUnchanged() throws Exception {
        // Create user
        SysUser u = new SysUser();
        u.setUsername("updtest_" + System.currentTimeMillis());
        u.setPassword(encoder.encode("Orig@456"));
        u.setRole("user");
        u.setStatus(1);
        sysUserMapper.insert(u);

        try {
            // Update role only, leave password empty
            String body = "{\"id\":" + u.getId() + ",\"role\":\"readonly\",\"status\":1,\"password\":\"\"}";
            mockMvc.perform(post("/api/user/save")
                            .header("satoken", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200));

            SysUser updated = sysUserMapper.selectById(u.getId());
            assertThat(updated.getRole()).isEqualTo("readonly");
            // Password unchanged: original hash still matches
            assertThat(encoder.matches("Orig@456", updated.getPassword())).isTrue();
        } finally {
            sysUserMapper.deleteById(u.getId());
        }
    }
```

- [ ] **Step 2: 运行测试确认失败（UserController 未创建）**

```bash
cd backend && mvn test -Dtest="SYS3UserTest#saveUser_new_passwordBCryptEncrypted" -q 2>&1 | tail -5
```

期望：FAIL（404）

- [ ] **Step 3: 创建 UserService.java**

```java
package com.powergateway.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.SysUser;
import com.powergateway.model.dto.UserSaveRequest;
import com.powergateway.model.dto.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
    private static final List<String> VALID_ROLES = Arrays.asList("admin", "user", "readonly");

    public List<UserVO> list(String username, int page, int size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like(SysUser::getUsername, username);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return sysUserMapper.selectList(wrapper).stream()
                .map(UserVO::from)
                .collect(Collectors.toList());
    }

    public Long save(UserSaveRequest req) {
        if (req.getRole() != null && !VALID_ROLES.contains(req.getRole())) {
            throw new BusinessException(400, "角色无效，只允许：admin、user、readonly");
        }

        if (req.getId() == null) {
            // 新增
            if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
                throw new BusinessException(400, "用户名不能为空");
            }
            if (req.getPassword() == null || req.getPassword().length() < 6) {
                throw new BusinessException(400, "密码不能少于6位");
            }
            Long count = sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername().trim()));
            if (count > 0) {
                throw new BusinessException(400, "用户名已存在");
            }
            SysUser entity = new SysUser();
            entity.setUsername(req.getUsername().trim());
            entity.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));
            entity.setRole(req.getRole() != null ? req.getRole() : "user");
            entity.setStatus(req.getStatus() != null ? req.getStatus() : 1);
            sysUserMapper.insert(entity);
            return entity.getId();
        } else {
            // 更新
            SysUser existing = sysUserMapper.selectById(req.getId());
            if (existing == null) throw new BusinessException(404, "用户不存在");
            SysUser update = new SysUser();
            update.setId(req.getId());
            if (req.getPassword() != null && !req.getPassword().isEmpty()) {
                if (req.getPassword().length() < 6) {
                    throw new BusinessException(400, "密码不能少于6位");
                }
                update.setPassword(PASSWORD_ENCODER.encode(req.getPassword()));
            }
            if (req.getRole() != null) update.setRole(req.getRole());
            if (req.getStatus() != null) update.setStatus(req.getStatus());
            sysUserMapper.updateById(update);
            return req.getId();
        }
    }

    public void delete(Long id) {
        long currentUserId = StpUtil.getLoginIdAsLong();
        if (currentUserId == id) {
            throw new BusinessException(400, "不能删除当前登录账号");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) throw new BusinessException(404, "用户不存在");
        if ("admin".equals(user.getRole())) {
            Long adminCount = sysUserMapper.selectCount(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "admin"));
            if (adminCount <= 1) {
                throw new BusinessException(400, "至少保留一个管理员账号，无法删除");
            }
        }
        sysUserMapper.deleteById(id);
    }
}
```

- [ ] **Step 4: 创建 UserController.java**

```java
package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.UserSaveRequest;
import com.powergateway.model.dto.UserVO;
import com.powergateway.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（SYS-3）
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户 CRUD（SYS-3）")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    @Operation(summary = "用户列表（分页，密码脱敏）")
    public Result<List<UserVO>> list(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.list(username, page, size));
    }

    @PostMapping("/save")
    @Operation(summary = "新增/更新用户（id 为空=新增，密码空=不改）")
    public Result<Long> save(@RequestBody UserSaveRequest req) {
        return Result.success(userService.save(req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户（不能删自己，不能删最后一个 admin）")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }
}
```

- [ ] **Step 5: 运行全部 SYS3 测试（含菜单 + CRUD），确认通过**

```bash
cd backend && mvn test -Dtest=SYS3UserTest -q
```

期望：BUILD SUCCESS，10 个测试全绿。

- [ ] **Step 6: 运行全量测试确认无退化**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

期望：BUILD SUCCESS，268 + 10 = 278 个测试通过。

- [ ] **Step 7: 提交**

```bash
git add backend/src/test/java/com/powergateway/SYS3UserTest.java \
        backend/src/main/java/com/powergateway/service/UserService.java \
        backend/src/main/java/com/powergateway/controller/UserController.java
git commit -m "feat(SYS-3): add UserService and UserController with CRUD (TDD, 10 tests)"
```

---

## Task 4: 前端 userStore + auth API + 登录流程

**Files:**
- Modify: `frontend/src/store/user.js`
- Modify: `frontend/src/api/auth.js`
- Modify: `frontend/src/views/LoginView.vue`

- [ ] **Step 1: 修改 frontend/src/store/user.js**

完整替换文件内容：

```js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))
  const allowedMenus = ref(JSON.parse(localStorage.getItem('allowedMenus') || '[]'))

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value && userInfo.value.username ? userInfo.value.username : '')
  const role = computed(() => userInfo.value && userInfo.value.role ? userInfo.value.role : '')

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setAllowedMenus(menus) {
    allowedMenus.value = menus || []
    localStorage.setItem('allowedMenus', JSON.stringify(allowedMenus.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    allowedMenus.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('allowedMenus')
  }

  return {
    token,
    userInfo,
    allowedMenus,
    isLoggedIn,
    username,
    role,
    setToken,
    setUserInfo,
    setAllowedMenus,
    logout
  }
})
```

- [ ] **Step 2: 修改 frontend/src/api/auth.js — 追加 getMenuPermissions**

```js
import request from './request'

export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

export function logout() {
  return request.post('/auth/logout')
}

export function getMenuPermissions() {
  return request.get('/auth/menu')
}
```

- [ ] **Step 3: 修改 frontend/src/views/LoginView.vue — 登录后加载菜单权限**

在 `<script setup>` 中追加 import：

```js
import { getMenuPermissions } from '@/api/auth'
```

找到 `handleLogin` 函数中的：

```js
    userStore.setToken(res.token)
    userStore.setUserInfo(res.userInfo)
    ElMessage.success('登录成功')
    router.push('/')
```

改为：

```js
    userStore.setToken(res.token)
    userStore.setUserInfo(res.userInfo)
    try {
      const menus = await getMenuPermissions()
      userStore.setAllowedMenus(menus)
    } catch (e) {
      userStore.setAllowedMenus([])
    }
    ElMessage.success('登录成功')
    router.push('/')
```

- [ ] **Step 4: 验证前端构建**

```bash
cd frontend && npm run build 2>&1 | tail -3
```

期望：`✓ built in ...`，无错误。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/store/user.js \
        frontend/src/api/auth.js \
        frontend/src/views/LoginView.vue
git commit -m "feat(SYS-3): load menu permissions after login, add allowedMenus to userStore"
```

---

## Task 5: 前端 SideMenu.vue 动态菜单 + 路由守卫

**Files:**
- Modify: `frontend/src/components/layout/SideMenu.vue`
- Modify: `frontend/src/router/index.js`

- [ ] **Step 1: 完整替换 frontend/src/components/layout/SideMenu.vue**

```vue
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
      <el-menu-item v-if="can('/dashboard')" index="/dashboard">
        <el-icon><HomeFilled /></el-icon>
        <template #title>系统概览</template>
      </el-menu-item>

      <!-- 接口转换配置（模块一） -->
      <el-sub-menu v-if="hasConvert" index="convert">
        <template #title>
          <el-icon><Switch /></el-icon>
          <span>接口转换配置</span>
        </template>
        <el-menu-item v-if="can('/convert/format')" index="/convert/format">报文格式转换</el-menu-item>
        <el-menu-item v-if="can('/convert/field-mapping')" index="/convert/field-mapping">字段映射配置</el-menu-item>
        <el-menu-item v-if="can('/convert/field-process')" index="/convert/field-process">字段加工配置</el-menu-item>
        <el-menu-item v-if="can('/convert/channel')" index="/convert/channel">渠道模板管理</el-menu-item>
        <el-menu-item v-if="can('/convert/port-route')" index="/convert/port-route">端口分发路由</el-menu-item>
        <el-menu-item v-if="can('/convert/template')" index="/convert/template">转换模板管理</el-menu-item>
      </el-sub-menu>

      <!-- 可视化接口开发（模块二） -->
      <el-sub-menu v-if="hasInterface" index="interface">
        <template #title>
          <el-icon><Monitor /></el-icon>
          <span>可视化接口开发</span>
        </template>
        <el-menu-item v-if="can('/interface/db')" index="/interface/db">数据库连接管理</el-menu-item>
        <el-menu-item v-if="can('/interface/table')" index="/interface/table">表结构管理</el-menu-item>
        <el-menu-item v-if="can('/interface/dev')" index="/interface/dev">查询接口配置</el-menu-item>
        <el-menu-item v-if="can('/interface/insert')" index="/interface/insert">插入接口配置</el-menu-item>
        <el-menu-item v-if="can('/interface/update')" index="/interface/update">修改接口配置</el-menu-item>
        <el-menu-item v-if="can('/interface/delete')" index="/interface/delete">删除接口配置</el-menu-item>
        <el-menu-item v-if="can('/interface/list')" index="/interface/list">接口管理</el-menu-item>
        <el-menu-item v-if="can('/interface/shard')" index="/interface/shard">分库分表配置</el-menu-item>
        <el-menu-item v-if="can('/interface/formula')" index="/interface/formula">字段公式管理</el-menu-item>
        <el-menu-item v-if="can('/interface/cache')" index="/interface/cache">缓存查询管理</el-menu-item>
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

      <!-- 辅助工具 -->
      <el-sub-menu v-if="hasTools" index="tools">
        <template #title>
          <el-icon><Tools /></el-icon>
          <span>辅助工具</span>
        </template>
        <el-menu-item v-if="can('/tools/debug')" index="/tools/debug">报文调试</el-menu-item>
        <el-menu-item v-if="can('/tools/swagger')" index="/tools/swagger">接口文档</el-menu-item>
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

const CONVERT_PATHS  = ['/convert/format', '/convert/field-mapping', '/convert/field-process',
                        '/convert/channel', '/convert/port-route', '/convert/template']
const INTERFACE_PATHS = ['/interface/db', '/interface/table', '/interface/dev',
                         '/interface/insert', '/interface/update', '/interface/delete',
                         '/interface/list', '/interface/shard', '/interface/formula', '/interface/cache']
const SYSTEM_PATHS   = ['/system/log', '/system/stats', '/system/user', '/system/config']
const TOOLS_PATHS    = ['/tools/debug', '/tools/swagger']

const hasConvert   = computed(() => CONVERT_PATHS.some(function(p) { return can(p) }))
const hasInterface = computed(() => INTERFACE_PATHS.some(function(p) { return can(p) }))
const hasSystem    = computed(() => SYSTEM_PATHS.some(function(p) { return can(p) }))
const hasTools     = computed(() => TOOLS_PATHS.some(function(p) { return can(p) }))
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
```

- [ ] **Step 2: 修改 frontend/src/router/index.js — 补充路由守卫 + 替换 /system/user**

**2a.** 将：

```js
        {
          path: 'system/user',
          name: 'UserManage',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '用户权限管理' }
        },
```

改为：

```js
        {
          path: 'system/user',
          name: 'UserManage',
          component: () => import('@/views/system/UserList.vue'),
          meta: { title: '用户权限管理' }
        },
```

**2b.** 在现有的 `router.beforeEach` 中，在 `next()` 的 else 分支前插入菜单权限拦截：

找到：
```js
  } else {
    next()
  }
```

改为：
```js
  } else {
    // 已登录且菜单权限已加载时，拦截无权路由的直接 URL 访问
    const menus = userStore.allowedMenus
    if (menus.length > 0
        && to.meta.requiresAuth !== false
        && to.path !== '/dashboard'
        && !menus.includes(to.path)) {
      next('/dashboard')
    } else {
      next()
    }
  }
```

- [ ] **Step 3: 验证前端构建**

```bash
cd frontend && npm run build 2>&1 | tail -3
```

期望：`✓ built in ...`，无错误。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/layout/SideMenu.vue \
        frontend/src/router/index.js
git commit -m "feat(SYS-3): dynamic SideMenu with allowedMenus v-if, route guard, wire UserList route"
```

---

## Task 6: 前端 api/user.js + UserList.vue

**Files:**
- Create: `frontend/src/api/user.js`
- Create: `frontend/src/views/system/UserList.vue`

- [ ] **Step 1: 创建 frontend/src/api/user.js**

```js
import request from '@/api/request'

export function listUsers(username, page = 1, size = 20) {
  return request.get('/user/list', { params: { username, page, size } })
}

export function saveUser(data) {
  return request.post('/user/save', data)
}

export function deleteUser(id) {
  return request.delete(`/user/${id}`)
}
```

- [ ] **Step 2: 创建 frontend/src/views/system/UserList.vue**

```vue
<template>
  <div class="user-list">
    <div class="toolbar">
      <el-input v-model="searchUsername" placeholder="搜索用户名" clearable
        style="width: 240px" @keyup.enter="loadList" @clear="loadList" />
      <el-button type="primary" @click="loadList">查询</el-button>
      <el-button type="success" @click="openForm(null)">新建用户</el-button>
    </div>

    <el-table :data="list" stripe border v-loading="loading" style="margin-top: 16px">
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openForm(row)">编辑</el-button>
          <el-popconfirm title="确认删除该用户？" @confirm="handleDelete(row)">
            <template #reference>
              <el-button size="small" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; text-align: right"
      @current-change="loadList"
    />

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新建用户'" width="480px" @close="resetForm">
      <el-form :model="form" label-width="80px" style="padding-right: 16px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="form.id ? '留空则不修改密码' : '至少6位'" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="管理员 (admin)" value="admin" />
            <el-option label="普通用户 (user)" value="user" />
            <el-option label="只读用户 (readonly)" value="readonly" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listUsers, saveUser, deleteUser } from '@/api/user'

const list = ref([])
const loading = ref(false)
const searchUsername = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const formVisible = ref(false)
const saving = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return { id: null, username: '', password: '', role: 'user', statusBool: true }
}

function roleTagType(role) {
  return { admin: 'danger', user: '', readonly: 'info' }[role] || 'info'
}

function roleLabel(role) {
  return { admin: '管理员', user: '普通用户', readonly: '只读' }[role] || role
}

async function loadList() {
  loading.value = true
  try {
    const res = await listUsers(searchUsername.value || undefined, page.value, pageSize.value)
    list.value = res || []
    total.value = list.value.length
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = emptyForm()
  if (row) {
    form.value.id = row.id
    form.value.username = row.username
    form.value.role = row.role
    form.value.statusBool = row.status === 1
  }
  formVisible.value = true
}

function resetForm() {
  form.value = emptyForm()
}

async function handleSave() {
  if (!form.value.username.trim() && !form.value.id) {
    ElMessage.error('请填写用户名')
    return
  }
  if (!form.value.role) {
    ElMessage.error('请选择角色')
    return
  }
  saving.value = true
  try {
    await saveUser({
      id: form.value.id,
      username: form.value.username,
      password: form.value.password || '',
      role: form.value.role,
      status: form.value.statusBool ? 1 : 0
    })
    ElMessage.success(form.value.id ? '更新成功' : '创建成功')
    formVisible.value = false
    await loadList()
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e) {
    // 错误已由 request.js 拦截器统一提示
  }
}

onMounted(loadList)
</script>

<style scoped>
.user-list { padding: 16px; }
.toolbar { display: flex; gap: 8px; align-items: center; }
</style>
```

- [ ] **Step 3: 验证前端构建**

```bash
cd frontend && npm run build 2>&1 | tail -3
```

期望：`✓ built in ...`，无错误。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/api/user.js \
        frontend/src/views/system/UserList.vue
git commit -m "feat(SYS-3): add UserList.vue and user API"
```

---

## Task 7: 全量验证 + CLAUDE.md 更新 + git push

- [ ] **Step 1: 运行后端全量测试**

```bash
cd backend && mvn test -q 2>&1 | tail -5
```

期望：`Tests run: 278, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS。

- [ ] **Step 2: 验证前端构建**

```bash
cd frontend && npm run build 2>&1 | tail -3
```

期望：`✓ built in ...`，无错误。

- [ ] **Step 3: 更新 CLAUDE.md 状态行**

将根目录 `CLAUDE.md` 中的状态行：

```
**当前状态：阶段一全部完成（P0-1 ～ P0-4），阶段二全部完成（M1-1 ～ M1-7），阶段三全部完成（M2-1、M2-2、M2-9、M2-3、M2-4、M2-5、M2-6、M2-7），阶段四 M2-10、M2-8 完成，共 268 个测试全绿。下一阶段：阶段四剩余（SYS-1 ～ SYS-4）。**
```

改为：

```
**当前状态：阶段一全部完成（P0-1 ～ P0-4），阶段二全部完成（M1-1 ～ M1-7），阶段三全部完成（M2-1、M2-2、M2-9、M2-3、M2-4、M2-5、M2-6、M2-7），阶段四 M2-10、M2-8、SYS-3 完成，共 278 个测试全绿。下一阶段：阶段四剩余（SYS-1、SYS-2、SYS-4）。**
```

- [ ] **Step 4: 提交并推送**

```bash
git add CLAUDE.md
git commit -m "docs: mark SYS-3 complete, 278 tests passing"
git push
```
