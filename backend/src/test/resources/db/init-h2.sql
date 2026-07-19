-- PowerGateway H2 测试初始化脚本（与 init.sql 等价，JSON 列用 TEXT 代替）

-- H2 兼容：模拟 MySQL DATE_FORMAT 函数（仅支持 %H:00 和 %Y-%m-%d 格式）
CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR "com.powergateway.utils.H2DateFormatAlias.format";

DROP TABLE IF EXISTS perf_alert;
DROP TABLE IF EXISTS perf_stat;
DROP TABLE IF EXISTS sys_log_history;
DROP TABLE IF EXISTS sys_log;
DROP TABLE IF EXISTS sql_audit_log;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS convert_template;
DROP TABLE IF EXISTS channel_config;
DROP TABLE IF EXISTS port_route;
DROP TABLE IF EXISTS db_connection;
DROP TABLE IF EXISTS interface_config;
DROP TABLE IF EXISTS shard_config;
DROP TABLE IF EXISTS field_formula;
DROP TABLE IF EXISTS sys_config;

-- 1. 用户表
CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password VARCHAR(128) NOT NULL,
  role VARCHAR(32) DEFAULT 'user',
  status TINYINT DEFAULT 1,
  theme_pref TEXT DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 2. 转换模板表
CREATE TABLE convert_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  src_format VARCHAR(32),
  target_format VARCHAR(32),
  mapping_rule TEXT,
  process_rule TEXT,
  is_latest TINYINT DEFAULT 1,
  version INT DEFAULT 1,
  deleted TINYINT DEFAULT 0,
  creator VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 3. 渠道配置表
CREATE TABLE channel_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_code VARCHAR(64) NOT NULL UNIQUE,
  channel_name VARCHAR(128),
  identify_field VARCHAR(128),
  template_id BIGINT,
  header_config TEXT COMMENT '渠道级别报文头配置（JSON），CHG-002',
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 4. 数据库连接表
CREATE TABLE db_connection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  db_type VARCHAR(32),
  url VARCHAR(512) NOT NULL,
  username VARCHAR(128),
  password VARCHAR(256),
  env VARCHAR(32) DEFAULT 'dev',
  pool_size INT DEFAULT 10,
  timeout INT DEFAULT 3000,
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 5. 接口配置表
CREATE TABLE interface_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  path VARCHAR(256),
  type VARCHAR(32),
  db_connection_id BIGINT,
  config_json TEXT,
  shard_config_id BIGINT,
  allow_batch_delete TINYINT DEFAULT 0,
  status VARCHAR(32) DEFAULT 'draft',
  log_enabled TINYINT DEFAULT 1,
  cache_enabled      TINYINT      DEFAULT 0,
  cache_ttl_seconds  INT          DEFAULT 300,
  cache_key_template VARCHAR(512) DEFAULT '',
  deleted TINYINT DEFAULT 0,
  creator VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 6. 分库分表配置表
CREATE TABLE shard_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  module_name VARCHAR(128),
  request_field VARCHAR(128),
  shard_rule TEXT,
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 7. 常用字段公式表
CREATE TABLE field_formula (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL UNIQUE,
  scene VARCHAR(128),
  db_connection_id BIGINT,
  formula_json TEXT,
  remark VARCHAR(512),
  deleted TINYINT DEFAULT 0,
  creator VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 9. 端口分发路由表（M1-7）
CREATE TABLE port_route (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_code         VARCHAR(64)  NOT NULL,
  port_address         VARCHAR(512) NOT NULL,
  port_method          VARCHAR(16)  DEFAULT 'POST',
  timeout              INT          DEFAULT 3000,
  retry_count          INT          DEFAULT 3,
  request_template_id  BIGINT,
  response_template_id BIGINT,
  header_config        TEXT         COMMENT '端口路由级别报文头配置（JSON），CHG-002',
  deleted              TINYINT      DEFAULT 0,
  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 8. 系统配置表（KV）
CREATE TABLE sys_config (
  config_key VARCHAR(128) PRIMARY KEY,
  config_value VARCHAR(1024),
  description VARCHAR(512),
  value_type   VARCHAR(32)  DEFAULT 'string',
  group_name   VARCHAR(64)  DEFAULT '其他',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 初始化系统配置默认值
INSERT INTO sys_config (config_key, config_value, description, value_type, group_name) VALUES
  ('cache.query.ttl',         '300',  '查询缓存 TTL（秒）',          'number',  '缓存配置'),
  ('cache.template.ttl',      '600',  '模板缓存 TTL（秒）',          'number',  '缓存配置'),
  ('sys.log.retention.days',  '30',   '操作日志归档天数',             'number',  '日志配置'),
  ('audit.log.retention.days','365',  '审计日志保留天数',             'number',  '日志配置'),
  ('sql.log.retention.days',  '90',   'SQL 日志保留天数',             'number',  '日志配置'),
  ('log_menu_enabled',        'true', '日志管理菜单显示开关',         'boolean', '日志配置'),
  ('alert_fail_rate',         '5',    '告警失败率阈值（%）',          'number',  '告警配置'),
  ('alert_response_ms',       '1000', '告警响应时间阈值（ms）',       'number',  '告警配置');

-- 审计日志表（M2-9）：独立审计库，H2 测试中使用 TEXT 代替 JSON
CREATE TABLE sql_audit_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  interface_id    BIGINT,
  sql_text        TEXT,
  op_type         VARCHAR(32),
  operator        VARCHAR(64),
  op_ip           VARCHAR(64),
  op_time         DATETIME,
  target_db       VARCHAR(128),
  target_table    VARCHAR(128),
  result          VARCHAR(32),
  error_msg       TEXT,
  before_snapshot TEXT
);

-- SYS-1 操作日志表（H2）
CREATE TABLE sys_log (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  module    VARCHAR(64),
  action    VARCHAR(128),
  operator  VARCHAR(64),
  op_ip     VARCHAR(64),
  op_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
  level     VARCHAR(16),
  error_msg TEXT,
  cost_ms   INT
);

-- SYS-1 操作日志历史归档表（H2，CHG-006）
CREATE TABLE sys_log_history (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  module        VARCHAR(64),
  action        VARCHAR(128),
  operator      VARCHAR(64),
  op_ip         VARCHAR(64),
  op_time       DATETIME,
  level         VARCHAR(16),
  error_msg     TEXT,
  cost_ms       INT,
  archived_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- SYS-2 性能统计明细表（H2）
CREATE TABLE perf_stat (
  id           BIGINT        PRIMARY KEY AUTO_INCREMENT,
  interface_id BIGINT,
  op_type      VARCHAR(32),
  cost_ms      INT,
  success      TINYINT,
  stat_time    DATETIME      DEFAULT CURRENT_TIMESTAMP
);

-- SYS-2 告警记录表（H2）
CREATE TABLE perf_alert (
  id          BIGINT        PRIMARY KEY AUTO_INCREMENT,
  alert_type  VARCHAR(64),
  alert_value DECIMAL(10,2),
  threshold   DECIMAL(10,2),
  message     VARCHAR(512),
  check_time  DATETIME      DEFAULT CURRENT_TIMESTAMP,
  resolved    TINYINT       DEFAULT 0
);
