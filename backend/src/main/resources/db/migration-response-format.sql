-- CHG-020 UX-E FN-06：为 interface_config 补齐响应格式相关字段（幂等）
-- 适用于在 CHG-020 之前创建的旧库；新库通过 init.sql 直接生效
DELIMITER $$

DROP PROCEDURE IF EXISTS pg_add_response_format$$

CREATE PROCEDURE pg_add_response_format()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'interface_config'
                   AND COLUMN_NAME = 'response_format') THEN
    ALTER TABLE interface_config
      ADD COLUMN response_format VARCHAR(16) DEFAULT 'JSON'
      COMMENT 'FN-06 用户默认响应格式：JSON/XML/CSV/FORM_DATA';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'interface_config'
                   AND COLUMN_NAME = 'response_headers') THEN
    ALTER TABLE interface_config
      ADD COLUMN response_headers TEXT DEFAULT NULL
      COMMENT 'FN-06 自定义响应头 JSON，格式 {"X-Foo":"bar"}';
  END IF;
END$$

DELIMITER ;

CALL pg_add_response_format();
DROP PROCEDURE pg_add_response_format;
