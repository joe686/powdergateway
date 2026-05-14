package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.PerfAlertMapper;
import com.powergateway.dao.PerfStatMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.job.PerfAlertJob;
import com.powergateway.model.PerfAlert;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.model.SysConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-2 PerfAlertJob 告警检查测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS2PerfAlertJobTest {

    @Autowired private PerfAlertJob perfAlertJob;
    @Autowired private PerfStatMapper perfStatMapper;
    @Autowired private PerfAlertMapper perfAlertMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @BeforeEach
    void clearTestData() {
        perfStatMapper.delete(new LambdaQueryWrapper<PerfStatRecord>()
                .isNotNull(PerfStatRecord::getId));
        perfAlertMapper.delete(new LambdaQueryWrapper<PerfAlert>()
                .isNotNull(PerfAlert::getId));
    }

    private void insertStat(int success, int costMs) {
        PerfStatRecord r = new PerfStatRecord();
        r.setInterfaceId(999L);
        r.setOpType("SELECT");
        r.setCostMs(costMs);
        r.setSuccess(success);
        r.setStatTime(LocalDateTime.now());
        // 插入时间早于 checkAndAlert() 内的 to=now，保证 stat_time < to 查询条件成立
        perfStatMapper.insert(r);
    }

    @Test
    @Order(1)
    void 失败率超阈值_写入FAIL_RATE告警() {
        // 设置阈值 5%
        SysConfig cfg = sysConfigMapper.selectById("alert_fail_rate");
        cfg.setConfigValue("5");
        sysConfigMapper.updateById(cfg);

        // 插入 10 条记录，4条失败（40%，超过5%阈值）
        for (int i = 0; i < 6; i++) insertStat(1, 50);
        for (int i = 0; i < 4; i++) insertStat(0, 50);

        perfAlertJob.checkAndAlert();

        List<PerfAlert> alerts = perfAlertMapper.selectList(
                new LambdaQueryWrapper<PerfAlert>().eq(PerfAlert::getAlertType, "FAIL_RATE"));
        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0).getAlertValue().doubleValue()).isGreaterThan(5.0);
    }

    @Test
    @Order(2)
    void 平均响应时间超阈值_写入AVG_RESPONSE告警() {
        // 设置阈值 100ms
        SysConfig cfg = sysConfigMapper.selectById("alert_response_ms");
        cfg.setConfigValue("100");
        sysConfigMapper.updateById(cfg);

        // 插入5条，平均 500ms，超过100ms阈值
        for (int i = 0; i < 5; i++) insertStat(1, 500);

        perfAlertJob.checkAndAlert();

        List<PerfAlert> alerts = perfAlertMapper.selectList(
                new LambdaQueryWrapper<PerfAlert>().eq(PerfAlert::getAlertType, "AVG_RESPONSE"));
        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0).getAlertValue().doubleValue()).isGreaterThan(100.0);
    }

    @Test
    @Order(3)
    void 无数据时_不写告警() {
        perfAlertJob.checkAndAlert();
        long count = perfAlertMapper.selectCount(null);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @Order(4)
    void 指标正常时_不写告警() {
        for (int i = 0; i < 10; i++) insertStat(1, 50);

        perfAlertJob.checkAndAlert();

        long count = perfAlertMapper.selectCount(null);
        assertThat(count).isEqualTo(0);
    }
}
