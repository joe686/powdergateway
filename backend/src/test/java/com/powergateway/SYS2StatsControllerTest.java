package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.SysConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-2 StatsController 接口测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS2StatsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    private String token;

    @BeforeAll
    void setup() throws Exception {
        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");

        // 清理并插入测试数据
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>().isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>().isNotNull(PerfAlert::getId));

        for (int i = 0; i < 3; i++) {
            PerfStatRecord r = new PerfStatRecord();
            r.setInterfaceId(1L);
            r.setOpType("SELECT");
            r.setCostMs(100 + i * 50);
            r.setSuccess(1);
            r.setStatTime(LocalDateTime.now());
            perfStatMapper.insert(r);
        }

        PerfAlert alert = new PerfAlert();
        alert.setAlertType("FAIL_RATE");
        alert.setAlertValue(new BigDecimal("10.00"));
        alert.setThreshold(new BigDecimal("5.00"));
        alert.setMessage("测试告警");
        alert.setCheckTime(LocalDateTime.now());
        alert.setResolved(0);
        perfAlertMapper.insert(alert);
    }

    @AfterAll
    void cleanup() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>().isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>().isNotNull(PerfAlert::getId));
    }

    @Test
    @Order(1)
    void summary接口_today维度_返回正确结构() throws Exception {
        mockMvc.perform(get("/api/stats/summary")
                .header("satoken", token)
                .param("dimension", "today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.timeline").isArray())
                .andExpect(jsonPath("$.data.successCounts").isArray())
                .andExpect(jsonPath("$.data.failCounts").isArray())
                .andExpect(jsonPath("$.data.avgCostMs").isArray());
    }

    @Test
    @Order(2)
    void summary接口_今日有数据_timeline非空() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/stats/summary")
                .header("satoken", token)
                .param("dimension", "today"))
                .andReturn();
        int timelineSize = ((java.util.List<?>) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.timeline")).size();
        assertThat(timelineSize).isGreaterThan(0);
    }

    @Test
    @Order(3)
    void alerts接口_返回分页告警列表() throws Exception {
        mockMvc.perform(get("/api/stats/alerts")
                .header("satoken", token)
                .param("page", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(4)
    void alertConfig接口_更新阈值后sys_config已修改() throws Exception {
        mockMvc.perform(put("/api/stats/alert-config")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"failRate\":10,\"responseMs\":2000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        SysConfig failRateCfg = sysConfigMapper.selectById("alert_fail_rate");
        SysConfig responseCfg = sysConfigMapper.selectById("alert_response_ms");
        assertThat(failRateCfg.getConfigValue()).isEqualTo("10.0");
        assertThat(responseCfg.getConfigValue()).isEqualTo("2000");
    }
}
