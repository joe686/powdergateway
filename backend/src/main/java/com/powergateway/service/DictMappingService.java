package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.DictMappingMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingBatchSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import com.powergateway.model.dto.DictMappingImportResult;
import com.powergateway.utils.DictMappingExcelHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 字典映射业务层（FN-12 · v0.2.0 ① · v0.2.5 CR-004 加 scope 分场景）
 */
@Service
public class DictMappingService {

    private static final Logger log = LoggerFactory.getLogger(DictMappingService.class);
    private static final long DICT_TTL_SECONDS = 3600L;

    /** scope 常量 · CR-004 · v0.2.5 */
    public static final int SCOPE_TRANSFORM = 1;   // 接口转换 M1 侧
    public static final int SCOPE_INTERFACE = 2;   // 可视化接口 M2 侧
    public static final int SCOPE_SHARED    = 3;   // 通用共享

    /** batch 接口单次上限 · CR-004 · v0.2.5 */
    public static final int BATCH_MAX = 200;

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
        // Service 层自守卫（Processor 直调不走 Controller @Valid）
        if (req.getSystemCode() == null || req.getSystemCode().trim().isEmpty()) {
            throw new BusinessException(400, "systemCode 必填");
        }
        if (req.getDictKey() == null || req.getDictKey().trim().isEmpty()) {
            throw new BusinessException(400, "dictKey 必填");
        }
        if (req.getSourceValue() == null || req.getSourceValue().trim().isEmpty()) {
            throw new BusinessException(400, "sourceValue 必填");
        }
        if (req.getTargetValue() == null || req.getTargetValue().trim().isEmpty()) {
            throw new BusinessException(400, "targetValue 必填");
        }
        if (req.getDirection() == null || (req.getDirection() != 1 && req.getDirection() != 2)) {
            throw new BusinessException(400, "direction 必须为 1 (出向) 或 2 (入向)");
        }
        int scope = normalizeScope(req.getScope());

        List<Long> ids = new ArrayList<>();
        // 单向
        DictMapping first = toEntity(req, scope, req.getDirection(), req.getSourceValue(), req.getTargetValue());
        insertOne(first);
        ids.add(first.getId());

        // 双向拆条
        if (Boolean.TRUE.equals(req.getBidirectional())) {
            int reverseDir = 3 - req.getDirection();  // 1↔2 互换
            DictMapping second = toEntity(req, scope, reverseDir, req.getTargetValue(), req.getSourceValue());
            insertOne(second);
            ids.add(second.getId());
        }
        // 精准失效缓存（单向 + 双向的反向 · 按 scope 广播）
        evictAllScopeViews(scope, req.getSystemCode(), req.getDictKey(), req.getDirection());
        if (Boolean.TRUE.equals(req.getBidirectional())) {
            evictAllScopeViews(scope, req.getSystemCode(), req.getDictKey(), 3 - req.getDirection());
        }
        return ids;
    }

    /**
     * 批量保存字典映射条目（v0.2.5 CR-004 · FB-048）。
     * 请求语义：顶部锁 {system, dictKey, direction, scope} 共享,下方 items 每条 {source, target, cnLabel}。
     * 整批事务:任意行校验失败或唯一冲突,全部回滚。
     * 上限 {@link #BATCH_MAX} 条,超限抛 400。
     */
    @Transactional
    public List<Long> saveBatch(DictMappingBatchSaveRequest req) {
        if (req.getSystemCode() == null || req.getSystemCode().trim().isEmpty()) {
            throw new BusinessException(400, "systemCode 必填");
        }
        if (req.getDictKey() == null || req.getDictKey().trim().isEmpty()) {
            throw new BusinessException(400, "dictKey 必填");
        }
        if (req.getDirection() == null || (req.getDirection() != 1 && req.getDirection() != 2)) {
            throw new BusinessException(400, "direction 必须为 1 或 2");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException(400, "items 不能为空");
        }
        if (req.getItems().size() > BATCH_MAX) {
            throw new BusinessException(400,
                "单次批量上限 " + BATCH_MAX + " 条 · 超限请用 FN-11 Excel 导入");
        }
        int scope = normalizeScope(req.getScope());

        List<Long> ids = new ArrayList<>(req.getItems().size());
        for (int i = 0; i < req.getItems().size(); i++) {
            DictMappingBatchSaveRequest.Item it = req.getItems().get(i);
            if (it == null || it.getSourceValue() == null || it.getSourceValue().trim().isEmpty()) {
                throw new BusinessException(400, "第 " + (i + 1) + " 行 sourceValue 必填");
            }
            if (it.getTargetValue() == null || it.getTargetValue().trim().isEmpty()) {
                throw new BusinessException(400, "第 " + (i + 1) + " 行 targetValue 必填");
            }
            DictMapping m = new DictMapping();
            m.setScope(scope);
            m.setSystemCode(req.getSystemCode());
            m.setDictKey(req.getDictKey());
            m.setDirection(req.getDirection());
            m.setSourceValue(it.getSourceValue());
            m.setTargetValue(it.getTargetValue());
            m.setCnLabel(it.getCnLabel());
            m.setStatus(1);
            try {
                insertOne(m);
            } catch (BusinessException be) {
                throw new BusinessException(be.getCode(),
                    "第 " + (i + 1) + " 行:" + be.getMessage());
            }
            ids.add(m.getId());
        }
        // 一次性 evict 视图缓存
        evictAllScopeViews(scope, req.getSystemCode(), req.getDictKey(), req.getDirection());
        return ids;
    }

    private void insertOne(DictMapping m) {
        // 唯一约束预检(含 scope 维度)
        long exist = dictMappingMapper.selectCount(new QueryWrapper<DictMapping>()
            .eq("scope", m.getScope())
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

    private DictMapping toEntity(DictMappingSaveRequest req, int scope, int direction, String src, String tgt) {
        DictMapping m = new DictMapping();
        m.setScope(scope);
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
     * 老签名重载(v0.2.0 兼容 · 无 scope 参数视作查全部作用域 · 供老测试与老代码调用)。
     * v0.2.5 CR-004:新代码请调 {@link #list(Integer, String, String, Integer, Integer)} 显式传 scope。
     */
    public List<DictMappingVO> list(String systemCode, String dictKey, Integer direction, Integer status) {
        return list(null, systemCode, dictKey, direction, status);
    }

    /**
     * 查询字典映射条目（支持多条件筛选，含 scope）
     */
    public List<DictMappingVO> list(Integer scope, String systemCode, String dictKey, Integer direction, Integer status) {
        QueryWrapper<DictMapping> q = new QueryWrapper<>();
        if (scope != null)                                q.eq("scope", scope);
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
     * 更新字典映射条目。允许修改 targetValue/cnLabel/status；禁止修改 scope/direction/sourceValue（必须删除后重建）
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

        // 禁止改 scope
        if (req.getScope() != null && !req.getScope().equals(cur.getScope())) {
            throw new BusinessException(400, "不允许修改 scope，请删除后重建");
        }

        // 更新允许的字段
        cur.setTargetValue(req.getTargetValue());
        cur.setCnLabel(req.getCnLabel());
        if (req.getStatus() != null) cur.setStatus(req.getStatus());

        dictMappingMapper.updateById(cur);
        // 精准失效缓存
        evictAllScopeViews(cur.getScope(), cur.getSystemCode(), cur.getDictKey(), cur.getDirection());
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
        evictAllScopeViews(m.getScope(), m.getSystemCode(), m.getDictKey(), m.getDirection());
    }

    /**
     * 老签名重载(v0.2.0 兼容 · 默认 scope=3 共享 · 供老测试与老代码调用)。
     */
    public List<String> getSystems() {
        return getSystems(SCOPE_SHARED);
    }

    /**
     * 查询已有的 system_code 去重列表（字母升序 · 按 scope 过滤）
     */
    public List<String> getSystems(Integer scope) {
        return dictMappingMapper.selectDistinctSystems(normalizeScope(scope));
    }

    /**
     * 字典查值：Redis 命中直接返回；未命中时全量装载 DB 并写 Redis（TTL 3600s）。
     * 测试环境 stringRedisTemplate 为 null，直接走 DB fallback。
     *
     * CR-004 · v0.2.5:
     *   - scope 参数决定 lookup 视角(M1 / M2 / 共享)
     *   - Redis key 包含 scope 维度(不同视角独立缓存)
     *   - DB 查询走 IN(scope, 3) fallback 到共享
     *
     * @return 命中时返回 {@link DictMappingLookupResult}，miss 返回 null
     */
    /**
     * 老签名重载(v0.2.0 兼容 · scope 参数缺失时默认走 SCOPE_SHARED · 供老测试与老代码调用)。
     * v0.2.5 CR-004:新代码请调 {@link #lookup(Integer, String, String, Integer, String)} 显式传 scope。
     */
    public DictMappingLookupResult lookup(String system, String dictKey, Integer direction, String source) {
        return lookup(null, system, dictKey, direction, source);
    }

    public DictMappingLookupResult lookup(Integer scope, String system, String dictKey, Integer direction, String source) {
        // 契约保护
        if (system == null || system.trim().isEmpty()) {
            throw new BusinessException(400, "lookup 参数 system 必填");
        }
        if (dictKey == null || dictKey.trim().isEmpty()) {
            throw new BusinessException(400, "lookup 参数 dictKey 必填");
        }
        if (direction == null || (direction != 1 && direction != 2)) {
            throw new BusinessException(400, "lookup 参数 direction 必须为 1 或 2");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new BusinessException(400, "lookup 参数 source 必填");
        }
        int actualScope = normalizeScope(scope);

        String key = cacheKey(actualScope, system, dictKey, direction);

        // 1. 先尝试从 Redis 读取
        DictMappingLookupResult fromRedis = tryLoadFromRedis(key, source);
        if (fromRedis == REDIS_HIT_BUT_MISS) {
            return null;
        }
        if (fromRedis != null) {
            return fromRedis;
        }

        // 2. Redis miss（或 Redis 不可用）→ DB 全量装载
        List<DictMapping> rows = dictMappingMapper.selectByLookup(actualScope, system, dictKey, direction);
        if (rows.isEmpty()) return null;

        // 优先 scope=actualScope 条目,scope=3 共享条目作为 fallback (若 source 同键值两者都有,优先精确 scope)
        Map<String, String> hashData = new HashMap<>();
        for (DictMapping m : rows) {
            if (m.getScope() != null && m.getScope() == SCOPE_SHARED && actualScope != SCOPE_SHARED) {
                // 共享条目 fallback:仅当精确 scope 没有该 source 时才填入
                hashData.putIfAbsent(m.getSourceValue(), m.getTargetValue());
                if (m.getCnLabel() != null) {
                    hashData.putIfAbsent(m.getSourceValue() + "__cn", m.getCnLabel());
                }
            } else {
                hashData.put(m.getSourceValue(), m.getTargetValue());
                if (m.getCnLabel() != null) {
                    hashData.put(m.getSourceValue() + "__cn", m.getCnLabel());
                }
            }
        }

        // 3. 异步写入 Redis（失败降级，不抛出）
        if (stringRedisTemplate != null && !hashData.isEmpty()) {
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

    private String cacheKey(Integer scope, String system, String dictKey, Integer direction) {
        return "dict:" + scope + ":" + system + ":" + dictKey + ":" + direction;
    }

    /**
     * 尝试从 Redis Hash 读取 lookup 结果。
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
                return REDIS_HIT_BUT_MISS;
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 读取失败，降级走 DB：{}", e.getMessage());
        }
        return null;
    }

    /**
     * 精准 evict Redis 中该 dict 各 scope 视图。
     * <p>CR-004 · v0.2.5:
     *   - 存的 scope=3(共享) → 需 evict scope=1 视角 + scope=2 视角 + scope=3 视角
     *   - 存的 scope=1 → 只 evict scope=1 视角
     *   - 存的 scope=2 → 只 evict scope=2 视角
     * 保证任何 scope 视角下 lookup 都能读到最新数据。
     */
    private void evictAllScopeViews(Integer storedScope, String system, String dictKey, Integer direction) {
        if (stringRedisTemplate == null) return;
        int s = normalizeScope(storedScope);
        try {
            if (s == SCOPE_SHARED) {
                stringRedisTemplate.delete(cacheKey(SCOPE_TRANSFORM, system, dictKey, direction));
                stringRedisTemplate.delete(cacheKey(SCOPE_INTERFACE, system, dictKey, direction));
                stringRedisTemplate.delete(cacheKey(SCOPE_SHARED, system, dictKey, direction));
            } else {
                stringRedisTemplate.delete(cacheKey(s, system, dictKey, direction));
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 删除缓存失败，忽略：{}", e.getMessage());
        }
    }

    private int normalizeScope(Integer scope) {
        if (scope == null) return SCOPE_SHARED;
        int s = scope;
        if (s < 1 || s > 3) return SCOPE_SHARED;
        return s;
    }

    private DictMappingVO toVO(DictMapping m) {
        DictMappingVO v = new DictMappingVO();
        v.setId(m.getId());
        v.setScope(m.getScope());
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

    // ──────────────── Excel 导入 / 导出 ────────────────

    /**
     * 批量导入字典映射（整体事务：任意一行失败则全部回滚）。
     * CR-004 · v0.2.5:导入时 scope 从 URL 参数传入,整批共享同一 scope(默认 3=共享)
     */
    /** 老签名重载(v0.2.0 兼容 · 默认 scope=3 · 供老测试与老代码调用)。 */
    @Transactional(rollbackFor = Exception.class)
    public DictMappingImportResult importExcel(MultipartFile file) {
        return importExcel(file, SCOPE_SHARED);
    }

    @Transactional(rollbackFor = Exception.class)
    public DictMappingImportResult importExcel(MultipartFile file, Integer scope) {
        DictMappingImportResult result = new DictMappingImportResult();
        Exception firstError = null;
        int actualScope = normalizeScope(scope);

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || DictMappingExcelHelper.isBlankRow(row)) continue;

                int excelRowIndex = r + 1;  // Excel 行号：表头=1，数据行从 2 起
                try {
                    DictMappingSaveRequest req = DictMappingExcelHelper.parseRow(row);
                    req.setScope(actualScope);
                    save(req);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } catch (BusinessException | IllegalArgumentException e) {
                    firstError = e;
                    result.getFailedRows().add(
                        new DictMappingImportResult.FailedRow(excelRowIndex, e.getMessage()));
                    break;  // 遇第一个错误立即停止，准备整体回滚
                }
            }
        } catch (Exception e) {
            throw new BusinessException(400, "Excel 解析失败：" + e.getMessage());
        }

        if (firstError != null) {
            result.setSuccessCount(0);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return result;
    }

    /** 老签名重载(v0.2.0 兼容 · 无 scope 视作全部作用域 · 供老测试与老代码调用)。 */
    public byte[] exportExcel(String systemCode, String dictKey, Integer direction, Integer status) {
        return exportExcel(null, systemCode, dictKey, direction, status);
    }

    /**
     * 导出字典映射为 .xlsx 字节流。
     */
    public byte[] exportExcel(Integer scope, String systemCode, String dictKey, Integer direction, Integer status) {
        List<DictMappingVO> data = list(scope, systemCode, dictKey, direction, status);
        try {
            return DictMappingExcelHelper.build(data);
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 生成失败：" + e.getMessage());
        }
    }
}
