package com.powergateway.testkit.api;

import com.powergateway.testkit.scenario.TestScenario;
import com.powergateway.testkit.scenario.TestScenarioRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试操作 API（供 AI 调用）。
 * <p>
 * 暴露 /test/* 系列 API，AI 可通过 HTTP 控制 Mock 服务器、查询数据库、运行测试场景。
 */
@RestController
@RequestMapping("/test")
public class TestApiController {

    @Autowired
    private TestScenarioRunner scenarioRunner;

    @Value("${pg-testkit.db.config-url}")
    private String configDbUrl;

    @Value("${pg-testkit.db.audit-url}")
    private String auditDbUrl;

    @Value("${pg-testkit.db.username:root}")
    private String dbUsername;

    @Value("${pg-testkit.db.password:qwe12345}")
    private String dbPassword;

    @Value("${pg-testkit.db.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String dbDriver;

    private JdbcTemplate configJdbc;
    private JdbcTemplate auditJdbc;

    /** 最近一次场景执行结果（内存缓存，供 /test/results 查询） */
    private final List<Map<String, Object>> resultHistory = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
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

    // ─────────────────────── 健康检查 ───────────────────────

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("component", "pg-testkit");
        m.put("timestamp", System.currentTimeMillis());
        return m;
    }

    // ─────────────────────── 数据库查询 ───────────────────────

    /**
     * 执行 SQL 查询。
     * 请求体格式：{ "db": "config|audit", "sql": "SELECT ..." }
     */
    @PostMapping("/db/query")
    public Map<String, Object> dbQuery(@RequestBody Map<String, Object> req) {
        String db = (String) req.getOrDefault("db", "config");
        String sql = (String) req.get("sql");

        JdbcTemplate jdbc = "audit".equalsIgnoreCase(db) ? auditJdbc : configJdbc;
        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db", db);
        result.put("rowCount", rows.size());
        result.put("rows", rows);
        return result;
    }

    // ─────────────────────── 场景执行 ───────────────────────

    /**
     * 执行预设测试场景。
     * 请求体为完整的 {@link TestScenario} JSON。
     */
    @PostMapping("/run-scenario")
    public Map<String, Object> runScenario(@RequestBody TestScenario scenario) {
        List<TestScenarioRunner.StepResult> stepResults = scenarioRunner.run(scenario);

        long passed = stepResults.stream().filter(TestScenarioRunner.StepResult::isPassed).count();
        boolean allPassed = passed == stepResults.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", scenario.getName());
        result.put("totalSteps", stepResults.size());
        result.put("passedSteps", passed);
        result.put("allPassed", allPassed);
        result.put("steps", stepResults);

        // 缓存到历史
        Map<String, Object> historyEntry = new LinkedHashMap<>();
        historyEntry.put("timestamp", System.currentTimeMillis());
        historyEntry.put("scenario", scenario.getName());
        historyEntry.put("allPassed", allPassed);
        historyEntry.put("passedSteps", passed);
        historyEntry.put("totalSteps", stepResults.size());
        resultHistory.add(historyEntry);

        return result;
    }

    /**
     * 获取最近一次（或全部）测试结果。
     */
    @GetMapping("/results")
    public Map<String, Object> results(@RequestParam(required = false, defaultValue = "false") boolean all) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (all) {
            result.put("count", resultHistory.size());
            result.put("history", resultHistory);
        } else if (!resultHistory.isEmpty()) {
            result.put("latest", resultHistory.get(resultHistory.size() - 1));
        } else {
            result.put("message", "暂无测试结果");
        }
        return result;
    }
}
