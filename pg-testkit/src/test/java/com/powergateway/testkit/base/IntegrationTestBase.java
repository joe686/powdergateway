package com.powergateway.testkit.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.testkit.data.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * 全链路测试基类。
 * <p>
 * 封装登录、HTTP 调用 PG 后端、数据库校验等通用操作。
 * E2E 测试类继承此类即可直接使用 {@link #pgPost}、{@link #queryConfigDb} 等方法。
 * <p>
 * 位于 src/test/java（依赖 JUnit + spring-boot-test，仅测试期可用）。
 * 注意：测试类需通过 @SpringBootTest 启动 pg-testkit 上下文（用于注入 Mock 服务器等）。
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    @Value("${pg-testkit.pg-backend.base-url:http://localhost:8080}")
    protected String pgBaseUrl;

    @Value("${pg-testkit.pg-backend.username:admin}")
    protected String pgUsername;

    @Value("${pg-testkit.pg-backend.password:Admin@123}")
    protected String pgPassword;

    @Value("${pg-testkit.db.config-url}")
    protected String configDbUrl;

    @Value("${pg-testkit.db.audit-url}")
    protected String auditDbUrl;

    @Value("${pg-testkit.db.username:root}")
    protected String dbUsername;

    @Value("${pg-testkit.db.password:qwe12345}")
    protected String dbPassword;

    @Value("${pg-testkit.db.driver-class-name:com.mysql.cj.jdbc.Driver}")
    protected String dbDriver;

    protected RestTemplate restTemplate = new RestTemplate();
    protected ObjectMapper mapper = new ObjectMapper();
    protected String token;

    protected JdbcTemplate configJdbc;
    protected JdbcTemplate auditJdbc;

    @PostConstruct
    public void initJdbc() {
        configJdbc = createJdbcTemplate(configDbUrl);
        auditJdbc = createJdbcTemplate(auditDbUrl);
    }

    private JdbcTemplate createJdbcTemplate(String url) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(dbDriver);
        ds.setUrl(url);
        ds.setUsername(dbUsername);
        ds.setPassword(dbPassword);
        return new JdbcTemplate(ds);
    }

    @BeforeEach
    public void login() throws Exception {
        Map<String, Object> body = TestDataFactory.loginAdmin();
        ResponseEntity<String> resp = restTemplate.postForEntity(
                pgBaseUrl + "/api/auth/login", body, String.class);
        JsonNode root = mapper.readTree(resp.getBody());
        token = root.path("data").path("token").asText();
    }

    // ─────────────────────── HTTP 调用 ───────────────────────

    /** 以当前 token 调用 PG 后端 POST 接口 */
    protected JsonNode pgPost(String path, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.add("satoken", token);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                pgBaseUrl + path, HttpMethod.POST, entity, String.class);
        return mapper.readTree(resp.getBody());
    }

    /** 以当前 token 调用 PG 后端 GET 接口 */
    protected JsonNode pgGet(String path) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.add("satoken", token);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                pgBaseUrl + path, HttpMethod.GET, entity, String.class);
        return mapper.readTree(resp.getBody());
    }

    // ─────────────────────── 数据库校验 ───────────────────────

    /** 查询配置库 */
    protected List<Map<String, Object>> queryConfigDb(String sql) {
        return configJdbc.queryForList(sql);
    }

    /** 查询审计库 */
    protected List<Map<String, Object>> queryAuditDb(String sql) {
        return auditJdbc.queryForList(sql);
    }

    /** 查询配置库最新一条记录 */
    protected Map<String, Object> queryConfigDbLatest(String table, String orderColumn) {
        String sql = "SELECT * FROM " + table + " ORDER BY " + orderColumn + " DESC LIMIT 1";
        List<Map<String, Object>> rows = configJdbc.queryForList(sql);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
