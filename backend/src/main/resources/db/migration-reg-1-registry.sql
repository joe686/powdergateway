-- REG-1 迁移：新增 registry_config 表 + sys_config 追加 4 个 registry.* 默认 KV
-- 幂等：可重复执行；生产环境手工执行一次即可（新库通过 init.sql 已包含）

-- ============================================================
-- 1. 建 registry_config 表（存在则跳过）
-- ============================================================
CREATE TABLE IF NOT EXISTS registry_config (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  type           VARCHAR(32)  NOT NULL COMMENT 'nacos/eureka',
  name           VARCHAR(64)  NOT NULL COMMENT '用户起的别名',
  server_addr    VARCHAR(512) NOT NULL COMMENT 'nacos: host:port,host:port；eureka: http://host:port/eureka/',
  namespace      VARCHAR(64)  COMMENT 'nacos 命名空间（可空）',
  group_name     VARCHAR(64)  DEFAULT 'DEFAULT_GROUP' COMMENT 'nacos 分组',
  username       VARCHAR(64),
  password       VARCHAR(512) COMMENT 'AES 加密',
  enabled        TINYINT      DEFAULT 1 COMMENT '是否启用',
  register_self  TINYINT      DEFAULT 1 COMMENT '是否把 PG 自身注册进去',
  service_name   VARCHAR(64)  DEFAULT 'POWERGATEWAY' COMMENT '自注册服务名',
  extra_metadata TEXT         COMMENT '注册携带的额外元数据 JSON',
  deleted        TINYINT      DEFAULT 0,
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. sys_config 追加 4 个默认 KV（幂等，已有则跳过）
-- ============================================================
INSERT IGNORE INTO sys_config (config_key, config_value, description, value_type, group_name) VALUES
('registry.self.service_name',         'POWERGATEWAY', '本机注册到注册中心时使用的服务名（REG-1）', 'string',  '注册中心'),
('registry.self.ip.override',          '',             '注册时上报的 IP，空则自动探测（REG-1）',   'string',  '注册中心'),
('registry.heartbeat.interval.seconds','5',            '注册中心心跳间隔（秒，REG-1）',              'number',  '注册中心'),
('registry.heartbeat.fail.threshold',  '3',            '连续心跳失败多少次触发告警（REG-1）',       'number',  '注册中心');
