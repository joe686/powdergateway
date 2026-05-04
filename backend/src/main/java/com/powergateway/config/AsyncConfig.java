package com.powergateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步与定时任务配置（M2-9）
 * - @EnableAsync：启用 @Async 注解支持
 * - @EnableScheduling：启用 @Scheduled 注解支持（AuditLogCleanupJob 每天凌晨清理过期审计记录）
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
