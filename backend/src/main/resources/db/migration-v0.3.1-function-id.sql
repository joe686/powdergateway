-- ====================================================================
-- v0.3.1 · CR-007 双层功能号路由迁移(2026-08-02)
-- ====================================================================
-- 目的:interface_config 加 function_id 字段(PG 内部功能号 · 建议 PG- 前缀)
-- 幂等 · 老库客户升级零破坏(function_id 可空 · 老数据留空不影响)
--
-- 双层路由机制:
--  1. interface_config.function_id 存 PG 内部功能号(如 PG-181345)
--  2. FN-12 字典 scope=3 · systemCode=ROUTE · dictKey=channel_to_pg
--     存渠道 functionId → PG 功能号映射(路由前先查字典 · 未命中 fallback 直接 lookup)
--
-- 执行方式:mysql -u<user> -p<pwd> <config_db> < migration-v0.3.1-function-id.sql
-- ====================================================================

-- 加 function_id 字段(幂等 · MySQL 5.7 用 IF NOT EXISTS 或 procedure · 简化用 ALTER + 允许重复错误)
ALTER TABLE interface_config
    ADD COLUMN function_id VARCHAR(64) NULL COMMENT 'CR-007 · PG 内部功能号(建议 PG- 前缀 · 与渠道 functionId 区分)';

-- 唯一索引(function_id 允许 NULL · MySQL 唯一索引允许多个 NULL)
CREATE UNIQUE INDEX uk_interface_function_id ON interface_config(function_id);
