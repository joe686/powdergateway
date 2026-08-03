package com.powergateway.testkit.api;

import com.powergateway.testkit.eureka.MultiAppSelfRegister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v0.3.11 CHG-054 · pg-testkit Eureka 多应用自注册手工控制 API。
 *
 * <p>供 AI / 用户手工触发注册 · 观察 pg-testkit 假应用在 Eureka Server 的可见性。</p>
 *
 * <p>启动自动注册见 {@link MultiAppSelfRegister} @PostConstruct(需 pg-testkit.eureka.enabled=true)。</p>
 */
@RestController
@RequestMapping("/test/eureka")
public class EurekaTestController {

    @Autowired private MultiAppSelfRegister register;

    /** 已注册应用列表 + 配置概览。 */
    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", register.getConfig().isEnabled());
        m.put("serverUrl", register.getConfig().getServerUrl());
        m.put("configuredCount", register.getConfig().getApplications().size());
        m.put("configured", register.getConfig().getApplications());
        m.put("registeredCount", register.listRegistered().size());
        m.put("registered", register.listRegistered());
        return m;
    }

    /** 手工触发全量注册 · 返成功数。 */
    @PostMapping("/register-all")
    public Map<String, Object> registerAll() {
        int ok = register.registerAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", "register-all");
        m.put("successCount", ok);
        m.put("total", register.getConfig().getApplications().size());
        return m;
    }

    /** 手工触发全量注销 · 返成功数。 */
    @DeleteMapping("/deregister-all")
    public Map<String, Object> deregisterAll() {
        int ok = register.deregisterAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", "deregister-all");
        m.put("successCount", ok);
        return m;
    }

    /** 手工注册单个应用(按名称匹配 config.applications)。 */
    @PostMapping("/register/{name}")
    public Map<String, Object> registerOne(@PathVariable String name) {
        boolean ok = register.registerOne(name);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", "register");
        m.put("name", name);
        m.put("success", ok);
        return m;
    }
}
