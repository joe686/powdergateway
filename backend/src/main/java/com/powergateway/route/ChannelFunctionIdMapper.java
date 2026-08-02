package com.powergateway.route;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.service.DictMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 渠道 functionId → PG 内部功能号映射(v0.3.1 CR-007 Task 1.2)。
 *
 * <p>走 FN-12 字典 scope=3(通用共享) · systemCode="ROUTE" · dictKey="channel_to_pg" · direction=1
 * · sourceValue={渠道 functionId} · targetValue={PG 功能号}。</p>
 *
 * <p><b>fallback 语义</b>(用户 Q1 补充):字典未命中或字典查询失败 → 直接返回渠道原值
 * · 兼容"渠道直送 PG 功能号"场景(如渠道已知 PG-181345,不再需要转换)。</p>
 *
 * <p>渠道功能号字典配置示例:</p>
 * <pre>
 *   scope=3 · systemCode="ROUTE" · dictKey="channel_to_pg"
 *     "181345" → "PG-181345"
 *     "180345" → "PG-180345"
 *     "999999" → "PG-CUSTOM-A"     ← 渠道自定义前缀映射到 PG 内部功能号
 * </pre>
 */
@Component
public class ChannelFunctionIdMapper {

    private static final Logger log = LoggerFactory.getLogger(ChannelFunctionIdMapper.class);

    /** 字典 systemCode(约定固定值)*/
    public static final String ROUTE_SYSTEM = "ROUTE";
    /** 字典 dictKey(约定固定值)*/
    public static final String ROUTE_DICT_KEY = "channel_to_pg";
    /** 字典 scope 通用共享 */
    public static final int SCOPE_SHARED = 3;
    /** 字典 direction 源→目标 */
    public static final int DIRECTION_FORWARD = 1;

    @Autowired private DictMappingService dictMappingService;

    /**
     * 渠道 functionId → PG 功能号。
     *
     * @param channelFunctionId 渠道方送来的功能号(非空)
     * @return PG 内部功能号(命中字典时返 targetValue · 未命中 fallback 直接返 channelFunctionId)
     * @throws BusinessException channelFunctionId 为空
     */
    public String map(String channelFunctionId) {
        if (channelFunctionId == null || channelFunctionId.trim().isEmpty()) {
            throw new BusinessException(400, "channel functionId 不能为空");
        }
        String source = channelFunctionId.trim();
        try {
            DictMappingLookupResult hit = dictMappingService.lookup(
                    SCOPE_SHARED, ROUTE_SYSTEM, ROUTE_DICT_KEY, DIRECTION_FORWARD, source);
            if (hit != null && hit.getTargetValue() != null && !hit.getTargetValue().isEmpty()) {
                return hit.getTargetValue();
            }
        } catch (Exception e) {
            log.warn("CR-007 · 字典 lookup 失败 · fallback 直接返回原值 · channelFunctionId={} · {}",
                    source, e.getMessage());
        }
        // 未命中 fallback:直接返回渠道原值(兼容渠道直送 PG 功能号)
        return source;
    }
}
