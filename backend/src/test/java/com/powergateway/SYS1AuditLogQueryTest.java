package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.service.SysLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-1 SQL 审计日志查询测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1AuditLogQueryTest {

    @Autowired private SysLogService sysLogService;
    @Autowired private SqlAuditLogMapper auditLogMapper;

    @BeforeAll
    void insertTestData() {
        SqlAuditLog log1 = new SqlAuditLog();
        log1.setInterfaceId(1L);
        log1.setSqlText("INSERT INTO orders VALUES(1)");
        log1.setOpType("INSERT");
        log1.setOperator(null);
        log1.setOpIp(null);
        log1.setOpTime(LocalDateTime.now());
        log1.setTargetDb("order_db");
        log1.setTargetTable("orders");
        log1.setResult("SUCCESS");
        auditLogMapper.insert(log1);

        SqlAuditLog log2 = new SqlAuditLog();
        log2.setInterfaceId(2L);
        log2.setSqlText("DELETE FROM orders WHERE id=1");
        log2.setOpType("DELETE");
        log2.setOperator("admin");
        log2.setOpIp("192.168.1.1");
        log2.setOpTime(LocalDateTime.now());
        log2.setTargetDb("order_db");
        log2.setTargetTable("orders");
        log2.setResult("FAIL");
        log2.setErrorMsg("Connection timeout");
        auditLogMapper.insert(log2);
    }

    @AfterAll
    void cleanup() {
        auditLogMapper.delete(new LambdaQueryWrapper<SqlAuditLog>()
                .eq(SqlAuditLog::getTargetDb, "order_db"));
    }

    @Test
    void 按opType过滤_只返回INSERT记录() {
        IPage<SqlAuditLog> page = sysLogService.listAuditLogs("INSERT", null, null, null, 1, 10);
        assertThat(page.getRecords()).anyMatch(l -> "INSERT".equals(l.getOpType()));
        assertThat(page.getRecords()).noneMatch(l -> "DELETE".equals(l.getOpType()));
    }

    @Test
    void 按result过滤_只返回FAIL记录() {
        IPage<SqlAuditLog> page = sysLogService.listAuditLogs(null, "FAIL", null, null, 1, 10);
        assertThat(page.getRecords()).isNotEmpty();
        assertThat(page.getRecords()).allMatch(l -> "FAIL".equals(l.getResult()));
    }

    @Test
    void null的operator和ip字段_正常返回不报错() {
        IPage<SqlAuditLog> page = sysLogService.listAuditLogs("INSERT", null, null, null, 1, 10);
        assertThat(page.getRecords()).isNotEmpty();
        SqlAuditLog nullOperatorLog = page.getRecords().stream()
                .filter(l -> l.getOperator() == null).findFirst().orElse(null);
        assertThat(nullOperatorLog).isNotNull();
    }
}
