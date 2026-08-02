-- ====================================================================
-- v0.3.1 · Task 6 · trace_id 三表补齐(2026-08-02)
-- ====================================================================
-- Q23=A · sys_log / sql_audit_log / perf_stat 三表加 trace_id + 索引
-- v0.5.0 只需补 business_op_log 第四表
--
-- 幂等 · 老库客户零破坏 · 老数据 trace_id 空可接受
-- ====================================================================

ALTER TABLE sys_log ADD COLUMN trace_id VARCHAR(64) NULL COMMENT 'v0.3.1 · 跨表追溯 UUID';
CREATE INDEX idx_sys_log_trace_id ON sys_log(trace_id);

ALTER TABLE sql_audit_log ADD COLUMN trace_id VARCHAR(64) NULL COMMENT 'v0.3.1 · 跨表追溯 UUID';
CREATE INDEX idx_sql_audit_log_trace_id ON sql_audit_log(trace_id);

ALTER TABLE perf_stat ADD COLUMN trace_id VARCHAR(64) NULL COMMENT 'v0.3.1 · 跨表追溯 UUID';
CREATE INDEX idx_perf_stat_trace_id ON perf_stat(trace_id);
