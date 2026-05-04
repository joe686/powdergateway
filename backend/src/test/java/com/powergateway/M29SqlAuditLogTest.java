package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.aop.AuditLog;
import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.job.AuditLogCleanupJob;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M2-9 SQL 审计日志验收测试
 * 验收标准：
 * 1. 执行一次写操作后，审计库中有对应记录
 * 2. 执行失败时 result=FAIL 且有错误信息
 * 3. 定时清理任务可正确删除过期记录
 */
@SpringBootTest
@ActiveProfiles("test")
class M29SqlAuditLogTest {

    /** 注册一个带 @AuditLog 的测试桩 Bean，用于验证 AOP 切面 */
    @TestConfiguration
    static class TestConfig {
        @Bean
        TestAuditableStub testAuditableStub() {
            return new TestAuditableStub();
        }
    }

    /** 测试桩：模拟未来执行器方法（INSERT/UPDATE/DELETE 执行器均会标注 @AuditLog） */
    public static class TestAuditableStub {
        @AuditLog
        public void executeOk() {
            // 正常执行，不抛异常
        }

        @AuditLog
        public void executeFail() {
            throw new RuntimeException("模拟 SQL 执行失败");
        }
    }

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SqlAuditLogMapper auditLogMapper;

    @Autowired
    private AuditLogCleanupJob cleanupJob;

    @Autowired
    private TestAuditableStub testStub;

    @BeforeEach
    void setUp() {
        // 每次测试前清空审计表，避免数据污染
        auditLogMapper.delete(new QueryWrapper<>());
        // 清理线程上下文（防止上次测试残留）
        AuditContextHolder.clear();
    }

    // ─── 1. 直接入队写入 ───────────────────────────────────────────────────────

    @Test
    void 直接入队_消费后写入审计库() throws InterruptedException {
        SqlAuditLog log = new SqlAuditLog();
        log.setInterfaceId(1L);
        log.setSqlText("INSERT INTO orders VALUES (1, 'test')");
        log.setOpType("INSERT");
        log.setOperator("admin");
        log.setOpIp("127.0.0.1");
        log.setOpTime(LocalDateTime.now());
        log.setTargetDb("business_db");
        log.setTargetTable("orders");
        log.setResult("SUCCESS");

        auditLogService.enqueue(log);
        Thread.sleep(500); // 等待异步消费

        List<SqlAuditLog> logs = auditLogMapper.selectList(null);
        assertEquals(1, logs.size());
        assertEquals("INSERT", logs.get(0).getOpType());
        assertEquals("SUCCESS", logs.get(0).getResult());
        assertEquals("admin", logs.get(0).getOperator());
    }

    // ─── 2. AOP 拦截 - 成功路径 ──────────────────────────────────────────────

    @Test
    void AOP拦截_成功路径_写入SUCCESS记录() throws InterruptedException {
        AuditContextHolder.set(new AuditContext()
                .setInterfaceId(2L)
                .setSqlText("UPDATE orders SET status=1 WHERE id=1")
                .setOpType("UPDATE")
                .setTargetDb("business_db")
                .setTargetTable("orders"));

        testStub.executeOk(); // AOP 拦截，执行成功
        Thread.sleep(500);

        List<SqlAuditLog> logs = auditLogMapper.selectList(null);
        assertEquals(1, logs.size());
        SqlAuditLog saved = logs.get(0);
        assertEquals("UPDATE", saved.getOpType());
        assertEquals("SUCCESS", saved.getResult());
        assertEquals(2L, saved.getInterfaceId());
        assertNull(saved.getErrorMsg());
    }

    // ─── 3. AOP 拦截 - 失败路径 ──────────────────────────────────────────────

    @Test
    void AOP拦截_失败路径_写入FAIL记录并保留异常信息() throws InterruptedException {
        AuditContextHolder.set(new AuditContext()
                .setInterfaceId(3L)
                .setSqlText("DELETE FROM orders WHERE id=1")
                .setOpType("DELETE")
                .setTargetDb("business_db")
                .setTargetTable("orders"));

        assertThrows(RuntimeException.class, () -> testStub.executeFail());
        Thread.sleep(500);

        List<SqlAuditLog> logs = auditLogMapper.selectList(null);
        assertEquals(1, logs.size());
        SqlAuditLog saved = logs.get(0);
        assertEquals("DELETE", saved.getOpType());
        assertEquals("FAIL", saved.getResult());
        assertNotNull(saved.getErrorMsg());
        assertTrue(saved.getErrorMsg().contains("模拟 SQL 执行失败"));
    }

    // ─── 4. AOP 执行后自动清理 ThreadLocal ──────────────────────────────────

    @Test
    void AOP执行后ThreadLocal已清理() throws InterruptedException {
        AuditContextHolder.set(new AuditContext()
                .setInterfaceId(4L)
                .setSqlText("INSERT INTO t VALUES (1)")
                .setOpType("INSERT")
                .setTargetDb("db")
                .setTargetTable("t"));

        testStub.executeOk();

        // AOP finally 块应清理上下文
        assertNull(AuditContextHolder.get());
    }

    // ─── 5. 带 beforeSnapshot 的审计记录 ─────────────────────────────────────

    @Test
    void 带before_snapshot的审计记录_正确保存() throws InterruptedException {
        String snapshot = "{\"id\":1,\"status\":0,\"amount\":100}";
        AuditContextHolder.set(new AuditContext()
                .setInterfaceId(5L)
                .setSqlText("UPDATE orders SET status=1 WHERE id=1")
                .setOpType("UPDATE")
                .setTargetDb("business_db")
                .setTargetTable("orders")
                .setBeforeSnapshot(snapshot));

        testStub.executeOk();
        Thread.sleep(500);

        List<SqlAuditLog> logs = auditLogMapper.selectList(null);
        assertEquals(1, logs.size());
        assertEquals(snapshot, logs.get(0).getBeforeSnapshot());
    }

    // ─── 6. 无 AuditContext 时不写入 ─────────────────────────────────────────

    @Test
    void 无AuditContext时_不写入审计记录() throws InterruptedException {
        // 不设置 AuditContextHolder
        testStub.executeOk();
        Thread.sleep(300);

        List<SqlAuditLog> logs = auditLogMapper.selectList(null);
        assertTrue(logs.isEmpty(), "无 AuditContext 时不应写入审计记录");
    }

    // ─── 7. 定时清理任务 ──────────────────────────────────────────────────────

    @Test
    void 清理任务_删除超期记录_保留未超期记录() {
        // 插入一条过期记录（400天前，超过默认365天）
        SqlAuditLog oldLog = new SqlAuditLog();
        oldLog.setSqlText("OLD SQL");
        oldLog.setOpType("INSERT");
        oldLog.setResult("SUCCESS");
        oldLog.setOpTime(LocalDateTime.now().minusDays(400));
        auditLogMapper.insert(oldLog);

        // 插入一条未过期记录（10天前）
        SqlAuditLog recentLog = new SqlAuditLog();
        recentLog.setSqlText("RECENT SQL");
        recentLog.setOpType("INSERT");
        recentLog.setResult("SUCCESS");
        recentLog.setOpTime(LocalDateTime.now().minusDays(10));
        auditLogMapper.insert(recentLog);

        // 手动触发清理（不依赖 Cron 定时触发）
        cleanupJob.cleanup();

        List<SqlAuditLog> remaining = auditLogMapper.selectList(null);
        assertEquals(1, remaining.size());
        assertEquals("RECENT SQL", remaining.get(0).getSqlText());
    }
}
