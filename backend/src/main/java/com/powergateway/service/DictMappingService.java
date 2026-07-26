package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.powergateway.dao.DictMappingMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DictMapping;
import com.powergateway.model.dto.DictMappingSaveRequest;
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
        DictMapping m = toEntity(req, req.getDirection(), req.getSourceValue(), req.getTargetValue());
        insertOne(m);
        ids.add(m.getId());
        // 双向拆条留 Task 3
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
}
