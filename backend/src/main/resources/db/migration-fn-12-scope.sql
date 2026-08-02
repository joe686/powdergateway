-- ============================================================================
-- Migration: FN-12 dict_mapping 加 scope 字段（v0.2.0 → v0.2.5 · CR-004 · FB-047）
-- ============================================================================
--
-- 用途:老库客户从 v0.2.0 升级到 v0.2.5 时执行。
-- 变更内容：
--   1. dict_mapping 加 scope TINYINT NOT NULL DEFAULT 3（3=通用共享，老数据兜底）
--   2. 唯一键从 (system_code, dict_key, direction, source_value) 扩为 (scope, system_code, dict_key, direction, source_value)
--   3. lookup 索引同步扩展
--
-- 幂等：可重复执行；已有 scope 字段或已扩展的唯一键会被 IF 条件跳过。
-- 场景语义：
--   scope=1 → 接口转换 M1 侧配置的字典
--   scope=2 → 可视化接口 M2 侧配置的字典
--   scope=3 → 通用共享（对 M1/M2 两侧同时可见 · 老数据 v0.2.0 兜底）
--
-- 运行前：**强烈建议先备份 dict_mapping 表**
-- 运行方式：`mysql -u root -p powergateway < migration-fn-12-scope.sql`
-- ============================================================================

USE powergateway;

-- ---------------------------------------------------------------------------
-- 1. 加 scope 字段（幂等：字段已存在时跳过）
-- ---------------------------------------------------------------------------

SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dict_mapping'
    AND COLUMN_NAME = 'scope'
);

SET @sql := IF(@col_exists = 0,
  'ALTER TABLE dict_mapping ADD COLUMN scope TINYINT NOT NULL DEFAULT 3 COMMENT ''1=接口转换M1侧 2=可视化接口M2侧 3=通用共享(v0.2.5 CR-004)'' AFTER id',
  'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 重建唯一键 uk_src 加 scope 维度（幂等：先尝试删旧再建新）
-- ---------------------------------------------------------------------------

SET @old_uk_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dict_mapping'
    AND INDEX_NAME = 'uk_src'
    AND COLUMN_NAME = 'system_code'
    AND SEQ_IN_INDEX = 1
);

-- 若旧唯一键第一列是 system_code（即未扩展），删除后重建含 scope
SET @sql := IF(@old_uk_exists > 0,
  'ALTER TABLE dict_mapping DROP INDEX uk_src, ADD UNIQUE KEY uk_src (scope, system_code, dict_key, direction, source_value)',
  'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 重建 lookup 索引加 scope 维度（幂等：先尝试删旧再建新）
-- ---------------------------------------------------------------------------

SET @old_idx_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'dict_mapping'
    AND INDEX_NAME = 'idx_lookup'
    AND COLUMN_NAME = 'system_code'
    AND SEQ_IN_INDEX = 1
);

SET @sql := IF(@old_idx_exists > 0,
  'ALTER TABLE dict_mapping DROP INDEX idx_lookup, ADD KEY idx_lookup (scope, system_code, dict_key, direction, source_value)',
  'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 4. 完成校验（查询 dict_mapping 表结构 · 应包含 scope 字段和新唯一键）
-- ---------------------------------------------------------------------------

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dict_mapping' AND COLUMN_NAME = 'scope';

SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dict_mapping'
GROUP BY INDEX_NAME;
