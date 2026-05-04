package com.powergateway.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.common.Result;
import com.powergateway.model.PortRoute;
import com.powergateway.model.dto.DispatchRequest;
import com.powergateway.model.dto.PortRouteSaveRequest;
import com.powergateway.service.PortRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M1-7 端口分发路由接口
 * <p>
 * 端点列表：
 * <ul>
 *   <li>GET  /api/port-route/list          — 分页查询端口路由</li>
 *   <li>POST /api/port-route/save          — 新增/更新端口路由</li>
 *   <li>DELETE /api/port-route/{id}        — 逻辑删除</li>
 *   <li>POST /api/port-route/{id}/test     — 测试目标端口连通性</li>
 *   <li>POST /api/dispatch                 — 完整双向分发接口</li>
 * </ul>
 */
@Tag(name = "M1-7 端口分发路由", description = "渠道与目标端口绑定配置 + 双向转换分发")
@RestController
@RequiredArgsConstructor
public class PortRouteController {

    private final PortRouteService portRouteService;

    // ─────────────────── CRUD ───────────────────

    @Operation(summary = "分页查询端口路由列表", description = "支持渠道编码模糊搜索")
    @GetMapping("/api/port-route/list")
    public Result<Page<PortRoute>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String channelCode) {
        return Result.success(portRouteService.listRoutes(page, size, channelCode));
    }

    @Operation(summary = "新增/更新端口路由", description = "id 为空时新增，id 不为空时更新；返回路由 id")
    @PostMapping("/api/port-route/save")
    public Result<Long> save(@RequestBody PortRouteSaveRequest req) {
        return Result.success(portRouteService.saveRoute(req));
    }

    @Operation(summary = "删除端口路由", description = "逻辑删除")
    @DeleteMapping("/api/port-route/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        portRouteService.deleteRoute(id);
        return Result.success();
    }

    // ─────────────────── 连通性测试 ───────────────────

    @Operation(summary = "测试目标端口连通性", description = "HTTP GET 探活，返回 success 和 httpStatus")
    @PostMapping("/api/port-route/{id}/test")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        return Result.success(portRouteService.testConnectivity(id));
    }

    // ─────────────────── 分发接口 ───────────────────

    @Operation(
            summary = "端口分发（双向转换）",
            description = "执行链：渠道路由 → 请求转换（A→B）→ 转发 B 系统 → 应答转换（B→A）→ 返回。"
                    + "无请求模板则原样转发，无应答模板则透传。"
    )
    @PostMapping("/api/dispatch")
    public Result<Map<String, Object>> dispatch(
            @RequestBody DispatchRequest req,
            javax.servlet.http.HttpServletResponse httpResponse) {
        Map<String, Object> result = portRouteService.dispatch(req);
        // 注入 responseHeaders 到 servlet response（CHG-002）
        @SuppressWarnings("unchecked")
        Map<String, String> respHeaders = (Map<String, String>) result.remove("responseHeaders");
        if (respHeaders != null) {
            respHeaders.forEach(httpResponse::setHeader);
        }
        return Result.success(result);
    }
}
