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
        // CR-005（v0.2.5 minor）: 10 张 demo_* 表全部补真业务字段，可支撑连表 CRUD 练习
        // 关联关系：
        //   demo_user 1─N demo_account / demo_txn / demo_address / demo_order / demo_log
        //   demo_order 1─N demo_order_item N─1 demo_product
        //   demo_dict 独立字典（product_category / order_status / gender 等），被 demo_product.category_code 逻辑引用
        //   demo_config 独立 KV+JSON 配置表

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
        st.execute("CREATE TABLE IF NOT EXISTS demo_product (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  product_code VARCHAR(32) UNIQUE," +
                "  name VARCHAR(128)," +
                "  category_code VARCHAR(32)," +
                "  price DECIMAL(18,2) DEFAULT 0," +
                "  stock INT DEFAULT 0," +
                "  status TINYINT DEFAULT 1," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_order (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  order_no VARCHAR(32) UNIQUE," +
                "  user_id BIGINT," +
                "  total_amount DECIMAL(18,2) DEFAULT 0," +
                "  status VARCHAR(16) DEFAULT 'CREATED'," +
                "  created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_order_item (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  order_id BIGINT," +
                "  product_id BIGINT," +
                "  qty INT DEFAULT 1," +
                "  unit_price DECIMAL(18,2) DEFAULT 0," +
                "  subtotal DECIMAL(18,2) DEFAULT 0)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_address (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT," +
                "  receiver VARCHAR(64)," +
                "  phone VARCHAR(20)," +
                "  province VARCHAR(32)," +
                "  city VARCHAR(32)," +
                "  district VARCHAR(32)," +
                "  detail VARCHAR(255)," +
                "  is_default TINYINT DEFAULT 0)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_dict (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  dict_type VARCHAR(32)," +
                "  dict_code VARCHAR(32)," +
                "  dict_name VARCHAR(64)," +
                "  sort_order INT DEFAULT 0," +
                "  UNIQUE KEY uk_type_code (dict_type, dict_code))");
        st.execute("CREATE TABLE IF NOT EXISTS demo_config (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  config_key VARCHAR(64) UNIQUE," +
                "  config_value TEXT," +
                "  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        st.execute("CREATE TABLE IF NOT EXISTS demo_log (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  user_id BIGINT," +
                "  action VARCHAR(32)," +
                "  target VARCHAR(64)," +
                "  detail VARCHAR(500)," +
                "  log_time DATETIME DEFAULT CURRENT_TIMESTAMP)");
    }

    private void seedMinimalData(Statement st) throws Exception {
        // CR-005（v0.2.5 minor）: 灌少量样本数据，够练连表 CRUD；Faker 10 万条留 v1.1
        // 依赖顺序：user → account / txn / address / order → order_item / dict / config / log
        // 注：TRUNCATE 会重置 AUTO_INCREMENT，seed 完 demo_user id 从 1 起递增，下面的外联 id 依此硬编码

        // --- demo_user (5 条) ---
        st.execute("INSERT INTO demo_user (user_no, name, gender, phone, balance) VALUES " +
                "('U000001', '张三', 1, '13800138000', 10000.00)," +
                "('U000002', '李四', 2, '13900139000', 20000.00)," +
                "('U000003', '王五', 1, '13700137000',  5000.00)," +
                "('U000004', '赵六', 2, '13600136000',  8000.00)," +
                "('U000005', '钱七', 1, '13500135000',  3000.00)" +
                " ON DUPLICATE KEY UPDATE name=VALUES(name)");

        // --- demo_account (6 条) ---
        st.execute("INSERT INTO demo_account (user_id, account_no, balance) VALUES " +
                "(1, 'A000001-01', 6000.00)," +
                "(1, 'A000001-02', 4000.00)," +
                "(2, 'A000002-01', 20000.00)," +
                "(3, 'A000003-01',  5000.00)," +
                "(4, 'A000004-01',  8000.00)," +
                "(5, 'A000005-01',  3000.00)" +
                " ON DUPLICATE KEY UPDATE balance=VALUES(balance)");

        // --- demo_txn (8 条) ---
        st.execute("INSERT INTO demo_txn (user_id, amount) VALUES " +
                "(1,  5000.00),(1, -300.00),(1, 200.00)," +
                "(2, 15000.00),(2, 5000.00)," +
                "(3,  5000.00),(4, 8000.00),(5, 3000.00)");

        // --- demo_dict (8 条，两类：product_category / order_status) ---
        st.execute("INSERT INTO demo_dict (dict_type, dict_code, dict_name, sort_order) VALUES " +
                "('product_category', 'ELECTRONICS', '电子产品', 1)," +
                "('product_category', 'BOOK',        '图书',    2)," +
                "('product_category', 'FOOD',        '食品',    3)," +
                "('order_status',     'CREATED',     '已创建',  1)," +
                "('order_status',     'PAID',        '已支付',  2)," +
                "('order_status',     'SHIPPED',     '已发货',  3)," +
                "('order_status',     'DONE',        '已完成',  4)," +
                "('order_status',     'CANCELED',    '已取消',  5)" +
                " ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name)");

        // --- demo_product (10 条) ---
        st.execute("INSERT INTO demo_product (product_code, name, category_code, price, stock) VALUES " +
                "('P001', 'iPhone 15',       'ELECTRONICS', 5999.00, 100)," +
                "('P002', 'MacBook Air',     'ELECTRONICS', 7999.00,  30)," +
                "('P003', 'AirPods Pro',     'ELECTRONICS', 1999.00, 200)," +
                "('P004', 'Kindle',          'ELECTRONICS',  999.00,  50)," +
                "('P005', 'Java核心技术卷I',   'BOOK',         128.00, 500)," +
                "('P006', '设计模式',          'BOOK',          68.00, 300)," +
                "('P007', '算法导论',          'BOOK',         128.00, 200)," +
                "('P008', '巧克力礼盒',        'FOOD',         128.00,1000)," +
                "('P009', '牛肉干',            'FOOD',          68.00, 800)," +
                "('P010', '茶叶',              'FOOD',         298.00, 400)" +
                " ON DUPLICATE KEY UPDATE name=VALUES(name)");

        // --- demo_address (4 条) ---
        st.execute("INSERT INTO demo_address (user_id, receiver, phone, province, city, district, detail, is_default) VALUES " +
                "(1, '张三', '13800138000', '广东省', '深圳市', '南山区', '科技园路 1 号',   1)," +
                "(1, '张三', '13800138000', '广东省', '广州市', '天河区', '珠江新城 A 座',   0)," +
                "(2, '李四', '13900139000', '北京市', '北京市', '朝阳区', '望京 SOHO T2',    1)," +
                "(3, '王五', '13700137000', '上海市', '上海市', '浦东新区', '陆家嘴环路',   1)");

        // --- demo_order (7 条) ---
        st.execute("INSERT INTO demo_order (order_no, user_id, total_amount, status) VALUES " +
                "('O2026072800001', 1, 5999.00, 'PAID')," +
                "('O2026072800002', 1,  196.00, 'SHIPPED')," +
                "('O2026072800003', 2, 7999.00, 'DONE')," +
                "('O2026072800004', 2,  128.00, 'CREATED')," +
                "('O2026072800005', 3, 1999.00, 'PAID')," +
                "('O2026072800006', 4,  460.00, 'DONE')," +
                "('O2026072800007', 5,  128.00, 'CANCELED')" +
                " ON DUPLICATE KEY UPDATE status=VALUES(status)");

        // --- demo_order_item (10 条) ---
        st.execute("INSERT INTO demo_order_item (order_id, product_id, qty, unit_price, subtotal) VALUES " +
                "(1, 1, 1, 5999.00, 5999.00)," +
                "(2, 5, 1,  128.00,  128.00)," +
                "(2, 6, 1,   68.00,   68.00)," +
                "(3, 2, 1, 7999.00, 7999.00)," +
                "(4, 5, 1,  128.00,  128.00)," +
                "(5, 3, 1, 1999.00, 1999.00)," +
                "(6, 8, 2,  128.00,  256.00)," +
                "(6, 6, 1,   68.00,   68.00)," +
                "(6, 9, 2,   68.00,  136.00)," +
                "(7, 5, 1,  128.00,  128.00)");

        // --- demo_config (3 条 JSON 配置) ---
        st.execute("INSERT INTO demo_config (config_key, config_value) VALUES " +
                "('site.name',        '{\"zh\":\"PG 电商 Demo\",\"en\":\"PG Demo Shop\"}')," +
                "('promotion.banner', '{\"title\":\"暑期大促\",\"start\":\"2026-07-01\",\"end\":\"2026-08-31\",\"discount\":0.8}')," +
                "('feature.flags',    '{\"new_checkout\":true,\"ai_recommend\":false}')" +
                " ON DUPLICATE KEY UPDATE config_value=VALUES(config_value)");

        // --- demo_log (15 条操作日志) ---
        st.execute("INSERT INTO demo_log (user_id, action, target, detail) VALUES " +
                "(1, 'LOGIN',        'system',                  '登录成功')," +
                "(1, 'CREATE_ORDER', 'order:O2026072800001',    'iPhone 15 x1')," +
                "(1, 'PAY_ORDER',    'order:O2026072800001',    '支付宝支付 5999.00')," +
                "(1, 'CREATE_ORDER', 'order:O2026072800002',    '书 + 巧克力组合')," +
                "(2, 'LOGIN',        'system',                  '登录成功')," +
                "(2, 'CREATE_ORDER', 'order:O2026072800003',    'MacBook Air x1')," +
                "(2, 'PAY_ORDER',    'order:O2026072800003',    '支付宝支付 7999.00')," +
                "(2, 'CREATE_ORDER', 'order:O2026072800004',    'Java 核心技术')," +
                "(3, 'LOGIN',        'system',                  '登录成功')," +
                "(3, 'CREATE_ORDER', 'order:O2026072800005',    'AirPods Pro')," +
                "(4, 'LOGIN',        'system',                  '登录成功')," +
                "(4, 'CREATE_ORDER', 'order:O2026072800006',    '零食组合')," +
                "(5, 'CANCEL_ORDER', 'order:O2026072800007',    '库存不足取消')," +
                "(1, 'LOGOUT',       'system',                  '主动登出')," +
                "(2, 'UPDATE_ADDR',  'user:U000002',            '修改默认地址')");
    }
}
