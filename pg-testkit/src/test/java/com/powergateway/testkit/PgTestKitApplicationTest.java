package com.powergateway.testkit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * pg-testkit 应用上下文加载冒烟测试。
 */
@SpringBootTest
class PgTestKitApplicationTest {

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能正常启动
    }

    @Test
    void mainClassExists() {
        assertNotNull(PgTestKitApplication.class);
    }
}
