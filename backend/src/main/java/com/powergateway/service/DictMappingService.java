package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.DictMappingMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 字典映射业务层（FN-12 · v0.2.0 ①）
 */
@Service
public class DictMappingService {

    private static final Logger log = LoggerFactory.getLogger(DictMappingService.class);
    private static final long DICT_TTL_SECONDS = 3600L;

    @Autowired private DictMappingMapper dictMappingMapper;

    /** 测试环境 Redis 未注入时为 null，需 null 判断后再使用 */
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 哨兵常量：Redis Hash 已全量装载，但其中不存在请求的 source（真实 miss）。
     * 调用方收到此值应直接返回 null，不再走 DB fallback 重新装载。
     */
    private static final DictMappingLookupResult REDIS_HIT_BUT_MISS = new DictMappingLookupResult(null, null);

    /**
     * 保存字典映射条目。bidirectional=true 时拆两条（direction=1 + direction=2，source/target 互换）。
     * @return 新增 id 列表
     */
    @Transactional
    public List<Long> save(DictMappingSaveRequest req) {
        List<Long> ids = new ArrayList<>();
        // 单向
        DictMapping first = toEntity(req, req.getDirection(), req.getSourceValue(), req.getTargetValue());
        insertOne(first);
        ids.add(first.getId());

        // 双向拆条
        if (Boolean.TRUE.equals(req.getBidirectional())) {
            int reverseDir = 3 - req.getDirection();  // 1↔2 互换
            DictMapping second = toEntity(req, reverseDir, req.getTargetValue(), req.getSourceValue());
            insertOne(second);
            ids.add(second.getId());
        }
        // 精准失效缓存（单向 + 双向的反向）
        tryEvictRedis(req.getSystemCode(), req.getDictKey(), req.getDirection());
        if (Boolean.TRUE.equals(req.getBidirectional())) {
            tryEvictRedis(req.getSystemCode(), req.getDictKey(), 3 - req.getDirection());
        }
        return ids;
    }

    private void insertOne(DictMapping m) {
        // 唯一约束预检
        long exist = dictMappingMapper.selectCount(new QueryWrapper<DictMapping>()
            .eq("system_code", m.getSystemCode())
            .eq("dict_key", m.getDictKey())
            .eq("direction", m.getDirection())
            .eq("source_value", m.getSourceValue()));
        if (exist > 0) {
            throw new BusinessException(409,
                "已存在同源值映射：" + m.getSystemCode() + " / " + m.getDictKey() + " / " + m.getSourceValue());
        }
        dictMappingMapper.insert(m);
    }

    private DictMapping toEntity(DictMappingSaveRequest req, int direction, String src, String tgt) {
        DictMapping m = new DictMapping();
        m.setSystemCode(req.getSystemCode());
        m.setDictKey(req.getDictKey());
        m.setDirection(direction);
        m.setSourceValue(src);
        m.setTargetValue(tgt);
        m.setCnLabel(req.getCnLabel());
        m.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        return m;
    }

    /**
     * 查询字典映射条目（支持多条件筛选）
     */
    public List<DictMappingVO> list(String systemCode, String dictKey, Integer direction, Integer status) {
        QueryWrapper<DictMapping> q = new QueryWrapper<>();
        if (systemCode != null && !systemCode.isEmpty()) q.eq("system_code", systemCode);
        if (dictKey != null && !dictKey.isEmpty())       q.eq("dict_key", dictKey);
        if (direction != null)                            q.eq("direction", direction);
        if (status != null)                               q.eq("status", status);
        q.orderByDesc("id");
        List<DictMapping> rows = dictMappingMapper.selectList(q);
        List<DictMappingVO> vos = new ArrayList<>(rows.size());
        for (DictMapping r : rows) vos.add(toVO(r));
        return vos;
    }

    /**
     * 按 id 查询字典映射条目，不存在则抛异常
     */
    public DictMappingVO getById(Long id) {
        DictMapping m = dictMappingMapper.selectById(id);
        if (m == null) throw new BusinessException(404, "字典条目不存在或已删除：" + id);
        return toVO(m);
    }

    /**
     * 更新字典映射条目。允许修改 targetValue/cnLabel/status；禁止修改 direction 或 sourceValue（必须删除后重建）
     */
    @Transactional
    public void update(Long id, DictMappingSaveRequest req) {
        // 检查条目存在性
        DictMapping cur = dictMappingMapper.selectById(id);
        if (cur == null) throw new BusinessException(404, "字典条目不存在或已删除：" + id);

        // 禁止改 direction
        if (!cur.getDirection().equals(req.getDirection())) {
            throw new BusinessException(400, "不允许修改方向，请删除后重建");
        }

        // 禁止改 sourceValue
        if (!cur.getSourceValue().equals(req.getSourceValue())) {
            throw new BusinessException(400, "不允许修改源值，请删除后重建");
        }

        // 更新允许的字段
        cur.setTargetValue(req.getTargetValue());
        cur.setCnLabel(req.getCnLabel());
        if (req.getStatus() != null) cur.setStatus(req.getStatus());

        dictMappingMapper.updateById(cur);
        // 精准失效缓存
        tryEvictRedis(cur.getSystemCode(), cur.getDictKey(), cur.getDirection());
    }

    /**
     * 软删除字典映射条目。不存在则抛异常；否则标记 deleted=1（MP @TableLogic 自动处理）
     */
    @Transactional
    public void delete(Long id) {
        DictMapping m = dictMappingMapper.selectById(id);
        if (m == null) throw new BusinessException(404, "字典条目不存在或已删除：" + id);
        dictMappingMapper.deleteById(id);
        // 精准失效缓存
        tryEvictRedis(m.getSystemCode(), m.getDictKey(), m.getDirection());
    }

    /**
     * 查询已有的 system_code 去重列表（字母升序）
     */
    public List<String> getSystems() {
        return dictMappingMapper.selectDistinctSystems();
    }

    /**
     * 字典查值：Redis 命中直接返回；未命中时全量装载 DB 并写 Redis（TTL 3600s）。
     * 测试环境 stringRedisTemplate 为 null，直接走 DB fallback。
     *
     * @return 命中时返回 {@link DictMappingLookupResult}，miss 返回 null
     */
    public DictMappingLookupResult lookup(String system, String dictKey, Integer direction, String source) {
        String key = cacheKey(system, dictKey, direction);

        // 1. 先尝试从 Redis 读取
        DictMappingLookupResult fromRedis = tryLoadFromRedis(key, source);
        if (fromRedis == REDIS_HIT_BUT_MISS) {
            // Hash 已全量装载，source 不在其中 → 权威 miss，不回 DB
            return null;
        }
        if (fromRedis != null) {
            // Hash 命中且 source 存在 → 直接返回
            return fromRedis;
        }

        // 2. Redis miss（或 Redis 不可用）→ DB 全量装载
        List<DictMapping> rows = dictMappingMapper.selectByLookup(system, dictKey, direction);
        if (rows.isEmpty()) return null;

        Map<String, String> hashData = new HashMap<>();
        for (DictMapping m : rows) {
            hashData.put(m.getSourceValue(), m.getTargetValue());
            if (m.getCnLabel() != null) {
                hashData.put(m.getSourceValue() + "__cn", m.getCnLabel());
            }
        }

        // 3. 异步写入 Redis（失败降级，不抛出）
        if (stringRedisTemplate != null) {
            try {
                stringRedisTemplate.opsForHash().putAll(key, hashData);
                stringRedisTemplate.expire(key, DICT_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (RedisConnectionFailureException e) {
                log.warn("Redis 写入失败，已降级走 DB：{}", e.getMessage());
            }
        }

        // 4. 从 DB 结果返回
        String hit = hashData.get(source);
        if (hit == null) return null;
        String hitCn = hashData.get(source + "__cn");
        return new DictMappingLookupResult(hit, hitCn);
    }

    // ──────────────── Redis 私有 helper ────────────────

    private String cacheKey(String system, String dictKey, Integer direction) {
        return "dict:" + system + ":" + dictKey + ":" + direction;
    }

    /**
     * 尝试从 Redis Hash 读取 lookup 结果。
     *
     * @return
     *   <ul>
     *     <li>命中：{@code DictMappingLookupResult(target, cnLabel)}</li>
     *     <li>Hash 存在但无此 source（真实 miss）：{@link #REDIS_HIT_BUT_MISS} 哨兵，
     *         调用方应返回 null，不走 DB fallback（Hash 已是全量权威数据）</li>
     *     <li>Hash 未装载 or Redis 不可用：{@code null}，调用方应走 DB fallback 全量装载</li>
     *   </ul>
     */
    private DictMappingLookupResult tryLoadFromRedis(String key, String source) {
        if (stringRedisTemplate == null) return null;
        try {
            Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(key);
            if (!hash.isEmpty()) {
                Object t = hash.get(source);
                if (t != null) {
                    String cnLabel = null;
                    Object c = hash.get(source + "__cn");
                    if (c != null) cnLabel = c.toString();
                    return new DictMappingLookupResult(t.toString(), cnLabel);
                }
                // Hash 已全量装载，但无该 source → 真实 miss，返回哨兵，不让调用方回 DB
                return REDIS_HIT_BUT_MISS;
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 读取失败，降级走 DB：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 精准删除 Redis 中对应 dict key 的缓存 Hash。
     * 若 stringRedisTemplate 为 null（测试环境）或 Redis 不可用，静默忽略。
     */
    private void tryEvictRedis(String system, String dictKey, Integer direction) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.delete(cacheKey(system, dictKey, direction));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 删除缓存失败，忽略：{}", e.getMessage());
        }
    }

    private DictMappingVO toVO(DictMapping m) {
        DictMappingVO v = new DictMappingVO();
        v.setId(m.getId());
        v.setSystemCode(m.getSystemCode());
        v.setDictKey(m.getDictKey());
        v.setDirection(m.getDirection());
        v.setSourceValue(m.getSourceValue());
        v.setTargetValue(m.getTargetValue());
        v.setCnLabel(m.getCnLabel());
        v.setStatus(m.getStatus());
        v.setCreateTime(m.getCreateTime());
        v.setUpdateTime(m.getUpdateTime());
        return v;
    }
}
