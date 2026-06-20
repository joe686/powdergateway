-- PowerGateway 数据库迁移脚本：sys_config 表补充 value_type/group_name 字段（BUG-010）
-- 幂等执行：通过 information_schema 判断字段是否存在，避免重复添加报错
-- 适用于在 CHG-005 之前创建的旧库，新库无需执行（CREATE TABLE 已包含这两个字段）

-- 使用存储过程实现幂等 ALTER TABLE
DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_sys_config_columns$$

CREATE PROCEDURE migrate_sys_config_columns()
BEGIN
    -- 检查并添加 value_type 字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND column_name = 'value_type'
    ) THEN
        ALTER TABLE sys_config ADD COLUMN value_type VARCHAR(32) DEFAULT 'string' COMMENT 'number/boolean/string';
    END IF;

    -- 检查并添加 group_name 字段
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_config'
          AND column_name = 'group_name'
    ) THEN
        ALTER TABLE sys_config ADD COLUMN group_name VARCHAR(64) DEFAULT '其他' COMMENT '前端分组名';
    END IF;
END$$

DELIMITER ;

CALL migrate_sys_config_columns();

DROP PROCEDURE IF EXISTS migrate_sys_config_columns;
