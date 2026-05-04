package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.PortRouteMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.PortRoute;
import com.powergateway.model.dto.ConvertRequest;
import com.powergateway.model.dto.DispatchRequest;
import com.powergateway.model.dto.HeaderConfig;
import com.powergateway.model.dto.PortRouteSaveRequest;
import com.powergateway.utils.FormatType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * M1-7 端口分发路由 Service
 * <p>
 * 职责：
 * <ol>
 *   <li>端口路由 CRUD（list / save / delete）</li>
 *   <li>端口连通性探活（HTTP GET）</li>
 *   <li>{@link #dispatch} — 双向转换分发：请求加工→转发→应答加工→返回</li>
 * </ol>
 * <p>
 * 执行链（dispatch）：
 * <pre>
 * 1. ChannelRouter.matchChannelCode → channel_code
 * 2. 查 port_route（Redis 缓存 TTL 10min）
 * 3. [请求方向] 若 request_template_id 不为空 → 转换（复用 M1-6 链路）
 * 4. RestTemplate 转发到 port_address（失败重试 retry_count 次）
 * 5. [应答方向] 若 response_template_id 不为空 → 对 B 应答执行转换
 * 6. 返回最终应答给 A
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortRouteService {

    private final PortRouteMapper portRouteMapper;
    private final ChannelConfigService channelConfigService;
    private final ConvertService convertService;
    private final TemplateService templateService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    private static final String CACHE_PREFIX = "portRoute:";
    private static final long CACHE_TTL_SECONDS = 600;

    // ────────────────────────── CRUD ──────────────────────────────

    /**
     * 分页查询端口路由列表，支持渠道编码模糊搜索。
     */
    public Page<PortRoute> listRoutes(int page, int size, String channelCode) {
        LambdaQueryWrapper<PortRoute> wrapper = new LambdaQueryWrapper<>();
        if (channelCode != null && !channelCode.trim().isEmpty()) {
            wrapper.like(PortRoute::getChannelCode, channelCode.trim());
        }
        wrapper.orderByDesc(PortRoute::getCreateTime);
        Page<PortRoute> result = portRouteMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(r -> r.setHeaderConfig(parseHeaderConfig(r.getHeaderConfigRaw())));
        return result;
    }

    /**
     * 新增或更新端口路由。
     * <ul>
     *   <li>id == null：新增</li>
     *   <li>id != null：更新</li>
     * </ul>
     *
     * @return 路由 id
     */
    public Long saveRoute(PortRouteSaveRequest req) {
        validateSaveRequest(req);

        PortRoute route;
        if (req.getId() == null) {
            route = new PortRoute();
            route.setDeleted(0);
        } else {
            route = requireById(req.getId());
        }

        route.setChannelCode(req.getChannelCode());
        route.setPortAddress(req.getPortAddress());
        route.setPortMethod(req.getPortMethod() != null ? req.getPortMethod() : "POST");
        route.setTimeout(req.getTimeout() != null ? req.getTimeout() : 3000);
        route.setRetryCount(req.getRetryCount() != null ? req.getRetryCount() : 3);
        route.setRequestTemplateId(req.getRequestTemplateId());
        route.setResponseTemplateId(req.getResponseTemplateId());

        if (req.getHeaderConfig() != null) {
            try {
                route.setHeaderConfigRaw(objectMapper.writeValueAsString(req.getHeaderConfig()));
            } catch (Exception e) {
                log.warn("portRoute.headerConfig 序列化失败: {}", e.getMessage());
                route.setHeaderConfigRaw(null);
            }
        } else {
            route.setHeaderConfigRaw(null);
        }

        if (req.getId() == null) {
            portRouteMapper.insert(route);
        } else {
            portRouteMapper.updateById(route);
        }

        evictCache(route.getChannelCode());
        return route.getId();
    }

    /**
     * 逻辑删除端口路由。
     */
    public void deleteRoute(Long id) {
        PortRoute route = requireById(id);
        portRouteMapper.deleteById(id);
        evictCache(route.getChannelCode());
    }

    // ────────────────────────── 连通性测试 ──────────────────────────

    /**
     * 测试目标端口连通性（HTTP GET 探活）。
     *
     * @return 包含 success、message 的 Map
     */
    public Map<String, Object> testConnectivity(Long id) {
        PortRoute route = requireById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(route.getPortAddress(), String.class);
            boolean reachable = response.getStatusCode().is2xxSuccessful()
                    || response.getStatusCode().is3xxRedirection();
            result.put("success", reachable);
            result.put("httpStatus", response.getStatusCodeValue());
            result.put("message", reachable ? "端口连通正常" : "端口返回非成功状态码：" + response.getStatusCodeValue());
            log.info("连通性测试：url={}, status={}", route.getPortAddress(), response.getStatusCodeValue());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "端口不可达：" + e.getMessage());
            log.warn("连通性测试失败：url={}, error={}", route.getPortAddress(), e.getMessage());
        }
        return result;
    }

    // ────────────────────────── 分发核心逻辑 ──────────────────────────

    /**
     * 完整双向分发接口。
     * <p>
     * 执行链：渠道路由 → 请求转换（可选）→ 转发 B 系统 → 应答转换（可选）→ 返回
     *
     * @param req 分发请求
     * @return 包含 response（最终应答）、channelCode、costMs 的 Map
     */
    public Map<String, Object> dispatch(DispatchRequest req) {
        long start = System.currentTimeMillis();
        validateDispatchRequest(req);

        FormatType srcFormat = FormatType.parse(req.getSrcFormat());

        // 1. 通过渠道路由识别 channelCode
        String channelCode = channelConfigService.matchChannelCode(req.getMessage(), srcFormat);
        if (channelCode == null) {
            throw new BusinessException(400, "报文未命中任何渠道配置，无法路由");
        }

        // 2. 查 port_route（Redis 缓存）
        PortRoute route = getRouteCachedByChannelCode(channelCode);
        if (route == null) {
            throw new BusinessException(404, "渠道未配置端口路由：" + channelCode);
        }

        // 3. 请求方向转换（A→B）：有 requestTemplateId 则转换，否则原样
        String requestMsg = req.getMessage();
        if (route.getRequestTemplateId() != null) {
            ConvertRequest convertReq = new ConvertRequest();
            convertReq.setTemplateId(route.getRequestTemplateId());
            convertReq.setMessage(req.getMessage());
            convertReq.setSrcFormat(req.getSrcFormat());
            Map<String, Object> convertResult = convertService.convert(convertReq);
            requestMsg = (String) convertResult.get("result");
            log.debug("请求转换完成，channelCode={}, templateId={}", channelCode, route.getRequestTemplateId());
        }

        // 2b. 获取渠道 headerConfig 并两级合并（CHG-002）
        com.powergateway.model.ChannelConfig channelConfig =
                channelConfigService.getByChannelCode(channelCode);
        HeaderConfig channelHc = channelConfig != null
                ? channelConfigService.parseHeaderConfig(channelConfig.getHeaderConfigRaw()) : null;
        HeaderConfig routeHc = parseHeaderConfig(route.getHeaderConfigRaw());
        HeaderConfig effectiveHeader =
                com.powergateway.utils.HeaderConfigMerger.merge(channelHc, routeHc);

        // 4. 构造 HttpHeaders（CHG-002）
        HttpHeaders httpHeaders = buildHttpHeaders(effectiveHeader);

        // 4b. charset 转码：requestMsg(UTF-8) → 目标 charset bytes（CHG-002）
        String targetCharset = (effectiveHeader.getCharset() != null
                && !effectiveHeader.getCharset().isEmpty())
                ? effectiveHeader.getCharset() : "UTF-8";
        byte[] requestBytes = com.powergateway.utils.CharsetConverter.encodeToBytes(requestMsg, targetCharset);

        // 5. 转发到 B 系统（带重试）
        byte[] responseBytes = forwardWithRetryBytes(route, requestBytes, httpHeaders);
        log.debug("B 系统应答接收，channelCode={}, bodyLen={}", channelCode, responseBytes.length);

        // 5b. 应答 charset 反向转码（CHG-002）
        String bResponse = com.powergateway.utils.CharsetConverter.decodeFromBytes(responseBytes, targetCharset);

        // 6. 应答方向转换（B→A）：有 responseTemplateId 则转换，否则透传
        String finalResponse = bResponse;
        if (route.getResponseTemplateId() != null && bResponse != null) {
            // 获取应答模板的 srcFormat（即 B 应答的格式）
            ConvertTemplate respTemplate = templateService.getById(route.getResponseTemplateId());
            ConvertRequest respReq = new ConvertRequest();
            respReq.setTemplateId(route.getResponseTemplateId());
            respReq.setMessage(bResponse);
            respReq.setSrcFormat(respTemplate.getSrcFormat());
            Map<String, Object> respResult = convertService.convert(respReq);
            finalResponse = (String) respResult.get("result");
            log.debug("应答转换完成，channelCode={}, templateId={}", channelCode, route.getResponseTemplateId());
        }

        long costMs = System.currentTimeMillis() - start;
        log.info("分发完成，channelCode={}, costMs={}", channelCode, costMs);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("response", finalResponse);
        response.put("channelCode", channelCode);
        response.put("costMs", costMs);
        response.put("responseHeaders", effectiveHeader.getResponseHeaders()); // 可为 null，Controller 用（CHG-002）
        return response;
    }

    // ────────────────────────── 转发（含重试）──────────────────────────

    /**
     * 带重试的 HTTP 转发（字节级别，支持 charset 转码）（CHG-002）。
     */
    private byte[] forwardWithRetryBytes(PortRoute route, byte[] requestBytes, HttpHeaders headers) {
        String method = route.getPortMethod() != null ? route.getPortMethod().toUpperCase() : "POST";
        int maxAttempts = route.getRetryCount() != null && route.getRetryCount() > 0
                ? route.getRetryCount() : 1;

        HttpEntity<byte[]> entity = new HttpEntity<>(requestBytes, headers);
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(
                        route.getPortAddress(),
                        HttpMethod.valueOf(method),
                        entity,
                        byte[].class);
                return response.getBody() != null ? response.getBody() : new byte[0];
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("转发第{}次失败，准备重试，url={}, error={}", attempt, route.getPortAddress(), e.getMessage());
                } else {
                    log.error("转发失败（已重试{}次），url={}, error={}", maxAttempts, route.getPortAddress(), e.getMessage());
                }
            }
        }
        throw new BusinessException(502, "端口转发失败（已重试" + maxAttempts + "次）："
                + (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    /**
     * 根据合并后的 HeaderConfig 构造出向 HttpHeaders（CHG-002）。
     * Content-Type 未配置时默认 text/plain（保持原有行为）。
     */
    private HttpHeaders buildHttpHeaders(HeaderConfig config) {
        HttpHeaders headers = new HttpHeaders();
        String ct = config.getContentType();
        String charset = config.getCharset();
        if (ct != null && !ct.isEmpty()) {
            String fullCt = (charset != null && !charset.isEmpty()) ? ct + "; charset=" + charset : ct;
            headers.set(HttpHeaders.CONTENT_TYPE, fullCt);
        } else {
            headers.setContentType(MediaType.TEXT_PLAIN);
        }
        if (config.getRequestHeaders() != null) {
            config.getRequestHeaders().forEach(headers::set);
        }
        return headers;
    }

    // ────────────────────────── 缓存 ──────────────────────────────

    /**
     * 按 channelCode 从 Redis 缓存查询端口路由（缓存未命中时查库并回填，TTL 10min）。
     */
    private PortRoute getRouteCachedByChannelCode(String channelCode) {
        String cacheKey = CACHE_PREFIX + channelCode;
        StringRedisTemplate redis = redisProvider.getIfAvailable();

        if (redis != null) {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    log.debug("端口路由缓存命中，key={}", cacheKey);
                    return objectMapper.readValue(cached, PortRoute.class);
                } catch (Exception e) {
                    log.warn("端口路由缓存反序列化失败，降级查库: {}", e.getMessage());
                }
            }
        }

        LambdaQueryWrapper<PortRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PortRoute::getChannelCode, channelCode);
        PortRoute route = portRouteMapper.selectOne(wrapper);

        if (route != null && redis != null) {
            try {
                redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(route),
                        CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("端口路由已写入缓存，key={}, ttl={}s", cacheKey, CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("端口路由缓存写入失败: {}", e.getMessage());
            }
        }

        return route;
    }

    private void evictCache(String channelCode) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null && channelCode != null) {
            redis.delete(CACHE_PREFIX + channelCode);
            log.debug("端口路由缓存已清除，channelCode={}", channelCode);
        }
    }

    /**
     * 将 JSON 字符串反序列化为 HeaderConfig 对象。
     * json 为 null/空时返回 null；解析失败时记录 warn 日志并返回 null。
     */
    private HeaderConfig parseHeaderConfig(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, HeaderConfig.class);
        } catch (Exception e) {
            log.warn("portRoute.headerConfig 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    private PortRoute requireById(Long id) {
        PortRoute route = portRouteMapper.selectById(id);
        if (route == null) {
            throw new BusinessException(404, "端口路由不存在，id=" + id);
        }
        return route;
    }

    private void validateSaveRequest(PortRouteSaveRequest req) {
        if (req.getChannelCode() == null || req.getChannelCode().trim().isEmpty()) {
            throw new BusinessException(400, "渠道编码 channelCode 不能为空");
        }
        if (req.getPortAddress() == null || req.getPortAddress().trim().isEmpty()) {
            throw new BusinessException(400, "目标端口地址 portAddress 不能为空");
        }
    }

    private void validateDispatchRequest(DispatchRequest req) {
        if (req.getMessage() == null || req.getMessage().trim().isEmpty()) {
            throw new BusinessException(400, "源报文 message 不能为空");
        }
        if (req.getSrcFormat() == null || req.getSrcFormat().trim().isEmpty()) {
            throw new BusinessException(400, "源格式 srcFormat 不能为空");
        }
    }
}
