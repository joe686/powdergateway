package com.powergateway;

import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.SysUserMapper;
import com.powergateway.model.SysUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BUG-011 / BUG-012 修复验证测试
 * - BUG-011：验证 /api/sys-config/list、/api/sys-log/list、/api/sql-audit-log/list、
 *            /api/perf-stat/list、/api/perf-stat/stats 五个端点可访问
 * - BUG-012：验证 admin 用户 createTime 不为 null
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("BUG-011/BUG-012 修复验证")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BUG011_012_FixTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SysUserMapper sysUserMapper;

    private String token;

    @BeforeAll
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    // ─── BUG-011：验证 5 个新增端点可访问 ──────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("BUG-011: GET /api/sys-config/list 返回 200")
    void sysConfigList_返回200() throws Exception {
        mockMvc.perform(get("/api/sys-config/list").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(2)
    @DisplayName("BUG-011: GET /api/sys-log/list 返回 200")
    void sysLogList_返回200() throws Exception {
        mockMvc.perform(get("/api/sys-log/list").header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    @DisplayName("BUG-011: GET /api/sql-audit-log/list 返回 200")
    void sqlAuditLogList_返回200() throws Exception {
        mockMvc.perform(get("/api/sql-audit-log/list").header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("BUG-011: GET /api/perf-stat/list 返回 200")
    void perfStatList_返回200() throws Exception {
        mockMvc.perform(get("/api/perf-stat/list").header("satoken", token)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(5)
    @DisplayName("BUG-011: GET /api/perf-stat/stats 返回 200")
    void perfStatStats_返回200() throws Exception {
        mockMvc.perform(get("/api/perf-stat/stats").header("satoken", token)
                        .param("dimension", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ─── BUG-012：验证 admin 用户 createTime 不为 null ──────────────────────────

    @Test
    @Order(10)
    @DisplayName("BUG-012: admin 用户 createTime 不为 null")
    void adminCreateTime_不为null() {
        SysUser admin = sysUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, "admin"));
        assertThat(admin).isNotNull();
        assertThat(admin.getCreateTime()).as("admin 用户的 createTime 不应为 null（BUG-012）").isNotNull();
    }
}
