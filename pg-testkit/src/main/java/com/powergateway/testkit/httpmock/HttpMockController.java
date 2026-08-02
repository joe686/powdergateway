package com.powergateway.testkit.httpmock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * pg-testkit HTTP Mock 联机(v0.3.2 SOCK-5-B · Task 5 · FB-053 简化版)。
 *
 * <p>挂在 pg-testkit 主 web(端口 8081)· 路径 {@code /mock/**} 通配 · 按 URL 应答 JSON。</p>
 *
 * <p><b>应答优先级</b>:</p>
 * <ol>
 *   <li>从 body 提 head.FunctionId 或 [0].head.FunctionId</li>
 *   <li>加载 classpath {@code mocks/http/{functionId}.json}</li>
 *   <li>加载失败 fallback 通用 {@code mocks/http/default.json}</li>
 *   <li>再失败返 {@code {"head":{...},"body":{"err":"no mock"}}}</li>
 * </ol>
 *
 * <p><b>限制</b>(v0.3.2 简化):</p>
 * <ul>
 *   <li>不做 Eureka 注册 · 用户手工在 Eureka 里注册 pg-testkit 应用名(如 lcpt-hstc-online-query · IP=127.0.0.1 · port=8081)</li>
 *   <li>或者用户改 REG-1 配置为静态实例列表 · 指向 pg-testkit · 后续 minor 加 EurekaMultiAppSelfRegister 自动化</li>
 * </ul>
 */
@Controller
@RequestMapping("/mock")
public class HttpMockController {

    private static final Logger log = LoggerFactory.getLogger(HttpMockController.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping(value = "/**", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.GET},
            produces = "application/json;charset=UTF-8")
    @ResponseBody
    public String handle(HttpServletRequest request,
                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                         @RequestHeader(value = "X-Original-Function-Id", required = false) String originalFnId,
                         @RequestBody(required = false) String body) {
        String path = request.getRequestURI();
        String functionId = extractFunctionId(body);
        log.info("SOCK-5-B Mock · path={} · X-Trace-Id={} · X-Original-Function-Id={} · body FunctionId={} · bodySize={}",
                path, traceId, originalFnId, functionId, body == null ? 0 : body.length());
        return resolveResponse(functionId, traceId);
    }

    @SuppressWarnings("unchecked")
    private String extractFunctionId(String body) {
        if (body == null || body.isEmpty()) return null;
        try {
            Object json = objectMapper.readValue(body, Object.class);
            if (json instanceof Map) {
                return findFunctionId((Map<String, Object>) json);
            }
            if (json instanceof java.util.List) {
                java.util.List<Object> list = (java.util.List<Object>) json;
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    return findFunctionId((Map<String, Object>) list.get(0));
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String findFunctionId(Map<String, Object> map) {
        if (map == null) return null;
        if (map.containsKey("FunctionId")) return String.valueOf(map.get("FunctionId"));
        Object head = map.get("head");
        if (head instanceof Map && ((Map<String, Object>) head).containsKey("FunctionId")) {
            return String.valueOf(((Map<String, Object>) head).get("FunctionId"));
        }
        for (Object v : map.values()) {
            if (v instanceof Map) {
                String found = findFunctionId((Map<String, Object>) v);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String resolveResponse(String functionId, String traceId) {
        String content = null;
        if (functionId != null) {
            content = tryRead("mocks/http/" + functionId + ".json");
        }
        if (content == null) {
            content = tryRead("mocks/http/default.json");
        }
        if (content == null) {
            // fallback:构造最小可用应答
            Map<String, Object> resp = new LinkedHashMap<>();
            Map<String, Object> head = new LinkedHashMap<>();
            head.put("FunctionId", functionId == null ? "unknown" : functionId);
            head.put("ErrorNo", "9999");
            head.put("ErrorInfo", "no mock file for " + functionId);
            resp.put("head", head);
            resp.put("body", new HashMap<>());
            try {
                content = objectMapper.writeValueAsString(new Object[]{resp});
            } catch (Exception e) {
                content = "[{\"head\":{\"ErrorNo\":\"9999\"},\"body\":{}}]";
            }
        }
        return content;
    }

    private String tryRead(String path) {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
