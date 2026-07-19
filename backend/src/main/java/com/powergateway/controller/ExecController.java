package com.powergateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.aop.PerfStat;
import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.ExecRequest;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.utils.AcceptNegotiator;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 对外执行入口（M2-7），无需 Sa-Token 登录。
 * 已在 SaTokenConfig 中排除 /api/exec/**。
 * FN-06：集成 AcceptNegotiator，支持 JSON/XML/CSV/FORM_DATA 四格式协商。
 */
@RestController
@RequestMapping("/api/exec")
@Tag(name = "接口执行", description = "已发布接口的对外统一执行入口（无需登录）")
public class ExecController {

    @Autowired private InterfaceConfigService service;
    @Autowired private FormatConverter formatConverter;
    @Autowired private ObjectMapper objectMapper;

    @PerfStat
    @PostMapping(value = "/{interfaceId}",
        produces = {
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.APPLICATION_XML_VALUE,
            "text/csv",
            MediaType.APPLICATION_FORM_URLENCODED_VALUE
        })
    @Operation(summary = "执行已发布接口（支持 Accept/format 协商 JSON/XML/CSV/FORM_DATA）")
    public ResponseEntity<?> execute(HttpServletRequest httpReq,
                                     @PathVariable Long interfaceId,
                                     @RequestParam(value = "format", required = false) String format,
                                     @RequestBody(required = false) ExecRequest req) {
        if (req == null) req = new ExecRequest();
        Map<String, Object> params = req.getParams() != null ? req.getParams() : new HashMap<>();

        InterfaceConfig config = service.getById(interfaceId);

        if ("disabled".equals(config.getStatus())) {
            return ResponseEntity.ok(Result.fail(403, "接口已禁用"));
        }
        if (!"published".equals(config.getStatus())) {
            return ResponseEntity.ok(Result.fail(400, "接口未发布"));
        }

        Object dataObj = dispatchByType(config, interfaceId, params, req);

        FormatType target;
        try {
            target = AcceptNegotiator.negotiate(httpReq, format, config.getResponseFormat());
        } catch (IllegalArgumentException | BusinessException e) {
            return ResponseEntity.badRequest().body(Result.fail(400, "未知响应格式: " + format));
        }

        if (target == FormatType.JSON) {
            // 保持 M2-7 契约：JSON 路径仍返回 Result<T>
            return ResponseEntity.ok(Result.success(dataObj));
        }
        return serializeNonJson(dataObj, target, config);
    }

    private Object dispatchByType(InterfaceConfig config, Long id,
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
            default:
                throw new BusinessException(400, "不支持的接口类型: " + config.getType());
        }
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<String> serializeNonJson(Object data, FormatType target,
                                                    InterfaceConfig config) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        if (data instanceof List) {
            // XML 需要单一根节点，包装为 {rows: [...]}
            wrapped.put("rows", data);
        } else {
            wrapped.put("data", data);
        }
        String body = formatConverter.serializeMap(wrapped, target);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(mediaTypeOf(target));
        // 自定义响应头透传
        if (config.getResponseHeaders() != null && !config.getResponseHeaders().isEmpty()) {
            try {
                Map<String, String> extra = objectMapper.readValue(config.getResponseHeaders(), Map.class);
                extra.forEach(h::add);
            } catch (Exception ignore) { /* 配置格式错时忽略，不阻塞主流程 */ }
        }
        return new ResponseEntity<>(body, h, HttpStatus.OK);
    }

    private MediaType mediaTypeOf(FormatType f) {
        switch (f) {
            case XML:       return MediaType.APPLICATION_XML;
            case CSV:       return MediaType.parseMediaType("text/csv;charset=UTF-8");
            case FORM_DATA: return MediaType.APPLICATION_FORM_URLENCODED;
            default:        return MediaType.APPLICATION_JSON;
        }
    }
}
