package com.powergateway;

import cn.dev33.satoken.stp.StpUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UXAThemePrefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StpUtil.logout();
    }

    @AfterEach
    void tearDown() {
        StpUtil.logout();
    }

    @Test
    void getThemePref_未登录_返回401() throws Exception {
        mockMvc.perform(get("/api/user/theme-pref"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void getThemePref_已登录_未设置_返回null() throws Exception {
        StpUtil.login(1L);          // admin 用户
        mockMvc.perform(get("/api/user/theme-pref")
                       .header("satoken", StpUtil.getTokenValue()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.code").value(200))
               .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
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
