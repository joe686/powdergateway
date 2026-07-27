-- ============================================================================
-- FB-043 · CHG-030 · 2026-07-27
-- 老库客户升级脚本：审计库 sql_audit_log 表迁到配置库
-- ----------------------------------------------------------------------------
-- 背景：
--   v0.1.x 及以前 sql_audit_log 表建在独立 powergateway_audit 库。
--   FB-043 为简化客户环境部署（DBA 通常不给建多 schema），合并到单库。
-- 影响：
--   应用侧 application.yml 里 audit datasource URL 已改为指向 powergateway_config。
--   SqlAuditLogMapper 保留 @DS("audit") 兼容注解，datasource bean 保留但物理同库。
-- ============================================================================

-- 【推荐先备份】
-- mysqldump -u root -p powergateway_audit sql_audit_log > sql_audit_log_backup_$(date +%Y%m%d).sql

-- Step 1：把 audit 库的 sql_audit_log 表结构（含索引）复制到 config 库
--         若 config 库已有该表（异常情况），先手工 DROP 再执行
CREATE TABLE powergateway_config.sql_audit_log LIKE powergateway_audit.sql_audit_log;

-- Step 2：迁移数据
INSERT INTO powergateway_config.sql_audit_log SELECT * FROM powergateway_audit.sql_audit_log;

-- Step 3：验证条数一致（人工核对两个结果应相等）
SELECT COUNT(*) AS old_audit_rows FROM powergateway_audit.sql_audit_log;
SELECT COUNT(*) AS new_config_rows FROM powergateway_config.sql_audit_log;

-- Step 4：确认无误后清理老库
--         注意：DROP DATABASE 不可逆，请务必先完成 Step 3 校验
DROP TABLE powergateway_audit.sql_audit_log;
DROP DATABASE powergateway_audit;

-- Step 5：重启应用（新配置生效）
-- 完成 ✅
