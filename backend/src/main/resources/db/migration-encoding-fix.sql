-- ================================================================
-- UX-F · 中文乱码数据修复脚本（幂等）
-- 场景：MySQL 服务端字符集配置错导致中文被双重编码为乱码（latin1 存 utf8 字节）
-- 原理：CONVERT(CAST(CONVERT(col USING latin1) AS BINARY) USING utf8mb4) 反向解码
-- 前置：确保 my.ini 已设 [mysqld] character-set-server=utf8mb4 且已重启
--       并已执行 mysqldump powergateway_config > backup.sql 备份
-- 幂等：正常 utf8 字节的行 REGEXP '[[:cntrl:]]' 为 false，UPDATE 会跳过
-- ================================================================

-- 1. 会话字符集
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT     = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS    = utf8mb4;

-- 2. sys_config.description 反向解码
UPDATE sys_config
SET description = CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4)
WHERE description IS NOT NULL
  AND description <> ''
  AND description REGEXP '[[:cntrl:]]'
  AND CONVERT(CAST(CONVERT(description USING latin1) AS BINARY) USING utf8mb4) IS NOT NULL;

-- 3. sys_config 表字符集升级到 utf8mb4
ALTER TABLE sys_config
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 4. 输出汇总（供人工核对）
SELECT COUNT(*) AS sys_config_total
  FROM sys_config WHERE description IS NOT NULL AND description <> '';
