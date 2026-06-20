package com.powergateway.testkit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * PG-TestKit 独立测试工具启动类。
 * <p>
 * 默认端口 8081（见 application.yml），不与 PG 后端 8080 冲突。
 * 启动后可通过 /test/* API 控制 Mock 服务器、查询数据库、运行测试场景。
 * <p>
 * 排除 {@link DataSourceAutoConfiguration}：测试工具通过自定义 DriverManagerDataSource
 * 手动创建 JdbcTemplate 直连配置库/审计库，不使用 Spring Boot 的数据源自动配置。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class PgTestKitApplication {

    public static void main(String[] args) {
        SpringApplication.run(PgTestKitApplication.class, args);
    }
}
