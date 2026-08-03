package com.powergateway.testkit.eureka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.3.11 CHG-054 · pg-testkit Eureka 多应用自注册配置。
 *
 * <p>application.yml 段:</p>
 * <pre>
 * pg-testkit:
 *   eureka:
 *     enabled: false                    # 默认关闭 · 需外部 Eureka Server
 *     server-url: http://127.0.0.1:8761/eureka
 *     applications:
 *       - name: pg-internal
 *         ip: 127.0.0.1
 *         port: 8080
 *         scheme: http
 *       - name: bank-svc
 *         ip: 127.0.0.1
 *         port: 9999
 *       - name: host-svc
 *         ip: 127.0.0.1
 *         port: 9998
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "pg-testkit.eureka")
@Data
public class EurekaAppConfig {

    /** 是否启用自注册 · 默认关闭 · 需用户显式开启且提供 Eureka Server 地址 */
    private boolean enabled = false;

    /** Eureka Server URL · Netflix 惯例以 /eureka 或 /eureka/ 结尾 */
    private String serverUrl = "http://127.0.0.1:8761/eureka";

    /** 待注册的假应用列表 · @PostConstruct 批量注册 */
    private List<AppInstance> applications = new ArrayList<>();

    @Data
    public static class AppInstance {
        /** 服务名 · Eureka APP 名(自动大写) · 如 pg-internal / bank-svc */
        private String name;
        /** 实例 IP · 默认 127.0.0.1 */
        private String ip = "127.0.0.1";
        /** 实例端口 */
        private int port;
        /** 协议 · http / https */
        private String scheme = "http";
    }
}
