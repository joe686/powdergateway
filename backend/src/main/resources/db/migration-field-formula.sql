-- migration-field-formula.sql (UX-C · FN-03 · CHG-018)
-- 幂等升级 field_formula 表：补 update_time 列 + 3 个索引 + 表/列 COMMENT
-- 执行前提：powergateway_config 库已存在，field_formula 表已由 init.sql 建立

USE powergateway_config;

-- 1. 补 update_time 列（IF NOT EXISTS 通过存储过程实现，避免 MySQL 版本差异）
DROP PROCEDURE IF EXISTS add_update_time_if_missing;
DELIMITER $$
CREATE PROCEDURE add_update_time_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'powergateway_config'
          AND TABLE_NAME = 'field_formula'
          AND COLUMN_NAME = 'update_time'
    ) THEN
        ALTER TABLE field_formula
            ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            COMMENT '更新时间（MyBatis-Plus 自动填充）';
    END IF;
END$$
DELIMITER ;
CALL add_update_time_if_missing();
DROP PROCEDURE add_update_time_if_missing;

-- 2. 补索引（IF NOT EXISTS）
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(IN idx_name VARCHAR(64), IN idx_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = 'powergateway_config'
          AND TABLE_NAME = 'field_formula'
          AND INDEX_NAME = idx_name
    ) THEN
        SET @sql = idx_ddl;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL add_index_if_missing('uk_field_formula_name',
    'ALTER TABLE field_formula ADD UNIQUE KEY uk_field_formula_name (name)');
CALL add_index_if_missing('idx_field_formula_scene',
    'ALTER TABLE field_formula ADD KEY idx_field_formula_scene (scene)');
CALL add_index_if_missing('idx_field_formula_db_conn',
    'ALTER TABLE field_formula ADD KEY idx_field_formula_db_conn (db_connection_id)');
DROP PROCEDURE add_index_if_missing;

-- 3. 表注释
ALTER TABLE field_formula COMMENT = '常用字段公式表（UX-C FN-03 / CHG-018）';
