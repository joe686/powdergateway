package com.powergateway.route;

import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ExecRequest;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.socket.SocketExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 接口执行分发共享 Service(v0.3.1 CR-007 Task 1.4 抽取)。
 *
 * <p>从 ExecController 抽出 · 供 ExecController 与 RouteController 共享 · 保持分发逻辑单一来源。</p>
 *
 * <p>支持类型:SELECT / INSERT / UPDATE / DELETE / SOCKET(v0.3.0 SOCK-1)。</p>
 */
@Service
public class ExecDispatchService {

    @Autowired private InterfaceConfigService service;
    @Autowired private SocketExecutor socketExecutor;

    /**
     * 按接口类型分发到对应执行器。
     *
     * @param config 接口配置(status=published · 已校验)
     * @param id     接口 id
     * @param params 请求参数(已含 v0.3.1 CR-007 加的 _originalFunctionId · 传给下游)
     * @param req    ExecRequest(带 page/pageSize)
     * @return 执行结果(Object)
     */
    public Object dispatch(InterfaceConfig config, Long id,
                           Map<String, Object> params, ExecRequest req) {
        switch (config.getType()) {
            case "SELECT":
                return service.executeQuery(id, params, req.getPage(), req.getPageSize());
            case "INSERT":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(id).setOpType("INSERT")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return service.executeInsert(id, params);
            case "UPDATE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(id).setOpType("UPDATE")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return service.executeUpdate(id, params);
            case "DELETE":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(id).setOpType("DELETE")
                        .setTargetDb(config.getDbConnectionId() != null
                                ? config.getDbConnectionId().toString() : "unknown"));
                return service.executeDelete(id, params);
            case "SOCKET":
                AuditContextHolder.set(new AuditContext()
                        .setInterfaceId(id).setOpType("SOCKET_EXEC")
                        .setTargetDb("socket"));
                return socketExecutor.execute(config, params);
            default:
                throw new BusinessException(400, "不支持的接口类型: " + config.getType());
        }
    }
}
