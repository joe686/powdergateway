package com.powergateway.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SysConfig;
import com.powergateway.model.SysLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 操作日志归档定时任务（SYS-1，CHG-006）
 *
 * 每天凌晨 3 点执行（与 AuditLogCleanupJob 的 2 点错开），从 sys_config 读取
 * sys.log.retention.days（默认 30 天），将超期记录用单条 INSERT...SELECT 归档到
 * sys_log_history，再从 sys_log 删除。两步在同一事务中执行，失败时自动回滚。
 */
@Component
public class SysLogArchiveJob {

    private static final String CONFIG_KEY = "sys.log.retention.days";
    private static final int DEFAULT_RETENTION_DAYS = 30;

    @Autowired private SysLogMapper sysLogMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void archive() {
        int retentionDays = DEFAULT_RETENTION_DAYS;
        SysConfig config = sysConfigMapper.selectById(CONFIG_KEY);
        if (config != null && config.getConfigValue() != null) {
            try {
                retentionDays = Integer.parseInt(config.getConfigValue().trim());
            } catch (NumberFormatException ignored) {
            }
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);

        // 批量归档（INSERT...SELECT，比逐条循环高效）
        sysLogMapper.archiveTo(threshold);

        // 从当前表删除超期记录
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>().lt(SysLog::getOpTime, threshold));
    }
}
