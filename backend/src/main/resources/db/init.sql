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
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 初始化系统配置默认值
INSERT IGNORE INTO sys_config (config_key, config_value, description) VALUES
('cache.query.ttl', '300', '查询缓存 TTL（秒）'),
('cache.template.ttl', '600', '转换模板缓存 TTL（秒）'),
('audit.log.retention.days', '365', '审计日志保留天数'),
('sql.log.retention.days', '90', 'SQL 日志保留天数');

-- ========== 审计库表（M2-9）：在独立 powergateway_audit 库中建表 ==========
-- 生产环境需单独在审计库执行以下 DDL
-- CREATE DATABASE IF NOT EXISTS powergateway_audit DEFAULT CHARACTER SET utf8mb4;
-- USE powergateway_audit;
CREATE TABLE IF NOT EXISTS sql_audit_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  interface_id    BIGINT,
  sql_text        TEXT,
  op_type         VARCHAR(32),
  operator        VARCHAR(64),
  op_ip           VARCHAR(64),
  op_time         DATETIME,
  target_db       VARCHAR(128),
  target_table    VARCHAR(128),
  result          VARCHAR(32)  COMMENT 'SUCCESS/FAIL',
  error_msg       TEXT,
  before_snapshot JSON         COMMENT '修改前数据快照'
);
