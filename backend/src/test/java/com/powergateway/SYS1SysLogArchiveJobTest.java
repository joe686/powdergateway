package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysLogHistoryMapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.job.SysLogArchiveJob;
import com.powergateway.model.SysLog;
import com.powergateway.model.SysLogHistory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogArchiveJob 归档任务测试")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogArchiveJobTest {

    @Autowired private SysLogArchiveJob archiveJob;
    @Autowired private SysLogMapper sysLogMapper;
    @Autowired private SysLogHistoryMapper sysLogHistoryMapper;

    @AfterAll
    void cleanup() {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getAction, "archive_test"));
        sysLogHistoryMapper.delete(new LambdaQueryWrapper<SysLogHistory>()
                .eq(SysLogHistory::getAction, "archive_test"));
    }

    @Test
    void 超期记录_归档到history并从sys_log删除() {
        // 插入一条60天前的"超期"记录
        SysLog old = new SysLog();
        old.setModule("测试模块");
        old.setAction("archive_test");
        old.setOperator("test_archive");
        old.setOpIp("127.0.0.1");
        old.setOpTime(LocalDateTime.now().minusDays(60));
        old.setLevel("INFO");
        old.setCostMs(5);
        sysLogMapper.insert(old);

        // 插入一条当天的"未超期"记录
        SysLog recent = new SysLog();
        recent.setModule("测试模块");
        recent.setAction("archive_test");
        recent.setOperator("test_archive_recent");
        recent.setOpIp("127.0.0.1");
        recent.setOpTime(LocalDateTime.now());
        recent.setLevel("INFO");
        recent.setCostMs(5);
        sysLogMapper.insert(recent);

        // 手动执行归档（默认留存30天，60天的超期）
        archiveJob.archive();

        // 超期记录应已从 sys_log 删除
        List<SysLog> remaining = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getOperator, "test_archive"));
        assertThat(remaining).isEmpty();

        // 超期记录应在 sys_log_history 中
        List<SysLogHistory> archived = sysLogHistoryMapper.selectList(
                new LambdaQueryWrapper<SysLogHistory>().eq(SysLogHistory::getOperator, "test_archive"));
        assertThat(archived).hasSize(1);
        assertThat(archived.get(0).getArchivedTime()).isNotNull();

        // 未超期记录应仍在 sys_log
        List<SysLog> stillRecent = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getOperator, "test_archive_recent"));
        assertThat(stillRecent).hasSize(1);
    }
}
