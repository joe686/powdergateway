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
        SysConfig existing = sysConfigMapper.selectById(com.powergateway.config.MenuPermission.LOG_MENU_CONFIG_KEY);
        SysConfig cfg = new SysConfig();
        cfg.setConfigKey(com.powergateway.config.MenuPermission.LOG_MENU_CONFIG_KEY);
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
    @DisplayName("updateUser_密码留空_不覆盖原密码")
    void updateUser_emptyPassword_passwordUnchanged() throws Exception {
        SysUser u = new SysUser();
        u.setUsername("updtest_" + System.currentTimeMillis());
        u.setPassword(encoder.encode("Orig@456"));
        u.setRole("user");
        u.setStatus(1);
        sysUserMapper.insert(u);

        try {
            String body = "{\"id\":" + u.getId() + ",\"role\":\"readonly\",\"status\":1,\"password\":\"\"}";
            mockMvc.perform(post("/api/user/save")
                            .header("satoken", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(200));

            SysUser updated = sysUserMapper.selectById(u.getId());
            assertThat(updated.getRole()).isEqualTo("readonly");
            assertThat(encoder.matches("Orig@456", updated.getPassword())).isTrue();
        } finally {
            sysUserMapper.deleteById(u.getId());
        }
    }
}
