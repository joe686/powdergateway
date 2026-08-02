package com.powergateway.socket.inbound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.socket.route.InboundSocketOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 入站 Socket Server 生命周期管理器(v0.3.2 SOCK-5-A · Task 7)。
 *
 * <p><b>职责</b>:</p>
 * <ul>
 *   <li>启动时扫描所有 type=INBOUND_SOCKET + status=published 的接口 · 拉起对应 Server</li>
 *   <li>接口发布/下线时手动调 start(interfaceId) / stop(interfaceId)(InterfaceConfigService.publish 集成 · 或用户重启加载)</li>
 *   <li>关闭时优雅停机所有 Server</li>
 * </ul>
 *
 * <p><b>简化范围</b>(v0.3.2 · 后续 minor 精细化):</p>
 * <ul>
 *   <li>自动 lifecycle hook(接口 publish/disable/delete 事件监听)留后续 minor</li>
 *   <li>v0.3.2 用户可通过重启 backend 让新 INBOUND_SOCKET 接口生效 · 或通过 manager API 手动调</li>
 * </ul>
 */
@Component
public class SocketInboundServerManager {

    private static final Logger log = LoggerFactory.getLogger(SocketInboundServerManager.class);

    private final Map<Long, SocketInboundServer> servers = new ConcurrentHashMap<>();

    @Autowired private InterfaceConfigMapper interfaceConfigMapper;
    @Autowired private InboundSocketOrchestrator orchestrator;
    @Autowired private ObjectMapper objectMapper;

    /**
     * 启动时扫描已发布的 INBOUND_SOCKET 接口 · 拉起对应 Server。
     */
    @PostConstruct
    public void init() {
        try {
            LambdaQueryWrapper<InterfaceConfig> q = new LambdaQueryWrapper<>();
            q.eq(InterfaceConfig::getType, "INBOUND_SOCKET").eq(InterfaceConfig::getStatus, "published");
            List<InterfaceConfig> published = interfaceConfigMapper.selectList(q);
            log.info("SOCK-5-A · SocketInboundServerManager 启动 · 发现 {} 个已发布 INBOUND_SOCKET 接口", published.size());
            for (InterfaceConfig cfg : published) {
                try {
                    startServer(cfg);
                } catch (Exception e) {
                    log.error("SOCK-5-A · 启动接口 {} Server 失败:{}", cfg.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("SOCK-5-A · SocketInboundServerManager 初始化失败(可能启动早期表未就绪):{}", e.getMessage());
        }
    }

    /**
     * 关闭时优雅停机所有 Server。
     */
    @PreDestroy
    public void shutdown() {
        log.info("SOCK-5-A · SocketInboundServerManager 关闭 · 停机 {} 个 Server", servers.size());
        servers.values().forEach(SocketInboundServer::stop);
        servers.clear();
    }

    /**
     * 启动指定接口的 Server(可外部调 · 如 InterfaceConfigService.publish 后)。
     */
    public synchronized void start(Long interfaceId) {
        if (servers.containsKey(interfaceId)) {
            log.warn("SOCK-5-A · 接口 {} Server 已在运行 · 跳过重启", interfaceId);
            return;
        }
        InterfaceConfig cfg = interfaceConfigMapper.selectById(interfaceId);
        if (cfg == null || !"INBOUND_SOCKET".equals(cfg.getType())) {
            throw new BusinessException("接口 " + interfaceId + " 不存在或非 INBOUND_SOCKET 类型");
        }
        startServer(cfg);
    }

    /**
     * 停止指定接口的 Server(接口下线/删除时调)。
     */
    public synchronized void stop(Long interfaceId) {
        SocketInboundServer server = servers.remove(interfaceId);
        if (server != null) {
            server.stop();
            log.info("SOCK-5-A · 接口 {} Server 已停机", interfaceId);
        }
    }

    /** 内部启动 · 从 config_json.inbound 反解 SocketInboundConfig */
    @SuppressWarnings("unchecked")
    private void startServer(InterfaceConfig cfg) {
        Map<String, Object> configMap;
        try {
            configMap = objectMapper.readValue(cfg.getConfigJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("接口 " + cfg.getId() + " config_json 解析失败:" + e.getMessage());
        }
        Object inbound = configMap.get("inbound");
        if (!(inbound instanceof Map)) {
            throw new BusinessException("接口 " + cfg.getId() + " config_json 缺 inbound 段");
        }
        SocketInboundConfig cfg2 = SocketInboundConfig.fromMap((Map<String, Object>) inbound);
        SocketInboundServer server = new SocketInboundServer(cfg2, orchestrator);
        try {
            server.start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("接口 " + cfg.getId() + " Server 启动被中断");
        }
        servers.put(cfg.getId(), server);
        log.info("SOCK-5-A · 接口 {} INBOUND_SOCKET Server 启动 · port={}", cfg.getId(), cfg2.getPort());
    }

    /** 返回当前运行 Server 数(供测试/监控)*/
    public int runningCount() {
        return servers.size();
    }

    /** 判断接口 Server 是否运行中 */
    public boolean isRunning(Long interfaceId) {
        SocketInboundServer server = servers.get(interfaceId);
        return server != null && server.isRunning();
    }
}
