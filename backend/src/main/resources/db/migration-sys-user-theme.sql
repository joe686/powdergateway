-- UX-A: sys_user.theme_pref 幂等迁移
DROP PROCEDURE IF EXISTS pg_add_theme_pref;
DELIMITER //
CREATE PROCEDURE pg_add_theme_pref()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'sys_user'
          AND COLUMN_NAME = 'theme_pref'
    ) THEN
        ALTER TABLE sys_user
        ADD COLUMN theme_pref TEXT DEFAULT NULL COMMENT '主题偏好 JSON';
    END IF;
END//
DELIMITER ;
CALL pg_add_theme_pref();
DROP PROCEDURE pg_add_theme_pref;
