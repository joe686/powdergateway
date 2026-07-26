package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.DictMappingMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 字典映射业务层（FN-12 · v0.2.0 ①）
 */
@Service
public class DictMappingService {

    @Autowired private DictMappingMapper dictMappingMapper;

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
    }

    /**
     * 软删除字典映射条目。不存在则抛异常；否则标记 deleted=1（MP @TableLogic 自动处理）
     */
    @Transactional
    public void delete(Long id) {
        DictMapping m = dictMappingMapper.selectById(id);
        if (m == null) throw new BusinessException(404, "字典条目不存在或已删除：" + id);
        dictMappingMapper.deleteById(id);
        // Redis 精准失效留 Task 7 加
    }

    /**
     * 查询已有的 system_code 去重列表（字母升序）
     */
    public List<String> getSystems() {
        return dictMappingMapper.selectDistinctSystems();
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
