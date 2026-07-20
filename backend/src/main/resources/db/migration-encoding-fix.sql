-- ================================================================
-- UX-F · sys_config 中文乱码数据修复脚本（幂等）
-- 场景：MySQL 服务端字符集配置错（如 default-charset=latin1）导致早期
--       sys_config 初始化时中文按 utf8 编码后又被 latin1 存下，形成
--       双重编码乱码（"审计日志保留天数" → "瀹¤?鏃ュ織淇濈暀澶╂暟"）。
-- 前置：先执行 mysqldump powergateway_config > backup.sql 备份。
-- 幂等：只 UPDATE 目标 config_key 且当前 description 不等于正确值时才写，
--       不依赖 REGEXP 判乱码（Latin1 双编码字节不在控制字符范围，原判空跳过）。
-- 适用：sys_config 初始化产生的 4 条已知乱码；其它表用户数据请另行处理。
-- Wave6 修：老版本 REGEXP '[[:cntrl:]]' 匹配不到 Latin1 双编码，改为白名单直改。
-- ================================================================

SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT     = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS    = utf8mb4;

-- 1. 4 条已知乱码 config_key 白名单修复（幂等：目标值已正确则 UPDATE 影响行=0）
UPDATE sys_config SET description = '审计日志保留天数'
 WHERE config_key = 'audit.log.retention.days' AND description <> '审计日志保留天数';
UPDATE sys_config SET description = '查询缓存 TTL（秒）'
 WHERE config_key = 'cache.query.ttl' AND description <> '查询缓存 TTL（秒）';
UPDATE sys_config SET description = '转换模板缓存 TTL（秒）'
 WHERE config_key = 'cache.template.ttl' AND description <> '转换模板缓存 TTL（秒）';
UPDATE sys_config SET description = 'SQL 日志保留天数'
 WHERE config_key = 'sql.log.retention.days' AND description <> 'SQL 日志保留天数';

-- 2. 表字符集升级 utf8mb4（防止新数据再次乱码）
ALTER TABLE sys_config
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. 校验输出
SELECT config_key, description
  FROM sys_config
 WHERE config_key IN (
    'audit.log.retention.days','cache.query.ttl',
    'cache.template.ttl','sql.log.retention.days'
 );
