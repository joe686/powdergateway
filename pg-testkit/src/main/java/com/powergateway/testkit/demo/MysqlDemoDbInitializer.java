package com.powergateway.testkit.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TEST-1 · MySQL 样例业务库初始化器
 *
 * v1 骨架实现：init 创建 demo_user / demo_account / demo_txn 等 10 张表 + 灌少量样本数据；
 * 完整 Faker 生成 10 万条交易数据的能力留待 v1.1。
 *
 * 通过 pg-testkit.db.demo-url + demo-user + demo-password 配置连接。
 * @ConditionalOnProperty 保证未配 demo-url 时本 Bean 不装载。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pg-testkit.db.demo-url")
public class MysqlDemoDbInitializer implements DemoDbInitializer {

    private static final List<String> DEMO_TABLES = Arrays.asList(
            "demo_user", "demo_account", "demo_txn", "demo_product",
            "demo_order", "demo_order_item", "demo_address", "demo_dict",
            "demo_config", "demo_log"
    );

    @Value("${pg-testkit.db.demo-url}")
    private String url;

    @Value("${pg-testkit.db.demo-user:root}")
    private String user;

    @Value("${pg-testkit.db.demo-password:}")
    private String password;

    @Override
    public String describe() {
        return "MySQL @ " + url;
    }

    @Override
    public void init(boolean force) {
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            // 检查一个 sentinel 表是否存在
            boolean exists = tableExists(conn, "demo_user");
            if (exists && !force) {
                log.info("TEST-1: demo_user 已存在，跳过初始化（force=false）");
                return;
            }
            createTables(st);
            seedMinimalData(st);
            log.info("TEST-1: 样例业务库初始化完成");
        } catch (Exception e) {
            throw new RuntimeException("TEST-1: MySQL 样例库初始化失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void reset() {
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            for (String t : DEMO_TABLES) {
                st.execute("TRUNCATE TABLE " + t);
            }
            seedMinimalData(st);
            log.info("TEST-1: 样例业务库已重置");
        } catch (Exception e) {
            throw new RuntimeException("TEST-1: reset 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void drop() {
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            for (String t : DEMO_TABLES) {
                st.execute("DROP TABLE IF EXISTS " + t);
            }
            log.warn("TEST-1: 样例业务库已 DROP");
        } catch (Exception e) {
            throw new RuntimeException("TEST-1: drop 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Long> stats() {
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            for (String t : DEMO_TABLES) {
                long count = 0;
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    if (rs.next()) count = rs.getLong(1);
                } catch (Exception ignore) {
                    // 表不存在，count=0
                }
                result.put(t, count);
            }
        } catch (Exception e) {
            log.warn("TEST-1: stats 失败: {}", e.getMessage());
        }
        return result;
    }

    // ============================================================
    // helpers
    // ============================================================

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(url, user, password);
    }

    private boolean tableExists(Connection conn, String table) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void createTables(Statement st) throws Exception {
        // v1 骨架版：仅 demo_user + demo_account + demo_txn 三张核心表；
        // 完整 10 表结构留在 pg-testkit/src/main/resources/demo-db/schema-mysql.sql
        st.execute("CREATE TABLE IF NOT EXISTS demo_user (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_no VARCHAR(32) UNIQUE," +
                "  name VARCHAR(64)," +
                "  gender TINYINT," +
                "  phone VARCHAR(20)," +
                "  balance DECIMAL(18,2) DEFAULT 0," +
                "  status TINYINT DEFAULT 1," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_account (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT," +
                "  account_no VARCHAR(32) UNIQUE," +
                "  balance DECIMAL(18,2) DEFAULT 0," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_txn (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT," +
                "  amount DECIMAL(18,2)," +
                "  txn_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
        // 其余 7 张表按需拓展
        for (String t : Arrays.asList("demo_product", "demo_order", "demo_order_item",
                "demo_address", "demo_dict", "demo_config", "demo_log")) {
            st.execute("CREATE TABLE IF NOT EXISTS " + t +
                    " (id BIGINT AUTO_INCREMENT PRIMARY KEY, placeholder VARCHAR(255))");
        }
    }

    private void seedMinimalData(Statement st) throws Exception {
        // v1 只灌少量样本，用户可通过 reset + Faker 后续扩展
        st.execute("INSERT INTO demo_user (user_no, name, gender, phone, balance) " +
                "VALUES ('U000001', '张三', 1, '13800138000', 10000.00) ON DUPLICATE KEY UPDATE name=name");
        st.execute("INSERT INTO demo_user (user_no, name, gender, phone, balance) " +
                "VALUES ('U000002', '李四', 2, '13900139000', 20000.00) ON DUPLICATE KEY UPDATE name=name");
    }
}
