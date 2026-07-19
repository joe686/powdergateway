-- PowerGateway 配置库初始化脚本
-- 对应 P0-3 交付单元，共 8 张核心表
-- 执行前请确保数据库已创建：CREATE DATABASE powergateway_config DEFAULT CHARACTER SET utf8mb4;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(128) NOT NULL COMMENT 'BCrypt加密',
  role VARCHAR(32) DEFAULT 'user' COMMENT 'admin/user/readonly',
  status TINYINT DEFAULT 1,
  theme_pref TEXT DEFAULT NULL COMMENT '主题偏好 JSON',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. 转换模板表
CREATE TABLE IF NOT EXISTS convert_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  src_format VARCHAR(32) COMMENT 'JSON/XML/CSV/FORM',
  target_format VARCHAR(32),
  mapping_rule JSON COMMENT '字段映射规则列表',
  process_rule JSON COMMENT '字段加工规则列表',
  is_latest TINYINT DEFAULT 1,
  version INT DEFAULT 1,
  deleted TINYINT DEFAULT 0,
  creator VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 3. 渠道配置表
CREATE TABLE IF NOT EXISTS channel_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_code VARCHAR(64) NOT NULL UNIQUE COMMENT '渠道编码',
  channel_name VARCHAR(128),
  identify_field VARCHAR(128) COMMENT '识别字段名',
  template_id BIGINT COMMENT '关联convert_template.id',
  header_config TEXT COMMENT '渠道级别报文头配置（JSON），CHG-002',
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 4. 数据库连接表
CREATE TABLE IF NOT EXISTS db_connection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  db_type VARCHAR(32) COMMENT 'MySQL/Oracle/PostgreSQL',
  url VARCHAR(512) NOT NULL,
  username VARCHAR(128),
  password VARCHAR(256) COMMENT 'AES加密存储',
  env VARCHAR(32) DEFAULT 'dev' COMMENT 'dev/test/prod',
  pool_size INT DEFAULT 10,
  timeout INT DEFAULT 3000,
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 5. 接口配置表
CREATE TABLE IF NOT EXISTS interface_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  path VARCHAR(256) COMMENT '发布后的访问路径',
  type VARCHAR(32) COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  db_connection_id BIGINT,
  config_json JSON COMMENT '完整接口配置（表、字段、条件、加工规则等）',
  shard_config_id BIGINT COMMENT '关联分库分表配置',
  allow_batch_delete TINYINT DEFAULT 0,
  status VARCHAR(32) DEFAULT 'draft' COMMENT 'draft/published/disabled',
  log_enabled TINYINT DEFAULT 1,
  cache_enabled      TINYINT      DEFAULT 0    COMMENT '是否开启缓存：0=否，1=是',
  cache_ttl_seconds  INT          DEFAULT 300  COMMENT '缓存 TTL（秒）',
  cache_key_template VARCHAR(512) DEFAULT ''   COMMENT 'key 模板，支持 {参数名} 占位符',
  deleted TINYINT DEFAULT 0,
  creator VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 6. 分库分表配置表
CREATE TABLE IF NOT EXISTS shard_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  module_name VARCHAR(128) COMMENT '业务模块名',
  request_field VARCHAR(128) COMMENT '请求中的路由字段',
  shard_rule JSON COMMENT '库表映射规则',
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 7. 常用字段公式表
CREATE TABLE IF NOT EXISTS field_formula (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL UNIQUE,
  scene VARCHAR(128) COMMENT '所属业务场景',
  db_connection_id BIGINT,
  formula_json JSON COMMENT '公式配置（条件、运算、接口字段关联）',
  remark VARCHAR(512),
  deleted TINYINT DEFAULT 0,
  creator VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 9. 端口分发路由表（M1-7）
CREATE TABLE IF NOT EXISTS port_route (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_code         VARCHAR(64)  NOT NULL COMMENT '渠道编码，关联 channel_config.channel_code',
  port_address         VARCHAR(512) NOT NULL COMMENT '目标端口完整URL（含协议和路径）',
  port_method          VARCHAR(16)  DEFAULT 'POST'  COMMENT 'GET/POST/PUT/DELETE',
  timeout              INT          DEFAULT 3000    COMMENT '连接超时（ms）',
  retry_count          INT          DEFAULT 3       COMMENT '失败重试次数',
  request_template_id  BIGINT       COMMENT '请求方向转换模板ID（A→B），关联 convert_template.id',
  response_template_id BIGINT       COMMENT '应答方向转换模板ID（B→A），为空则透传',
  header_config        TEXT         COMMENT '端口路由级别报文头配置（JSON），CHG-002',
  deleted              TINYINT      DEFAULT 0,
  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 8. 系统配置表（KV）
CREATE TABLE IF NOT EXISTS sys_config (
  config_key VARCHAR(128) PRIMARY KEY,
  config_value VARCHAR(1024),
  description VARCHAR(512),
  value_type   VARCHAR(32)  DEFAULT 'string' COMMENT 'number/boolean/string',
  group_name   VARCHAR(64)  DEFAULT '其他'   COMMENT '前端分组名',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 兼容旧库：CHG-005 新增 value_type/group_name 字段（BUG-010 修复）
-- 对于在 CHG-005 之前创建的数据库，需执行以下 ALTER TABLE 补齐字段
-- 新库通过上面的 CREATE TABLE 已包含这两个字段，以下语句会因字段已存在而报错，可忽略
-- 如需幂等执行，请使用 migration-sys-config.sql 存储过程版本
-- ALTER TABLE sys_config ADD COLUMN value_type VARCHAR(32) DEFAULT 'string' COMMENT 'number/boolean/string';
-- ALTER TABLE sys_config ADD COLUMN group_name VARCHAR(64) DEFAULT '其他' COMMENT '前端分组名';

-- 初始化系统配置默认值
INSERT IGNORE INTO sys_config (config_key, config_value, description, value_type, group_name) VALUES
('cache.query.ttl',         '300',  '查询缓存 TTL（秒）',          'number',  '缓存配置'),
('cache.template.ttl',      '600',  '模板缓存 TTL（秒）',          'number',  '缓存配置'),
('sys.log.retention.days',  '30',   '操作日志归档天数',             'number',  '日志配置'),
('audit.log.retention.days','365',  '审计日志保留天数',             'number',  '日志配置'),
('sql.log.retention.days',  '90',   'SQL 日志保留天数',             'number',  '日志配置'),
('log_menu_enabled',        'true', '日志管理菜单显示开关',         'boolean', '日志配置'),
('alert_fail_rate',         '5',    '告警失败率阈值（%）',          'number',  '告警配置'),
('alert_response_ms',       '1000', '告警响应时间阈值（ms）',       'number',  '告警配置');

-- ========== 审计库表（M2-9）：在独立 powergateway_audit 库中建表 ==========
-- 注意：审计表 DDL 已拆分到独立文件 init-audit.sql（BUG-007/BUG-008 修复）
-- 请在审计库 powergateway_audit 中单独执行 init-audit.sql
-- 脚本位置：backend/src/main/resources/db/init-audit.sql

-- SYS-1 操作日志表（配置库）
CREATE TABLE IF NOT EXISTS sys_log (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  module    VARCHAR(64)  COMMENT '操作模块（如"模板管理"）',
  action    VARCHAR(128) COMMENT '操作动作（如"保存模板"）',
  operator  VARCHAR(64)  COMMENT '操作人（Sa-Token loginId，未登录时为 system）',
  op_ip     VARCHAR(64),
  op_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  level     VARCHAR(16)  COMMENT 'INFO / ERROR',
  error_msg TEXT         COMMENT '失败时的错误信息',
  cost_ms   INT          COMMENT '执行耗时（ms）'
);

-- SYS-1 操作日志历史归档表（CHG-006）
CREATE TABLE IF NOT EXISTS sys_log_history (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module        VARCHAR(64),
  action        VARCHAR(128),
  operator      VARCHAR(64),
  op_ip         VARCHAR(64),
  op_time       DATETIME,
  level         VARCHAR(16),
  error_msg     TEXT,
  cost_ms       INT,
  archived_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间'
);

-- SYS-2 性能统计明细表
CREATE TABLE IF NOT EXISTS perf_stat (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  interface_id BIGINT       COMMENT '接口ID，关联 interface_config.id',
  op_type      VARCHAR(32)  COMMENT 'SELECT/INSERT/UPDATE/DELETE',
  cost_ms      INT          COMMENT '耗时（毫秒）',
  success      TINYINT      COMMENT '1=成功 0=失败',
  stat_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_perf_stat_time (stat_time),
  INDEX idx_perf_interface (interface_id)
);

-- SYS-2 告警记录表
CREATE TABLE IF NOT EXISTS perf_alert (
  id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
  alert_type  VARCHAR(64)   COMMENT 'FAIL_RATE / AVG_RESPONSE',
  alert_value DECIMAL(10,2) COMMENT '实际值（失败率%或毫秒）',
  threshold   DECIMAL(10,2) COMMENT '触发时的阈值',
  message     VARCHAR(512),
  check_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  resolved    TINYINT       DEFAULT 0
);
