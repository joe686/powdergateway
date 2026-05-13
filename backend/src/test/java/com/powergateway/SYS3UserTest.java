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
}
