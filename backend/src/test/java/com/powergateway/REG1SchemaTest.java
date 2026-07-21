package com.powergateway;

import com.powergateway.service.SysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REG-1 Task 1 · Schema 与默认配置存在性测试
 *
 * 断言 registry_config 表已创建（可 INSERT 并 SELECT）+ sys_config 已含 4 个 registry.* 默认 KV。
 * 用 JdbcTemplate 而非 RegistryConfigMapper 是为了让 Task 1 的实体/Mapper 变更是"充分而非必要"，
 * 这份 SchemaTest 只关心 DDL + KV 初始化两件事，不耦合 Java 侧的实体设计。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class REG1SchemaTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SysConfigService sysConfigService;

    @Test
    void registry_config_表存在_可INSERT并SELECT() {
        int rows = jdbcTemplate.update(
                "INSERT INTO registry_config " +
                        "(type, name, server_addr, namespace, group_name, username, password, " +
                        " enabled, register_self, service_name, extra_metadata) " +
                        "VALUES ('nacos', '内部Nacos', '127.0.0.1:8848', 'public', 'DEFAULT_GROUP', " +
                        "        'nacos', 'AES-encrypted', 1, 1, 'POWERGATEWAY', '{}')"
        );
        assertThat(rows).isEqualTo(1);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registry_config WHERE type = 'nacos' AND name = '内部Nacos'",
                Long.class);
        assertThat(count).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void registry_config_必备列齐全() {
        // 通过 information_schema 查列存在性（H2 兼容）
        Long id = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registry_config WHERE " +
                        "id IS NULL OR type IS NULL OR name IS NULL OR server_addr IS NULL " +
                        "OR enabled IS NULL OR register_self IS NULL OR service_name IS NULL " +
                        "OR namespace IS NULL OR group_name IS NULL OR username IS NULL " +
                        "OR password IS NULL OR extra_metadata IS NULL OR create_time IS NULL " +
                        "OR update_time IS NULL",
                Long.class);
        // 只要 SQL 能执行不抛异常，说明列全在；具体值不关心
        assertThat(id).isNotNull();
    }

    @Test
    void sys_config_含_registry_self_service_name_默认POWERGATEWAY() {
        assertThat(sysConfigService.getString("registry.self.service_name", "__missing__"))
                .isEqualTo("POWERGATEWAY");
    }

    @Test
    void sys_config_含_registry_self_ip_override_默认空() {
        // KV 存在但值可以是空字符串；用 __missing__ 兜底区分"KV 不存在"和"KV 值为空"
        String v = sysConfigService.getString("registry.self.ip.override", "__missing__");
        assertThat(v).isNotEqualTo("__missing__");
    }

    @Test
    void sys_config_含_registry_heartbeat_interval_seconds_默认5() {
        assertThat(sysConfigService.getInt("registry.heartbeat.interval.seconds", -1))
                .isEqualTo(5);
    }

    @Test
    void sys_config_含_registry_heartbeat_fail_threshold_默认3() {
        assertThat(sysConfigService.getInt("registry.heartbeat.fail.threshold", -1))
                .isEqualTo(3);
    }
}
