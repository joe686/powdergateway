-- UX-D 迁移：port_route + convert_template 各加 function_code(_name) 字段，幂等
-- 在生产 MySQL 环境手工执行一次

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_uxd_function_code$$
CREATE PROCEDURE migrate_uxd_function_code()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'port_route'
          AND column_name = 'function_code'
    ) THEN
        ALTER TABLE port_route ADD COLUMN function_code VARCHAR(64) COMMENT '功能号（UX-D），可空';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'port_route'
          AND column_name = 'function_name'
    ) THEN
        ALTER TABLE port_route ADD COLUMN function_name VARCHAR(128) COMMENT '功能号中文名（UX-D），可空';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'convert_template'
          AND column_name = 'function_code'
    ) THEN
        ALTER TABLE convert_template ADD COLUMN function_code VARCHAR(64) COMMENT '功能号（UX-D），可空';
    END IF;
END$$
DELIMITER ;

CALL migrate_uxd_function_code();
DROP PROCEDURE IF EXISTS migrate_uxd_function_code;
