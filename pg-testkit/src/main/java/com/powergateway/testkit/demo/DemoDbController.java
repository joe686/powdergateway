package com.powergateway.testkit.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TEST-1 · 样例业务库管理 API
 *
 * 端点：
 *   POST /testkit/demo-db/init   [?force=true]  初始化样例库（幂等，force=true 强制重灌）
 *   POST /testkit/demo-db/reset                 清空重灌
 *   POST /testkit/demo-db/drop                  DROP 整个库（危险）
 *   GET  /testkit/demo-db/stats                 各表行数统计
 *
 * MysqlDemoDbInitializer 通过 @Component 装载；无 MySQL 数据源时端点会返回明确错误。
 */
@RestController
@RequestMapping("/testkit/demo-db")
@RequiredArgsConstructor
public class DemoDbController {

    private final ObjectProvider<DemoDbInitializer> initializerProvider;

    @PostMapping("/init")
    public Map<String, Object> init(@RequestParam(defaultValue = "false") boolean force) {
        DemoDbInitializer init = getInitializer();
        if (init == null) return notAvailable();
        init.init(force);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("message", "样例业务库初始化完成");
        r.put("database", init.describe());
        r.put("tables", buildTableStats(init));
        return r;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        DemoDbInitializer init = getInitializer();
        if (init == null) return notAvailable();
        init.reset();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("message", "样例业务库已重置");
        r.put("tables", buildTableStats(init));
        return r;
    }

    @PostMapping("/drop")
    public Map<String, Object> drop() {
        DemoDbInitializer init = getInitializer();
        if (init == null) return notAvailable();
        init.drop();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("message", "样例业务库已 DROP");
        return r;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        DemoDbInitializer init = getInitializer();
        if (init == null) return notAvailable();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("database", init.describe());
        r.put("tables", buildTableStats(init));
        return r;
    }

    private DemoDbInitializer getInitializer() {
        return initializerProvider.getIfAvailable();
    }

    private Map<String, Object> notAvailable() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", false);
        r.put("message", "无可用的 DemoDbInitializer（未配 MySQL 数据源或依赖缺失）");
        r.put("tables", new ArrayList<>());
        return r;
    }

    private List<Map<String, Object>> buildTableStats(DemoDbInitializer init) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            Map<String, Long> stats = init.stats();
            for (Map.Entry<String, Long> e : stats.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tableName", e.getKey());
                row.put("rowCount", e.getValue());
                row.put("description", describeTable(e.getKey()));
                rows.add(row);
            }
        } catch (Exception e) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tableName", "（错误）");
            row.put("rowCount", -1);
            row.put("description", e.getMessage());
            rows.add(row);
        }
        return rows;
    }

    private String describeTable(String tableName) {
        switch (tableName) {
            case "demo_user":       return "用户表（1000 条）";
            case "demo_account":    return "账户表（3000 条）";
            case "demo_txn":        return "交易流水（10 万条，分库分表 demo）";
            case "demo_product":    return "商品表（500 条）";
            case "demo_order":      return "订单表（5000 条）";
            case "demo_order_item": return "订单明细（1 万条）";
            case "demo_address":    return "地址表";
            case "demo_dict":       return "字典表";
            case "demo_config":     return "大文本 JSON 表";
            case "demo_log":        return "日志表（1 万条）";
            default: return "";
        }
    }
}
