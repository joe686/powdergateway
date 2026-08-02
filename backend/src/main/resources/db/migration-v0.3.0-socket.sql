-- ====================================================================
-- v0.3.0 SOCK-1 迁移脚本(2026-08-02 · lcpt-host 出站场景闭环)
-- ====================================================================
-- 目的:interface_config.type 扩枚举 SELECT/INSERT/UPDATE/DELETE + SOCKET
-- 幂等 · 老库客户升级零破坏(现有字段类型无变化 · 仅注释更新)
--
-- 执行方式:mysql -u<user> -p<pwd> <config_db> < migration-v0.3.0-socket.sql
-- ====================================================================

-- interface_config.type 已是 VARCHAR(32) · 能容纳 "SOCKET" · 无需 ALTER
-- 仅更新 COMMENT 说明支持的类型集合(便于运维/DBA 查表)
ALTER TABLE interface_config
    MODIFY COLUMN type VARCHAR(32) COMMENT 'SELECT/INSERT/UPDATE/DELETE/SOCKET(v0.3.0 起 SOCK-1)';

-- SOCKET 类型的接口配置约定(存 config_json 的 socket 段):
-- {
--   "socket": {
--     "ip":                    "10.1.2.3",
--     "port":                  6500,
--     "framing":               "xml_boundary" | "length_prefix_be4" | "length_prefix_be8",  // 必填 · 无默认
--     "charset":               "UTF-8" | "GBK",                                              // 必填 · 无默认
--     "connTimeoutMs":         3000,           // 可选 · 缺省 3000
--     "readTimeoutMs":         10000,          // 可选 · 缺省 10000
--     "connectionMode":        "short",        // 可选 · 缺省 short · v0.3.0 仅实装 short
--     "requestTemplate":       "<?xml ...?>", // 必填 · 支持 {paramName} 占位
--     "responseFlattenPrefix": ""              // 可选 · flattenMap 输出前缀
--   }
-- }
