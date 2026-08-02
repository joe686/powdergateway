package com.powergateway.route;

import com.powergateway.aop.PerfStat;
import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ExecRequest;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 功能号路由入口(v0.3.1 CR-007 Task 1.4 · 免登)。
 *
 * <p>渠道方按约定字段送 functionId · PG 通过<b>双层机制</b>路由到内部接口:</p>
 * <ol>
 *   <li>{@link ChannelFunctionIdMapper#map} 走 FN-12 字典 · 渠道 functionId → PG 功能号(未命中 fallback 原值)</li>
 *   <li>{@link FunctionIdRouteService#lookup} PG 功能号 → interfaceId(Redis 缓存)</li>
 *   <li>转 {@link ExecDispatchService#dispatch} · 复用 ExecController 分发逻辑</li>
 * </ol>
 *
 * <p><b>渠道原始 functionId 透传</b>(用户 Q1 明确):body 追加 {@code _originalFunctionId} · 供下游联机业务处理。</p>
 *
 * <p>请求字段名从 {@code sys_config} 读取(key={@link #CFG_FIELD_KEY} · 默认 "functionId")· 便于渠道方定制。</p>
 */
@RestController
@RequestMapping("/api/route")
@Tag(name = "功能号路由", description = "v0.3.1 CR-007 · 渠道方按功能号路由到内部接口(免登)")
public class RouteController {

    public static final String CFG_FIELD_KEY = "route.function.id.field";
    public static final String CFG_FIELD_DEFAULT = "functionId";
    public static final String ORIGINAL_FUNCTION_ID_KEY = "_originalFunctionId";

    @Autowired private ChannelFunctionIdMapper channelMapper;
    @Autowired private FunctionIdRouteService routeService;
    @Autowired private ExecDispatchService execDispatchService;
    @Autowired private InterfaceConfigService interfaceConfigService;
    @Autowired private SysConfigService sysConfigService;

    /**
     * 渠道路由入口:提 functionId → 映射 → 查 interfaceId → 转 exec。
     */
    @PerfStat
    @PostMapping
    @Operation(summary = "渠道功能号路由(v0.3.1 CR-007)")
    public Result<Object> route(@RequestBody Map<String, Object> body) {
        if (body == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String fieldName = readFieldName();
        Object rawFnId = body.get(fieldName);
        if (rawFnId == null || rawFnId.toString().trim().isEmpty()) {
            throw new BusinessException(400, "功能号缺失(字段名 " + fieldName + ")");
        }
        String channelFnId = rawFnId.toString().trim();

        String pgFnId = channelMapper.map(channelFnId);
        Long interfaceId = routeService.lookup(pgFnId);
        if (interfaceId == null) {
            throw new BusinessException(404, "未找到匹配接口 · PG 功能号:" + pgFnId
                    + "(渠道 functionId=" + channelFnId + ")");
        }

        InterfaceConfig config = interfaceConfigService.getById(interfaceId);
        if (config == null) {
            throw new BusinessException(500, "路由到不存在的接口 id=" + interfaceId + " · 请清 Redis 缓存重试");
        }
        if ("disabled".equals(config.getStatus())) {
            throw new BusinessException(403, "接口已禁用");
        }
        if (!"published".equals(config.getStatus())) {
            throw new BusinessException(400, "接口未发布");
        }

        // 渠道原始 functionId 透传给下游联机(用户 Q1 明确要求)
        Map<String, Object> params = extractParams(body, fieldName);
        params.put(ORIGINAL_FUNCTION_ID_KEY, channelFnId);

        ExecRequest execReq = new ExecRequest();
        execReq.setParams(params);
        Object data = execDispatchService.dispatch(config, interfaceId, params, execReq);
        return Result.success(data);
    }

    private String readFieldName() {
        return sysConfigService.getString(CFG_FIELD_KEY, CFG_FIELD_DEFAULT);
    }

    /**
     * 从 body 里提取 exec 需要的 params · 支持两种约定:
     * <ul>
     *   <li>body 有 {@code params} 字段 · 走标准 ExecRequest 结构(嵌套 params)</li>
     *   <li>否则整个 body 视作 params(渠道简化协议)</li>
     * </ul>
     * 无论哪种 · functionId 字段本身会剥掉不入 params。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Map<String, Object> body, String fieldName) {
        Object inner = body.get("params");
        Map<String, Object> params;
        if (inner instanceof Map) {
            params = new HashMap<>((Map<String, Object>) inner);
        } else {
            params = new HashMap<>(body);
            params.remove(fieldName);
            params.remove("params");
        }
        return params;
    }
}
