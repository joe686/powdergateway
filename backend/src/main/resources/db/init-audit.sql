-- PowerGateway 审计库初始化脚本（BUG-007/BUG-008 修复）
-- 对应 M2-9 SQL 审计日志模块
-- 执行前请确保数据库已创建：CREATE DATABASE IF NOT EXISTS powergateway_audit DEFAULT CHARACTER SET utf8mb4;
-- 使用方法：mysql -u root -p -D <你的审计库名> < init-audit.sql
--        （由命令行 -D 参数指定库，本脚本不硬编码 USE 语句，便于客户自定义库名）

-- SQL 审计日志表（M2-9）
CREATE TABLE IF NOT EXISTS sql_audit_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  interface_id    BIGINT,
  sql_text        TEXT,
  op_type         VARCHAR(32),
  operator        VARCHAR(64),
  op_ip           VARCHAR(64),
  op_time         DATETIME,
  target_db       VARCHAR(128),
  target_table    VARCHAR(128),
  result          VARCHAR(32)  COMMENT 'SUCCESS/FAIL',
  error_msg       TEXT,
  before_snapshot JSON         COMMENT '修改前数据快照'
);

-- 建议为审计日志表添加索引以提升查询性能（按时间范围查询为主）
CREATE INDEX idx_sql_audit_op_time ON sql_audit_log(op_time);
CREATE INDEX idx_sql_audit_interface_id ON sql_audit_log(interface_id);
