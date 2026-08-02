package com.powergateway.socket.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.service.registry.RegistryFacade;
import com.powergateway.service.registry.ServiceInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

/**
 * HTTP 出站执行器(v0.3.2 SOCK-5-B · Task 4)。
 *
 * <p>Eureka discover(复用 REG-1 {@link RegistryFacade}) · RestTemplate POST · X-Trace-Id header 透传。</p>
 *
 * <p><b>用法</b>:入站编排调 exec(req, jsonBody, traceId) · 返联机 JSON 字符串。</p>
 *
 * <p><b>注</b>:Eureka selfRegister(让联机能反向发现 PG)在 v0.3.2 保留 EurekaRegistryClient L55-60 占位注释状态,
 * 独立 minor 补齐(需 spring-cloud-starter-netflix-eureka-client 或手动装配 ApplicationInfoManager)。
 * 出站 discover 不受影响(REG-1 v0.1.0 已 verify)。</p>
 */
@Service
public class HttpOutboundExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpOutboundExecutor.class);

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_ORIGINAL_FUNCTION_ID = "X-Original-Function-Id";

    @Autowired private RegistryFacade registryFacade;
    @Autowired private ObjectMapper objectMapper;

    /**
     * 出站执行:discover → 选实例 → POST JSON body → 返应答字符串。
     *
     * @param req                HTTP 出站请求配置
     * @param jsonBody           请求 body(将序列化为 JSON)
     * @param traceId            trace ID(透传到下游联机 · 可 null)
     * @param originalFunctionId 渠道原始 functionId(用户 Q1 明确 · 头部透传 · 可 null)
     * @return 联机应答字符串(JSON)
     */
    public String exec(HttpOutboundRequest req, Object jsonBody, String traceId, String originalFunctionId) {
        Optional<ServiceInstance> instanceOpt = registryFacade.choose(req.getApplicationName());
        if (!instanceOpt.isPresent()) {
            throw new BusinessException(503, "SOCK-5-B · Eureka 未发现应用 " + req.getApplicationName()
                    + " · 请 verify 联机已注册 + Eureka 配置");
        }
        ServiceInstance instance = instanceOpt.get();
        String url = instance.getScheme() + "://" + instance.getIp() + ":" + instance.getPort() + req.getPath();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (traceId != null && !traceId.isEmpty()) {
            headers.set(HEADER_TRACE_ID, traceId);
        }
        if (originalFunctionId != null && !originalFunctionId.isEmpty()) {
            headers.set(HEADER_ORIGINAL_FUNCTION_ID, originalFunctionId);
        }

        String bodyJson;
        try {
            bodyJson = (jsonBody instanceof String) ? (String) jsonBody
                    : objectMapper.writeValueAsString(jsonBody);
        } catch (Exception e) {
            throw new BusinessException("SOCK-5-B · body 序列化失败:" + e.getMessage());
        }

        RestTemplate rt = buildRestTemplate(req.getConnTimeoutMs(), req.getReadTimeoutMs());
        long start = System.currentTimeMillis();
        try {
            HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);
            HttpMethod httpMethod = HttpMethod.resolve(req.getMethod());
            if (httpMethod == null) {
                throw new BusinessException("SOCK-5-B · 不支持的 HTTP method:" + req.getMethod());
            }
            ResponseEntity<String> resp = rt.exchange(url, httpMethod, entity, String.class);
            long cost = System.currentTimeMillis() - start;
            log.info("SOCK-5-B · HTTP 出站成功 · traceId={} · url={} · status={} · cost={}ms",
                    traceId, url, resp.getStatusCodeValue(), cost);
            return resp.getBody();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.warn("SOCK-5-B · HTTP 出站失败 · traceId={} · url={} · cost={}ms · {}",
                    traceId, url, cost, e.getMessage());
            throw new BusinessException(502, "SOCK-5-B · 联机调用失败(" + url + "):" + e.getMessage());
        }
    }

    private RestTemplate buildRestTemplate(int connMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}
