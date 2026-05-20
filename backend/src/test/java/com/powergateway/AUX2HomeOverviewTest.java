package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AUX-2 首页系统概览测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AUX2HomeOverviewTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    private String token;

    @BeforeAll
    void login() throws Exception {
        MvcResult lr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(lr.getResponse().getContentAsString(), "$.data.token");
    }

    @BeforeEach
    void cleanTables() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>().isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>().isNotNull(PerfAlert::getId));
        interfaceConfigMapper.delete(new LambdaQueryWrapper<InterfaceConfig>().isNotNull(InterfaceConfig::getId));
    }

    // ---------- 辅助方法 ----------

    private long insertInterface(String name, String status) {
        InterfaceConfig c = new InterfaceConfig();
        c.setName(name);
        c.setStatus(status);
        c.setType("SELECT");
        c.setDbConnectionId(1L);
        c.setConfigJson("{}");
        c.setCreator("test");
        interfaceConfigMapper.insert(c);
        return c.getId();
    }

    private void insertPerfStat(Long interfaceId, String opType, int costMs, int success, int hoursAgo) {
        PerfStatRecord r = new PerfStatRecord();
        r.setInterfaceId(interfaceId);
        r.setOpType(opType);
        r.setCostMs(costMs);
        r.setSuccess(success);
        r.setStatTime(LocalDateTime.now().minusHours(hoursAgo));
        perfStatMapper.insert(r);
    }

    private void insertAlert(String type, double value, double threshold, String msg, int resolved, int hoursAgo) {
        PerfAlert a = new PerfAlert();
        a.setAlertType(type);
        a.setAlertValue(BigDecimal.valueOf(value));
        a.setThreshold(BigDecimal.valueOf(threshold));
        a.setMessage(msg);
        a.setCheckTime(LocalDateTime.now().minusHours(hoursAgo));
        a.setResolved(resolved);
        perfAlertMapper.insert(a);
    }

    // ---------- 测试 ----------

    @Test
    void overview接口_未携带token_返回401业务码() throws Exception {
        // Sa-Token 拦截器：HTTP 200 + JSON code=401（见 SaTokenConfig.preHandle）
        mockMvc.perform(get("/api/home/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void overview_interfaceStats_按status分组计数() throws Exception {
        insertInterface("intf-draft-1", "draft");
        insertInterface("intf-draft-2", "draft");
        insertInterface("intf-pub-1",   "published");
        insertInterface("intf-pub-2",   "published");
        insertInterface("intf-pub-3",   "published");
        insertInterface("intf-dis-1",   "disabled");

        mockMvc.perform(get("/api/home/overview").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.interfaceStats.total").value(6))
                .andExpect(jsonPath("$.data.interfaceStats.draft").value(2))
                .andExpect(jsonPath("$.data.interfaceStats.published").value(3))
                .andExpect(jsonPath("$.data.interfaceStats.disabled").value(1));
    }

    @Test
    void overview_无接口时_interfaceStats全0() throws Exception {
        mockMvc.perform(get("/api/home/overview").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interfaceStats.total").value(0))
                .andExpect(jsonPath("$.data.interfaceStats.draft").value(0))
                .andExpect(jsonPath("$.data.interfaceStats.published").value(0))
                .andExpect(jsonPath("$.data.interfaceStats.disabled").value(0));
    }

    @Test
    void overview_callStats_汇总totalCalls_successRate_avgCostMs() throws Exception {
        long id = insertInterface("intf", "published");
        // 3 成功 (100/200/300ms) + 1 失败 (50ms) → total=4, fail=1, successRate=75.0
        insertPerfStat(id, "SELECT", 100, 1, 0);
        insertPerfStat(id, "SELECT", 200, 1, 0);
        insertPerfStat(id, "SELECT", 300, 1, 0);
        insertPerfStat(id, "SELECT", 50,  0, 0);

        mockMvc.perform(get("/api/home/overview").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callStats.totalCalls").value(4))
                .andExpect(jsonPath("$.data.callStats.successRate").value(75.0))
                .andExpect(jsonPath("$.data.callStats.avgCostMs").value(org.hamcrest.Matchers.greaterThan(100)));
    }

    @Test
    void overview_无perfStat_callStats全0_successRate0() throws Exception {
        mockMvc.perform(get("/api/home/overview").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callStats.totalCalls").value(0))
                .andExpect(jsonPath("$.data.callStats.successRate").value(0))
                .andExpect(jsonPath("$.data.callStats.avgCostMs").value(0));
    }

    @Test
    void overview_无启用缓存接口_cacheHitRate为null() throws Exception {
        insertInterface("intf-no-cache", "published");
        mockMvc.perform(get("/api/home/overview").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callStats.cacheHitRate").value(org.hamcrest.Matchers.nullValue()));
    }
}
