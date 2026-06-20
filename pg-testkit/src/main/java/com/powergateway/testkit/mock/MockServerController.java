package com.powergateway.testkit.mock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock 服务器管理 API。
 * <p>
 * 供 AI 通过 HTTP 控制 Mock 服务器：启停、配置规则、查询请求、验证。
 */
@RestController
@RequestMapping("/test/mock-server")
public class MockServerController {

    @Autowired
    private MockServerService mockServerService;

    /** 启动 Mock 服务器（已运行则返回提示） */
    @PostMapping("/start")
    public Map<String, Object> start() {
        mockServerService.start();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "running");
        return result;
    }

    /** 停止 Mock 服务器 */
    @PostMapping("/stop")
    public Map<String, Object> stop() {
        mockServerService.stop();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "stopped");
        return result;
    }

    /** 重置所有规则和请求记录 */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        mockServerService.reset();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "reset");
        return result;
    }

    /** 添加一条响应规则 */
    @PostMapping("/configure")
    public Map<String, Object> configure(@RequestBody MockResponseRule rule) {
        mockServerService.addRule(rule);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "added");
        result.put("ruleName", rule.getName());
        return result;
    }

    /** 替换全部响应规则 */
    @PostMapping("/configure-batch")
    public Map<String, Object> configureBatch(@RequestBody List<MockResponseRule> rules) {
        mockServerService.setRules(rules);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "replaced");
        result.put("ruleCount", rules == null ? 0 : rules.size());
        return result;
    }

    /** 查询当前所有规则 */
    @GetMapping("/rules")
    public List<MockResponseRule> rules() {
        return mockServerService.getRules();
    }

    /** 查询收到的请求记录（可选按 path 过滤） */
    @GetMapping("/requests")
    public List<RequestRecord> requests(@RequestParam(required = false) String path) {
        if (path == null || path.isEmpty()) {
            return mockServerService.getRequestRecords();
        }
        return mockServerService.getRequestRecordsByPath(path);
    }

    /**
     * 验证请求是否符合预期。
     * 请求体格式：{ "path": "...", "method": "POST", "bodyContains": "...", "minCount": 1 }
     */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody Map<String, Object> expectation) {
        String path = (String) expectation.get("path");
        String method = (String) expectation.get("method");
        String bodyContains = (String) expectation.get("bodyContains");
        int minCount = expectation.get("minCount") == null ? 1
                : ((Number) expectation.get("minCount")).intValue();

        List<RequestRecord> all = mockServerService.getRequestRecords();
        int matched = 0;
        for (RequestRecord r : all) {
            if (path != null && !path.equals(r.getPath())) {
                continue;
            }
            if (method != null && !method.equalsIgnoreCase(r.getMethod())) {
                continue;
            }
            if (bodyContains != null && (r.getBody() == null || !r.getBody().contains(bodyContains))) {
                continue;
            }
            matched++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("matched", matched);
        result.put("minCount", minCount);
        result.put("passed", matched >= minCount);
        return result;
    }
}
