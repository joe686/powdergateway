-- ============================================================================
-- FB-045 · v0.2.0 · 2026-07-27
-- 老库客户升级脚本：v0.1.x → v0.2.0 补建 FN-12 字典映射表
-- ----------------------------------------------------------------------------
-- 背景：
--   v0.2.0（CR-001 · CHG-028）新增 FN-12 字典映射管理功能，配套引入
--   dict_mapping 表。init.sql 是全量脚本，仅在**首次建库**执行；
--   老用户从 v0.1.x 拉 v0.2.0 代码后不重建库，会缺失该表，
--   前端 /tools/dict 管理页能进但保存必败（ERROR 1146 Table doesn't exist）。
-- 影响：
--   仅新增 1 张配置表（powergateway_config.dict_mapping），无数据迁移，
--   无 Java 代码修改，无老数据兼容问题。CI/新库通过 init.sql 已含此表。
-- 适用：
--   所有从 v0.1.x（含 v0.1.0 / v0.1.1）升级到 v0.2.x 的老库客户。
-- 前置：
--   已应用 CHG-030 的 migration-audit-to-config.sql（v0.2.0 另一个部署简化改动）。
-- ============================================================================

-- 【推荐先备份配置库】
-- mysqldump -u root -p powergateway_config > powergateway_config_backup_$(date +%Y%m%d).sql

USE powergateway_config;

-- Step 1：补建 dict_mapping 表（与 init.sql:258 定义完全一致 · IF NOT EXISTS 幂等）
CREATE TABLE IF NOT EXISTS dict_mapping (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  system_code   VARCHAR(64)  NOT NULL COMMENT '对端系统标识（业务代号 · 自由文本 · 前端下拉去重）',
  dict_key      VARCHAR(128) NOT NULL COMMENT '字典标识',
  direction     TINYINT      NOT NULL COMMENT '1=出向(PG→对端)  2=入向(对端→PG)',
  source_value  VARCHAR(255) NOT NULL COMMENT '源值',
  target_value  VARCHAR(255) NOT NULL COMMENT '目标值（多对一允许 target 重复）',
  cn_label      VARCHAR(255)          COMMENT '中文含义',
  status        TINYINT      DEFAULT 1 COMMENT '1=启用 0=停用',
  deleted       TINYINT      DEFAULT 0 COMMENT '软删除',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_src (system_code, dict_key, direction, source_value),
  KEY idx_lookup (system_code, dict_key, direction, source_value)
);

-- Step 2：验证表已建成（预期返回 1 行 dict_mapping）
SHOW TABLES LIKE 'dict_mapping';

-- Step 3：验证空表可查（预期返回 0）
SELECT COUNT(*) AS dict_mapping_row_count FROM dict_mapping;

-- Step 4：重启应用（Spring 首次访问 /api/dict-mapping 会加载 Mapper，无需额外操作）
-- 完成 ✅
--
-- 后续验证：admin 登录 → /tools/dict → 新增一条 CIF/GENDER/出向/M→1 → 保存成功即通。
-- 手工测试指南 MT-19-01 ~ MT-19-12 全部可执行（v0.1.0-手工测试指南.md § 十六）。
