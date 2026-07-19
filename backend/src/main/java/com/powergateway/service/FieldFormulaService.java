package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.dao.FieldFormulaMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.FieldFormula;
import com.powergateway.model.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 字段公式管理 Service（UX-C FN-03）。
 * 全部操作走配置库 master 数据源，无 @DS 注解。
 */
@Slf4j
@Service
public class FieldFormulaService {

    @Autowired private FieldFormulaMapper mapper;
    @Autowired private FormulaValidator validator;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ─── 查询 ─────────────────────────────────────────

    public IPage<FieldFormulaDto> list(String scene, String keyword, int pageNo, int pageSize) {
        Page<FieldFormula> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<FieldFormula> qw = new LambdaQueryWrapper<>();
        if (scene != null && !scene.isEmpty()) qw.eq(FieldFormula::getScene, scene);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(FieldFormula::getName, keyword)
                          .or().like(FieldFormula::getRemark, keyword));
        }
        qw.orderByDesc(FieldFormula::getUpdateTime, FieldFormula::getCreateTime);
        IPage<FieldFormula> raw = mapper.selectPage(page, qw);
        List<FieldFormulaDto> dtos = raw.getRecords().stream()
                .map(this::toDto).collect(Collectors.toList());
        Page<FieldFormulaDto> outPage = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        outPage.setRecords(dtos);
        return outPage;
    }

    public FieldFormulaDto getById(Long id) {
        FieldFormula e = mapper.selectById(id);
        return e == null ? null : toDto(e);
    }

    // ─── 写操作 ───────────────────────────────────────

    public Long save(FormulaSaveRequest req, String creator) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            throw new BusinessException(400, "公式名称必填");
        }
        if (req.getFormulaJson() == null || req.getFormulaJson().trim().isEmpty()) {
            throw new BusinessException(400, "公式配置不能为空");
        }

        // 校验公式
        FormulaValidateRequest vreq = new FormulaValidateRequest();
        vreq.setDbConnectionId(req.getDbConnectionId());
        vreq.setFormulaJson(req.getFormulaJson());
        FormulaValidateResult vr = validator.validate(vreq);
        if (!vr.isOk()) {
            String first = vr.getErrors().isEmpty() ? "校验失败" : vr.getErrors().get(0).getMessage();
            throw new BusinessException(400, "公式校验失败：" + first);
        }

        // 名称唯一（排除自身；软删除记录不占用名称）
        LambdaQueryWrapper<FieldFormula> qw = new LambdaQueryWrapper<>();
        qw.eq(FieldFormula::getName, req.getName());
        if (req.getId() != null) qw.ne(FieldFormula::getId, req.getId());
        if (mapper.selectCount(qw) > 0) {
            throw new BusinessException(400, "公式名称已存在：" + req.getName());
        }

        if (req.getId() == null) {
            FieldFormula e = new FieldFormula();
            e.setName(req.getName());
            e.setScene(req.getScene());
            e.setDbConnectionId(req.getDbConnectionId());
            e.setFormulaJson(req.getFormulaJson());
            e.setRemark(req.getRemark());
            e.setCreator(creator);
            mapper.insert(e);
            return e.getId();
        } else {
            FieldFormula e = mapper.selectById(req.getId());
            if (e == null) throw new BusinessException(404, "公式不存在：id=" + req.getId());
            e.setName(req.getName());
            e.setScene(req.getScene());
            e.setDbConnectionId(req.getDbConnectionId());
            e.setFormulaJson(req.getFormulaJson());
            e.setRemark(req.getRemark());
            mapper.updateById(e);
            return e.getId();
        }
    }

    public Long duplicate(Long originId, String creator) {
        FieldFormula origin = mapper.selectById(originId);
        if (origin == null) throw new BusinessException(404, "原公式不存在：id=" + originId);
        FormulaSaveRequest r = new FormulaSaveRequest();
        r.setName(origin.getName() + "_copy_" + LocalDateTime.now().format(STAMP));
        r.setScene(origin.getScene());
        r.setDbConnectionId(origin.getDbConnectionId());
        r.setFormulaJson(origin.getFormulaJson());
        r.setRemark(origin.getRemark());
        return save(r, creator);
    }

    public void delete(Long id) {
        FieldFormula e = mapper.selectById(id);
        if (e == null) throw new BusinessException(404, "公式不存在：id=" + id);
        mapper.deleteById(id); // @TableLogic 触发软删
    }

    public FormulaValidateResult validate(FormulaValidateRequest req) {
        return validator.validate(req);
    }

    // ─── 工具 ─────────────────────────────────────────

    private FieldFormulaDto toDto(FieldFormula e) {
        FieldFormulaDto dto = new FieldFormulaDto();
        BeanUtils.copyProperties(e, dto);
        return dto;
    }
}
