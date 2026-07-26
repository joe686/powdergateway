package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.DictMappingMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingLookupResult;
import com.powergateway.model.dto.DictMappingSaveRequest;
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
        // 契约保护：Processor v0.2.0 ② 直调时 null 参数应立即报错，不静默 miss
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

    // ──────────────── Excel 导入 / 导出 ────────────────

    /**
     * 批量导入字典映射（整体事务：任意一行失败则全部回滚）。
     * <p>逐行解析 + 逐行保存；遇到第一个错误时记入 failedRows，手工触发 setRollbackOnly，
     * 函数正常返回（不抛异常），结果中 successCount=0。</p>
     *
     * @param file 上传的 .xlsx 文件
     * @return 导入结果（成功行数 + 失败行列表）
     */
    @Transactional(rollbackFor = Exception.class)
    public DictMappingImportResult importExcel(MultipartFile file) {
        DictMappingImportResult result = new DictMappingImportResult();
        Exception firstError = null;

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || DictMappingExcelHelper.isBlankRow(row)) continue;

                int excelRowIndex = r + 1;  // Excel 行号：表头=1，数据行从 2 起
                try {
                    // 逐行解析（direction 非法时抛 IllegalArgumentException）
                    DictMappingSaveRequest req = DictMappingExcelHelper.parseRow(row);
                    // 逐行保存（唯一约束冲突等抛 BusinessException）
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
            // Excel 文件本身无法打开
            throw new BusinessException(400, "Excel 解析失败：" + e.getMessage());
        }

        if (firstError != null) {
            // 整体回滚：已成功插入的行也全部撤销
            result.setSuccessCount(0);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return result;
    }

    /**
     * 导出字典映射为 .xlsx 字节流。
     *
     * @param systemCode 系统代号（可为 null，不筛选）
     * @param dictKey    字典标识（可为 null，不筛选）
     * @param direction  方向（可为 null，不筛选）
     * @param status     状态（可为 null，不筛选）
     * @return .xlsx 文件字节数组
     */
    public byte[] exportExcel(String systemCode, String dictKey, Integer direction, Integer status) {
        List<DictMappingVO> data = list(systemCode, dictKey, direction, status);
        try {
            return DictMappingExcelHelper.build(data);
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 生成失败：" + e.getMessage());
        }
    }
}
