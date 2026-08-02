-- ====================================================================
-- v0.3.1 · CR-003 版本显示迁移(2026-08-02)
-- ====================================================================
-- 目的:sys_app_info 表存版本/构建时间/git sha/作者/发布注 · H2 + MySQL 双兼容
-- 幂等 · 老库客户升级零破坏
-- 用户明确要求:表存"防篡改" · 展示需含 版本号 + 作者(光斓) + 中文日期 + "当前仅为测试版本"注
-- ====================================================================

CREATE TABLE IF NOT EXISTS sys_app_info (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version      VARCHAR(32)  NOT NULL COMMENT '语义化版本 · 如 v0.3.1',
    build_time   DATETIME     NOT NULL COMMENT '构建时间',
    git_sha      VARCHAR(64)  NULL COMMENT 'git commit id 短哈希',
    author       VARCHAR(64)  NOT NULL DEFAULT '光斓' COMMENT '作者(默认光斓)',
    release_note VARCHAR(255) NULL COMMENT '发布注 · 如 当前仅为测试版本',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 种子:v0.3.1 build 时启动 upsert 逻辑写入(见 SysAppInfoInitializer)
-- 手工插入示例(通常由启动 hook 完成):
-- INSERT INTO sys_app_info(version, build_time, git_sha, author, release_note)
-- VALUES ('v0.3.1', CURRENT_TIMESTAMP, 'abc1234', '光斓', '当前仅为测试版本');
