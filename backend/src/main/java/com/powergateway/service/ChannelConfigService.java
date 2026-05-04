package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.ChannelConfigMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ChannelConfig;
import com.powergateway.model.dto.ChannelSaveRequest;
import com.powergateway.model.dto.HeaderConfig;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * M1-4 渠道模板管理 Service
 * <p>
 * 职责：
 * <ol>
 *   <li>渠道配置 CRUD（list / save / delete）</li>
 *   <li>渠道配置 Redis 缓存（TTL 600s），增删改时主动失效</li>
 *   <li>{@link #match} — 运行时报文渠道路由，返回匹配模板 id</li>
 * </ol>
 * <p>
 * Redis 为可选依赖：测试环境（application-test.yml）禁用了 RedisAutoConfiguration，
 * 通过 {@link ObjectProvider} 惰性注入，不可用时自动降级为直查数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConfigService {

    private final ChannelConfigMapper channelConfigMapper;
    private final FormatConverter formatConverter;
    private final ObjectMapper objectMapper;
    /** 惰性注入，测试环境 Redis 不可用时 getIfAvailable() 返回 null */
    private final ObjectProvider<StringRedisTemplate> redisProvider;

    private static final String CACHE_KEY = "channel:config:all";
    private static final long CACHE_TTL_SECONDS = 600;

    // ────────────────────────── CRUD ──────────────────────────────

    /**
     * 查询所有有效渠道配置列表（按 create_time 倒序）。
     */
    public List<ChannelConfig> listChannels() {
        LambdaQueryWrapper<ChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChannelConfig::getCreateTime);
        List<ChannelConfig> list = channelConfigMapper.selectList(wrapper);
        list.forEach(ch -> ch.setHeaderConfig(parseHeaderConfig(ch.getHeaderConfigRaw())));
        return list;
    }

    /**
     * 新增或更新渠道配置。
     * <ul>
     *   <li>id == null：新增，channelCode 不得重复</li>
     *   <li>id != null：更新，channelCode 不得与其他记录重复</li>
     * </ul>
     * 保存成功后清除 Redis 缓存。
     *
     * @return 渠道配置 id
     */
    public Long saveChannel(ChannelSaveRequest req) {
        validateRequired(req);
        checkChannelCodeUnique(req.getChannelCode(), req.getId());

        ChannelConfig config;
        if (req.getId() == null) {
            config = new ChannelConfig();
            config.setDeleted(0);
        } else {
            config = requireById(req.getId());
        }

        config.setChannelCode(req.getChannelCode());
        config.setChannelName(req.getChannelName());
        config.setIdentifyField(req.getIdentifyField());
        config.setTemplateId(req.getTemplateId());

        if (req.getHeaderConfig() != null) {
            try {
                config.setHeaderConfigRaw(objectMapper.writeValueAsString(req.getHeaderConfig()));
            } catch (Exception e) {
                log.warn("headerConfig 序列化失败: {}", e.getMessage());
                config.setHeaderConfigRaw(null);
            }
        } else {
            config.setHeaderConfigRaw(null);
        }

        if (req.getId() == null) {
            channelConfigMapper.insert(config);
        } else {
            channelConfigMapper.updateById(config);
        }

        evictCache();
        return config.getId();
    }

    /**
     * 逻辑删除渠道配置，并清除 Redis 缓存。
     */
    public void deleteChannel(Long id) {
        requireById(id);
        channelConfigMapper.deleteById(id);
        evictCache();
    }

    /**
     * 按 id 查询单个渠道配置（不存在时抛 404）。
     */
    public ChannelConfig getById(Long id) {
        return requireById(id);
    }

    /**
     * 按渠道编码查询渠道配置，并反序列化 headerConfig。
     *
     * @param channelCode 渠道编码
     * @return 渠道配置（含 headerConfig 对象）；不存在时返回 null
     */
    public ChannelConfig getByChannelCode(String channelCode) {
        return getAllChannelsCached().stream()
                .filter(ch -> channelCode.equals(ch.getChannelCode()))
                .findFirst()
                .map(ch -> {
                    ch.setHeaderConfig(parseHeaderConfig(ch.getHeaderConfigRaw()));
                    return ch;
                })
                .orElse(null);
    }

    // ────────────────────────── 渠道路由 ──────────────────────────

    /**
     * 运行时报文渠道自动路由。
     * <p>
     * 匹配规则：对每条渠道配置，检查报文中 {@code identifyField} 对应字段的值是否与
     * {@code channelCode} 相等，首次命中即返回关联的 {@code templateId}。
     *
     * @param message    源报文字符串
     * @param formatType 报文格式（JSON / XML / CSV / FORM_DATA）
     * @return 命中的模板 id；未匹配任何渠道时返回 {@code null}
     */
    public Long match(String message, FormatType formatType) {
        Map<String, Object> msgMap;
        try {
            msgMap = formatConverter.parseToMap(message, formatType);
        } catch (Exception e) {
            log.warn("渠道路由：报文解析失败，format={}, error={}", formatType, e.getMessage());
            return null;
        }

        List<ChannelConfig> channels = getAllChannelsCached();
        for (ChannelConfig ch : channels) {
            String fieldName = ch.getIdentifyField();
            if (fieldName == null || fieldName.trim().isEmpty()) {
                continue;
            }
            Object fieldValue = msgMap.get(fieldName);
            if (fieldValue != null && ch.getChannelCode().equals(String.valueOf(fieldValue))) {
                log.debug("渠道路由命中：channelCode={}, templateId={}", ch.getChannelCode(), ch.getTemplateId());
                return ch.getTemplateId();
            }
        }
        log.debug("渠道路由：未命中任何渠道");
        return null;
    }

    /**
     * 运行时报文渠道路由，返回匹配的渠道编码（channel_code）。
     * <p>
     * 匹配规则与 {@link #match} 相同，但返回 channelCode 而非 templateId，
     * 供 M1-7 端口分发路由使用。
     *
     * @param message    源报文字符串
     * @param formatType 报文格式
     * @return 命中的渠道编码；未匹配时返回 null
     */
    public String matchChannelCode(String message, FormatType formatType) {
        Map<String, Object> msgMap;
        try {
            msgMap = formatConverter.parseToMap(message, formatType);
        } catch (Exception e) {
            log.warn("渠道路由：报文解析失败，format={}, error={}", formatType, e.getMessage());
            return null;
        }
        List<ChannelConfig> channels = getAllChannelsCached();
        for (ChannelConfig ch : channels) {
            String fieldName = ch.getIdentifyField();
            if (fieldName == null || fieldName.trim().isEmpty()) continue;
            Object fieldValue = msgMap.get(fieldName);
            if (fieldValue != null && ch.getChannelCode().equals(String.valueOf(fieldValue))) {
                log.debug("渠道路由命中 channelCode={}", ch.getChannelCode());
                return ch.getChannelCode();
            }
        }
        log.debug("渠道路由：未命中任何渠道");
        return null;
    }

    // ────────────────────────── 缓存 ──────────────────────────────

    /**
     * 从 Redis 读取渠道列表；缓存不可用或缓存未命中时直查数据库并回填缓存。
     */
    private List<ChannelConfig> getAllChannelsCached() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            String cached = redis.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<List<ChannelConfig>>() {});
                } catch (Exception e) {
                    log.warn("渠道缓存反序列化失败，降级查库: {}", e.getMessage());
                }
            }
        }

        List<ChannelConfig> list = listChannels();

        if (redis != null) {
            try {
                redis.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(list),
                        CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("渠道缓存写入失败: {}", e.getMessage());
            }
        }
        return list;
    }

    /** 增删改后主动清除渠道列表缓存 */
    private void evictCache() {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            redis.delete(CACHE_KEY);
        }
    }

    /**
     * 将 JSON 字符串反序列化为 HeaderConfig 对象。
     * json 为 null/空时返回 null；解析失败时记录 warn 日志并返回 null。
     */
    public HeaderConfig parseHeaderConfig(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, HeaderConfig.class);
        } catch (Exception e) {
            log.warn("headerConfig 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    // ────────────────────────── 内部辅助 ──────────────────────────

    private ChannelConfig requireById(Long id) {
        ChannelConfig config = channelConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "渠道配置不存在，id=" + id);
        }
        return config;
    }

    private void validateRequired(ChannelSaveRequest req) {
        if (req.getChannelCode() == null || req.getChannelCode().trim().isEmpty()) {
            throw new BusinessException(400, "渠道编码不能为空");
        }
        if (req.getIdentifyField() == null || req.getIdentifyField().trim().isEmpty()) {
            throw new BusinessException(400, "识别字段不能为空");
        }
        if (req.getTemplateId() == null) {
            throw new BusinessException(400, "关联模板 id 不能为空");
        }
    }

    private void checkChannelCodeUnique(String channelCode, Long excludeId) {
        LambdaQueryWrapper<ChannelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChannelConfig::getChannelCode, channelCode);
        if (excludeId != null) {
            wrapper.ne(ChannelConfig::getId, excludeId);
        }
        if (channelConfigMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(400, "渠道编码已存在：" + channelCode);
        }
    }
}
