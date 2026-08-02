package com.powergateway.testkit.socket;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * TCP Socket Mock 配置(v0.3.0 SOCK-4 · pg-testkit)。
 *
 * <p>YAML 前缀 socket-mock · 默认 enabled=false 避免占用用户端口。</p>
 * <p>v0.3.0 仅支持 XML_BOUNDARY 分帧 + UTF-8 编码 · host 场景足够。</p>
 * <p>length_prefix_be4/be8 与 GBK 编码留 v0.3.7 pg-testkit 增强。</p>
 */
@Component
@ConfigurationProperties(prefix = "socket-mock")
public class SocketMockConfig {

    /** 是否启用 mock server(默认 false · 避免与用户其他服务冲突) */
    private boolean enabled = false;

    /** 监听端口(默认 6500 · 与 docs/04-测试/模拟host.md 示例一致) */
    private int port = 6500;

    /** functionId → responseFile 规则列表 */
    private List<SocketMockRule> rules = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public List<SocketMockRule> getRules() { return rules; }
    public void setRules(List<SocketMockRule> rules) { this.rules = rules; }
}
