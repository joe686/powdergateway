package com.powergateway.testkit.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.testkit.mock.MockServerService;
import com.powergateway.testkit.mock.RequestRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试场景执行器。
 * <p>
 * 按 {@link TestScenario} 的步骤顺序执行，每步结果记录到结果列表。
 * 支持 login / createTemplate / convert / dispatch / publishInterface / execInterface /
 * verifyAudit / verifyDb / verifyMock 等动作。
 */
@Slf4j
@Component
public class TestScenarioRunner {

    @Autowired
    private MockServerService mockServerService;

    @Value("${pg-testkit.pg-backend.base-url:http://localhost:8080}")
    private String pgBaseUrl;

    @Value("${pg-testkit.pg-backend.username:admin}")
    private String pgUsername;

    @Value("${pg-testkit.pg-backend.password:Admin@123}")
    private String pgPassword;

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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private JdbcTemplate configJdbc;
    private JdbcTemplate auditJdbc;

    /** 场景执行过程中的会话 token */
    private String currentToken;

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

    /**
     * 执行测试场景，返回每步结果。
     */
    public List<StepResult> run(TestScenario scenario) {
        List<StepResult> results = new ArrayList<>();
        log.info("开始执行测试场景：{}", scenario.getName());

        for (int i = 0; i < scenario.getSteps().size(); i++) {
            TestScenario.Step step = scenario.getSteps().get(i);
            StepResult result = new StepResult();
            result.setIndex(i + 1);
            result.setName(step.getName());
            result.setAction(step.getAction());

            try {
                Object output = executeStep(step);
                result.setOutput(output);
                result.setPassed(true);

                // 执行预期断言
                if (step.getExpect() != null) {
                    String issue = checkExpectation(step.getExpect(), output);
                    if (issue != null) {
                        result.setPassed(false);
                        result.setReason(issue);
                    }
                }
            } catch (Exception e) {
                result.setPassed(false);
                result.setReason(e.getMessage());
                log.error("步骤 [{}] 执行失败：{}", step.getName(), e.getMessage(), e);
            }
            results.add(result);

            // 某步失败则终止后续步骤
            if (!result.isPassed()) {
                log.warn("步骤 [{}] 失败，终止后续步骤", step.getName());
                break;
            }
        }
        log.info("测试场景 [{}] 执行完成，通过 {}/{} 步",
                scenario.getName(),
                results.stream().filter(StepResult::isPassed).count(),
                results.size());
        return results;
    }

    @SuppressWarnings("unchecked")
    private Object executeStep(TestScenario.Step step) throws Exception {
        Map<String, Object> params = step.getParams() instanceof Map
                ? (Map<String, Object>) step.getParams()
                : mapper.convertValue(step.getParams(), Map.class);

        switch (step.getAction()) {
            case "login":
                return doLogin(params);
            case "createTemplate":
                return doPost("/api/template/save", params);
            case "convert":
                return doPost("/api/convert", params);
            case "dispatch":
                return doPost("/api/dispatch", params);
            case "publishInterface":
                return doPost("/api/interface/publish/" + params.get("id"), new LinkedHashMap<>());
            case "execInterface":
                return doPost("/api/exec/" + params.get("id"), params);
            case "verifyAudit":
                return queryAudit(params);
            case "verifyDb":
                return queryConfig(params);
            case "verifyMock":
                return queryMock(params);
            default:
                throw new IllegalArgumentException("不支持的动作类型：" + step.getAction());
        }
    }

    private Object doLogin(Map<String, Object> params) throws Exception {
        String username = (String) params.getOrDefault("username", pgUsername);
        String password = (String) params.getOrDefault("password", pgPassword);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                pgBaseUrl + "/api/auth/login", body, String.class);
        Map<String, Object> result = mapper.readValue(resp.getBody(), Map.class);
        currentToken = (String) ((Map<String, Object>) result.get("data")).get("token");
        return result;
    }

    private Object doPost(String path, Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (currentToken != null) {
            headers.add("satoken", currentToken);
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(
                pgBaseUrl + path, HttpMethod.POST, entity, String.class);
        return mapper.readValue(resp.getBody(), Map.class);
    }

    private Object queryAudit(Map<String, Object> params) {
        String sql = (String) params.get("sql");
        return auditJdbc.queryForList(sql);
    }

    private Object queryConfig(Map<String, Object> params) {
        String sql = (String) params.get("sql");
        return configJdbc.queryForList(sql);
    }

    private Object queryMock(Map<String, Object> params) {
        String path = (String) params.get("path");
        List<RequestRecord> records = path == null
                ? mockServerService.getRequestRecords()
                : mockServerService.getRequestRecordsByPath(path);
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (RequestRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("method", r.getMethod());
            m.put("path", r.getPath());
            m.put("body", r.getBody());
            m.put("matchedRule", r.getMatchedRule());
            m.put("responseStatus", r.getResponseStatus());
            simplified.add(m);
        }
        return simplified;
    }

    @SuppressWarnings("unchecked")
    private String checkExpectation(TestScenario.Expectation expect, Object output) throws Exception {
        // code 断言
        if (expect.getCode() != null && output instanceof Map) {
            Map<String, Object> result = (Map<String, Object>) output;
            Object code = result.get("code");
            if (code == null || ((Number) code).intValue() != expect.getCode()) {
                return "预期 code=" + expect.getCode() + "，实际=" + code;
            }
        }
        // bodyContains 断言
        if (expect.getBodyContains() != null) {
            String json = mapper.writeValueAsString(output);
            if (!json.contains(expect.getBodyContains())) {
                return "响应未包含预期文本：" + expect.getBodyContains();
            }
        }
        // mockRequestCount 断言
        if (expect.getMockRequestCount() != null && output instanceof List) {
            int actual = ((List<?>) output).size();
            if (actual < expect.getMockRequestCount()) {
                return "预期 Mock 请求数 >= " + expect.getMockRequestCount() + "，实际=" + actual;
            }
        }
        return null;
    }

    /**
     * 单步执行结果。
     */
    @lombok.Data
    public static class StepResult {
        private int index;
        private String name;
        private String action;
        private boolean passed;
        private Object output;
        private String reason;
    }
}
