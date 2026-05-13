package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogService 基础写入/查询测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogServiceTest {

    @Autowired private SysLogService sysLogService;
    @Autowired private SysLogMapper sysLogMapper;

    @AfterAll
    void cleanup() {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .like(SysLog::getOperator, "test_svc_"));
    }

    @Test
    @Order(1)
    void enqueue后flushForTest_应写入数据库() {
        SysLog log = new SysLog();
        log.setModule("测试模块");
        log.setAction("测试动作");
        log.setOperator("test_svc_01");
        log.setOpIp("127.0.0.1");
        log.setOpTime(LocalDateTime.now());
        log.setLevel("INFO");
        log.setCostMs(10);

        sysLogService.enqueue(log);
        sysLogService.flushForTest();

        List<SysLog> result = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getOperator, "test_svc_01"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getModule()).isEqualTo("测试模块");
        assertThat(result.get(0).getLevel()).isEqualTo("INFO");
    }

    @Test
    @Order(2)
    void list_按module过滤_只返回匹配记录() {
        insertLog("test_svc_02", "模块A", "INFO");
        insertLog("test_svc_02", "模块B", "INFO");

        IPage<SysLog> page = sysLogService.list("test_svc_02", "模块A", null, null, null, 1, 10);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getModule()).isEqualTo("模块A");
    }

    @Test
    @Order(3)
    void list_按level过滤_只返回ERROR记录() {
        insertLog("test_svc_03", "模块X", "INFO");
        insertLog("test_svc_03", "模块X", "ERROR");

        IPage<SysLog> page = sysLogService.list("test_svc_03", null, "ERROR", null, null, 1, 10);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getLevel()).isEqualTo("ERROR");
    }

    private void insertLog(String operator, String module, String level) {
        SysLog log = new SysLog();
        log.setModule(module);
        log.setAction("测试");
        log.setOperator(operator);
        log.setOpIp("127.0.0.1");
        log.setOpTime(LocalDateTime.now());
        log.setLevel(level);
        log.setCostMs(5);
        sysLogMapper.insert(log);
    }
}
