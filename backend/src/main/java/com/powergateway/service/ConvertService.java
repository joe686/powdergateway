package com.powergateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.dto.ConvertRequest;
import com.powergateway.model.dto.FieldMappingRule;
import com.powergateway.utils.FieldProcessor;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import com.powergateway.utils.processor.ProcessRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * M1-6 报文转换调用服务
 * <p>
 * 执行链：读模板（Redis 缓存 TTL 10min）→ 格式解析 → 字段映射 → 字段加工 → 序列化返回
 * <p>
 * Redis 为可选依赖：测试环境通过 {@link ObjectProvider} 惰性注入，不可用时自动降级查库。
 * <p>
 * process_rule 存储格式（JSON Map）：
 * <pre>
 * {
 *   "userId":  [{"type": "TRIM",      "params": {"mode": "BOTH"}}],
 *   "amount":  [{"type": "TYPE_CAST", "params": {"targetType": "INTEGER"}}]
 * }
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConvertService {

    private final TemplateService templateService;
    private final ChannelConfigService channelConfigService;
    private final FormatConverter formatConverter;
    private final FieldProcessor fieldProcessor;
    private final ObjectMapper objectMapper;
    /** 惰性注入，测试环境 Redis 不可用时 getIfAvailable() 返回 null */
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final SysConfigService sysConfigService;

    private static final String TEMPLATE_CACHE_PREFIX = "template:";

    /**
     * 执行报文转换全流程。
     *
     * @param req 转换请求
     * @return 包含 result（转换结果）、targetFormat、costMs 的 Map
     */
    public Map<String, Object> convert(ConvertRequest req) {
        long start = System.currentTimeMillis();

        validateRequest(req);
        FormatType srcFormat = FormatType.parse(req.getSrcFormat());

        // 1. 确定模板 ID：直接指定 > 渠道路由
        Long templateId = req.getTemplateId();
        if (templateId == null) {
            templateId = channelConfigService.match(req.getMessage(), srcFormat);
            if (templateId == null) {
                throw new BusinessException(400, "未传 templateId 且报文未命中任何渠道配置，无法确定转换模板");
            }
            log.debug("渠道路由命中模板 id={}", templateId);
        }

        // 2. 从 Redis 缓存读取模板（缓存未命中时查库并回填，TTL 10min）
        ConvertTemplate template = getTemplateCached(templateId);
        FormatType targetFormat = FormatType.parse(template.getTargetFormat());

        // 3. 解析源报文为中间 Map
        Map<String, Object> srcMap = formatConverter.parseToMap(req.getMessage(), srcFormat);

        // 4. 应用字段映射规则（mapping_rule）
        List<FieldMappingRule> mappingRules = deserializeMappingRules(template.getMappingRule());
        Map<String, Object> mappedMap = applyMapping(srcMap, mappingRules);

        // 5. 应用字段加工规则（process_rule，可选）
        Map<String, List<ProcessRule>> processRules = deserializeProcessRules(template.getProcessRule());
        if (!processRules.isEmpty()) {
            Map<String, String> strValues = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : mappedMap.entrySet()) {
                strValues.put(entry.getKey(), entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
            }
            Map<String, String> processed = fieldProcessor.processBatch(strValues, processRules);
            mappedMap.putAll(processed);
        }

        // 6. 序列化为目标格式（直接从 Map 序列化，避免 JSON 中转）
        String resultStr = formatConverter.serializeMap(mappedMap, targetFormat);

        long costMs = System.currentTimeMillis() - start;
        log.debug("报文转换完成，templateId={}, {}→{}, costMs={}", templateId, srcFormat, targetFormat, costMs);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result", resultStr);
        response.put("targetFormat", targetFormat.name());
        response.put("costMs", costMs);
        return response;
    }

    // ─────────────────────── 模板缓存（Redis TTL 10min）───────────────────────

    private ConvertTemplate getTemplateCached(Long templateId) {
        String cacheKey = TEMPLATE_CACHE_PREFIX + templateId;
        StringRedisTemplate redis = redisProvider.getIfAvailable();

        if (redis != null) {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    log.debug("模板缓存命中，key={}", cacheKey);
                    return objectMapper.readValue(cached, ConvertTemplate.class);
                } catch (Exception e) {
                    log.warn("模板缓存反序列化失败，降级查库: {}", e.getMessage());
                }
            }
        }

        ConvertTemplate template = templateService.getById(templateId);

        if (redis != null) {
            try {
                redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(template),
                        sysConfigService.getInt("cache.template.ttl", 600), TimeUnit.SECONDS);
                log.debug("模板已写入缓存，key={}, ttl={}s", cacheKey, sysConfigService.getInt("cache.template.ttl", 600));
            } catch (Exception e) {
                log.warn("模板缓存写入失败: {}", e.getMessage());
            }
        }

        return template;
    }

    /**
     * 删除指定模板的缓存（模板更新时调用，保证缓存一致性）。
     * 当前由 TemplateService 更新/删除后调用。
     */
    public void evictTemplateCache(Long templateId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            redis.delete(TEMPLATE_CACHE_PREFIX + templateId);
            log.debug("模板缓存已清除，templateId={}", templateId);
        }
    }

    // ─────────────────────── 内部辅助方法 ────────────────────────────────────

    /**
     * 应用字段映射规则：srcField（或 fixedValue）→ targetField。
     * <p>
     * <ul>
     *   <li>fixedValue 不为 null 时优先使用固定值</li>
     *   <li>srcField 有对应来源字段时取其值</li>
     *   <li>规则为空时原样返回 srcMap</li>
     *   <li>规则命中结果为空时（所有规则均无法解析）也原样返回 srcMap（保底）</li>
     * </ul>
     */
    private Map<String, Object> applyMapping(Map<String, Object> srcMap, List<FieldMappingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return new LinkedHashMap<>(srcMap);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldMappingRule rule : rules) {
            String target = rule.getTargetField();
            if (target == null || target.trim().isEmpty()) continue;

            if (rule.getFixedValue() != null) {
                result.put(target, rule.getFixedValue());
            } else if (rule.getSrcField() != null) {
                Object val = getNestedValue(srcMap, rule.getSrcField());
                if (val != null) {
                    result.put(target, val);
                }
            }
        }
        return result.isEmpty() ? new LinkedHashMap<>(srcMap) : result;
    }

    /**
     * 按点号路径取嵌套 Map 中的值。
     * 例如 "head.FunctionId" 先取 srcMap["head"]，再取其中的 "FunctionId"。
     * 不含点号时等同于直接 get。
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String path) {
        int dot = path.indexOf('.');
        if (dot < 0) {
            return map.get(path);
        }
        Object parent = map.get(path.substring(0, dot));
        if (parent instanceof Map) {
            return getNestedValue((Map<String, Object>) parent, path.substring(dot + 1));
        }
        return null;
    }

    private List<FieldMappingRule> deserializeMappingRules(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<FieldMappingRule>>() {});
        } catch (Exception e) {
            log.warn("映射规则反序列化失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 反序列化字段加工规则。格式：Map&lt;targetField, List&lt;ProcessRule&gt;&gt;
     * 反序列化失败时静默降级（不影响转换主流程）。
     */
    private Map<String, List<ProcessRule>> deserializeProcessRules(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, List<ProcessRule>>>() {});
        } catch (Exception e) {
            log.warn("字段加工规则反序列化失败（跳过加工步骤）: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private void validateRequest(ConvertRequest req) {
        if (req.getMessage() == null || req.getMessage().trim().isEmpty()) {
            throw new BusinessException(400, "源报文 message 不能为空");
        }
        if (req.getSrcFormat() == null || req.getSrcFormat().trim().isEmpty()) {
            throw new BusinessException(400, "源格式 srcFormat 不能为空");
        }
    }
}
