package com.powergateway;

import com.powergateway.config.SysAppInfoInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-003 · v0.3.1 Task 2 · AppInfoController + SysAppInfoInitializer 集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CR003AppInfoTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/app-info · 免登可访问 · 返 version/author/releaseNote/currentDate")
    void appInfo_免登访问_返完整字段() throws Exception {
        mockMvc.perform(get("/api/app-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.version").value(SysAppInfoInitializer.CURRENT_VERSION))
                .andExpect(jsonPath("$.data.author").value("光斓"))
                .andExpect(jsonPath("$.data.releaseNote").value("当前仅为测试版本"))
                .andExpect(jsonPath("$.data.currentDate").isString())
                .andExpect(jsonPath("$.data.buildTime").isString());
    }

    @Test
    @DisplayName("currentDate 走中文格式:YYYY年M月D日")
    void currentDate_中文格式() throws Exception {
        mockMvc.perform(get("/api/app-info"))
                .andExpect(jsonPath("$.data.currentDate")
                        .value(org.hamcrest.Matchers.matchesRegex("\\d{4}年\\d{1,2}月\\d{1,2}日")));
    }

    @Test
    @DisplayName("SysAppInfoInitializer @PostConstruct 已插入 v0.3.1 记录")
    void initializer_已插入() throws Exception {
        // 依赖 Spring 上下文启动时的自动 upsert · 读到 version=v0.3.1 即验证
        mockMvc.perform(get("/api/app-info"))
                .andExpect(jsonPath("$.data.version").value("v0.3.1"));
    }
}
