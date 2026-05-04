package com.powergateway.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.SqlAuditLogMapper;
import com.powergateway.dao.SysConfigMapper;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.model.SysConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计日志定时清理任务（M2-9）
 *
 * 每天凌晨 2 点执行，从 sys_config 读取留存天数（audit.log.retention.days，默认 365），
 * 删除 op_time 早于阈值的审计记录（物理删除，审计表无软删除）。
 */
@Component
public class AuditLogCleanupJob {

    private static final String CONFIG_KEY = "audit.log.retention.days";
    private static final int DEFAULT_RETENTION_DAYS = 365;

    @Autowired
    private SqlAuditLogMapper auditLogMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanup() {
        int retentionDays = DEFAULT_RETENTION_DAYS;

        SysConfig config = sysConfigMapper.selectById(CONFIG_KEY);
        if (config != null && config.getConfigValue() != null) {
            try {
                retentionDays = Integer.parseInt(config.getConfigValue().trim());
            } catch (NumberFormatException ignored) {
                // 配置值非法时使用默认值
            }
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        auditLogMapper.delete(new QueryWrapper<SqlAuditLog>().lt("op_time", threshold));
    }
}
