package com.powergateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.ConvertTemplateMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.dto.FieldMappingRule;
import com.powergateway.model.dto.PreviewRequest;
import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.TemplateQueryRequest;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M1-2 转换模板 Service
 * <p>
 * 负责：模板保存（含版本留存）、按 ID 查询、映射规则预览。
 * M1-5 负责完整 CRUD（列表分页、复制、删除）。
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ConvertTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;
    private final FormatConverter formatConverter;

    /**
     * 保存或更新模板映射规则。
     * <ul>
     *   <li>id == null：新增，version=1，is_latest=1</li>
     *   <li>id != null：旧版本 is_latest 置 0，插入新版本（version+1）</li>
     * </ul>
     *
     * @return 新记录 id
     */
    public Long saveTemplate(TemplateSaveRequest req) {
        String mappingJson = serializeMappingRules(req.getMappingRules());

        if (req.getId() != null) {
            ConvertTemplate old = requireById(req.getId());
            // 旧版本标记为非最新
            old.setIsLatest(0);
            templateMapper.updateById(old);

            // 插入新版本，保留 process_rule
            ConvertTemplate neo = new ConvertTemplate();
            neo.setName(req.getName() != null ? req.getName() : old.getName());
            neo.setSrcFormat(req.getSrcFormat() != null ? req.getSrcFormat() : old.getSrcFormat());
            neo.setTargetFormat(req.getTargetFormat() != null ? req.getTargetFormat() : old.getTargetFormat());
            neo.setMappingRule(mappingJson);
            neo.setProcessRule(old.getProcessRule());
            neo.setIsLatest(1);
            neo.setVersion(old.getVersion() != null ? old.getVersion() + 1 : 2);
            neo.setDeleted(0);
            neo.setCreator(old.getCreator());
            templateMapper.insert(neo);
            return neo.getId();
        } else {
            ConvertTemplate tpl = new ConvertTemplate();
            tpl.setName(req.getName());
            tpl.setSrcFormat(req.getSrcFormat());
            tpl.setTargetFormat(req.getTargetFormat());
            tpl.setMappingRule(mappingJson);
            tpl.setIsLatest(1);
            tpl.setVersion(1);
            tpl.setDeleted(0);
            // UX-D：透传功能号
            tpl.setFunctionCode(req.getFunctionCode());
            templateMapper.insert(tpl);
            return tpl.getId();
        }
    }

    /**
     * 按 ID 查询模板（已删除/不存在时抛 404）。
     */
    public ConvertTemplate getById(Long id) {
        return requireById(id);
    }

    /**
     * 分页查询模板列表（M1-5）。
     * <ul>
     *   <li>支持模板名称关键词模糊搜索</li>
     *   <li>默认只返回最新版本（is_latest=1）</li>
     * </ul>
     */
    public Page<ConvertTemplate> listTemplates(TemplateQueryRequest req) {
        Page<ConvertTemplate> pageParam = new Page<>(req.getPage(), req.getSize());
        LambdaQueryWrapper<ConvertTemplate> wrapper = new LambdaQueryWrapper<>();
        if (req.isLatestOnly()) {
            wrapper.eq(ConvertTemplate::getIsLatest, 1);
        }
        if (StringUtils.hasText(req.getKeyword())) {
            wrapper.like(ConvertTemplate::getName, req.getKeyword());
        }
        // UX-D：功能号精确匹配
        if (req.getFunctionCode() != null && !req.getFunctionCode().trim().isEmpty()) {
            wrapper.eq(ConvertTemplate::getFunctionCode, req.getFunctionCode().trim());
        }
        wrapper.orderByDesc(ConvertTemplate::getCreateTime);
        return templateMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 逻辑删除模板（M1-5）。
     * 同时将同名模板的所有历史版本一并软删除，避免孤儿版本残留。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long id) {
        ConvertTemplate tpl = requireById(id);
        // 先批量软删同名历史版本（一条 SQL），再删当前版本
        LambdaQueryWrapper<ConvertTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConvertTemplate::getName, tpl.getName())
               .eq(ConvertTemplate::getIsLatest, 0);
        templateMapper.delete(wrapper);
        templateMapper.deleteById(id);
    }

    /**
     * 复制模板（M1-5）：name 加 _copy 后缀，version 重置为 1，is_latest=1。
     *
     * @return 新模板 id
     */
    public Long copyTemplate(Long id) {
        ConvertTemplate src = requireById(id);
        ConvertTemplate copy = new ConvertTemplate();
        copy.setName(src.getName() + "_copy");
        copy.setSrcFormat(src.getSrcFormat());
        copy.setTargetFormat(src.getTargetFormat());
        copy.setMappingRule(src.getMappingRule());
        copy.setProcessRule(src.getProcessRule());
        copy.setIsLatest(1);
        copy.setVersion(1);
        copy.setDeleted(0);
        copy.setCreator(src.getCreator());
        templateMapper.insert(copy);
        return copy.getId();
    }

    /**
     * 映射预览：解析测试报文 → 按 mapping_rule 映射字段 → 返回结果 Map。
     * <p>
     * fixedValue 优先于 srcField；目标字段无对应来源时跳过（不报错）。
     */
    public Map<String, Object> preview(Long templateId, PreviewRequest req) {
        ConvertTemplate tpl = requireById(templateId);

        FormatType formatType = FormatType.parse(req.getFormat());
        Map<String, Object> srcMap = formatConverter.parseToMap(req.getMessage(), formatType);

        List<FieldMappingRule> rules = deserializeMappingRules(tpl.getMappingRule());

        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldMappingRule rule : rules) {
            String target = rule.getTargetField();
            if (target == null || target.trim().isEmpty()) continue;

            if (rule.getFixedValue() != null) {
                result.put(target, rule.getFixedValue());
            } else if (rule.getSrcField() != null && srcMap.containsKey(rule.getSrcField())) {
                result.put(target, srcMap.get(rule.getSrcField()));
            }
        }
        return result;
    }

    // -------------------- 内部辅助 --------------------

    private ConvertTemplate requireById(Long id) {
        ConvertTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) {
            throw new BusinessException(404, "模板不存在，id=" + id);
        }
        return tpl;
    }

    /** FN-10 导出模板列表 Excel */
    public byte[] exportList(String keyword) {
        LambdaQueryWrapper<ConvertTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConvertTemplate::getIsLatest, 1);
        if (StringUtils.hasText(keyword)) wrapper.like(ConvertTemplate::getName, keyword);
        wrapper.orderByDesc(ConvertTemplate::getCreateTime);
        List<ConvertTemplate> rows = templateMapper.selectList(wrapper);
        return com.powergateway.utils.ExcelExportUtil.export("转换模板", java.util.Arrays.asList(
            new com.powergateway.utils.ExcelExportUtil.Column<>("模板名称", ConvertTemplate::getName),
            new com.powergateway.utils.ExcelExportUtil.Column<>("源格式",   ConvertTemplate::getSrcFormat),
            new com.powergateway.utils.ExcelExportUtil.Column<>("目标格式", ConvertTemplate::getTargetFormat),
            new com.powergateway.utils.ExcelExportUtil.Column<>("版本",     ConvertTemplate::getVersion),
            new com.powergateway.utils.ExcelExportUtil.Column<>("是否最新", r -> r.getIsLatest() != null && r.getIsLatest() == 1 ? "Y" : "N")
        ), rows);
    }

    /** FN-09 获取所有最新模板的摘要列表（{id, name, srcFormat, targetFormat}） */
    public List<Map<String, Object>> listAllSummary() {
        LambdaQueryWrapper<ConvertTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConvertTemplate::getIsLatest, 1);
        wrapper.orderByDesc(ConvertTemplate::getCreateTime);
        List<ConvertTemplate> all = templateMapper.selectList(wrapper);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (ConvertTemplate t : all) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("srcFormat", t.getSrcFormat());
            m.put("targetFormat", t.getTargetFormat());
            result.add(m);
        }
        return result;
    }

    /** FN-11 按主键三元组（name + srcFormat + targetFormat）查询模板 */
    public ConvertTemplate findByPrimary(String name, String srcFormat, String targetFormat) {
        LambdaQueryWrapper<ConvertTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConvertTemplate::getName, name)
               .eq(ConvertTemplate::getSrcFormat, srcFormat)
               .eq(ConvertTemplate::getTargetFormat, targetFormat)
               .eq(ConvertTemplate::getIsLatest, 1);
        return templateMapper.selectOne(wrapper);
    }

    private String serializeMappingRules(List<FieldMappingRule> rules) {
        try {
            return objectMapper.writeValueAsString(rules != null ? rules : Collections.emptyList());
        } catch (Exception e) {
            throw new BusinessException(400, "映射规则序列化失败: " + e.getMessage());
        }
    }

    private List<FieldMappingRule> deserializeMappingRules(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<FieldMappingRule>>() {});
        } catch (Exception e) {
            throw new BusinessException(500, "映射规则反序列化失败: " + e.getMessage());
        }
    }
}
