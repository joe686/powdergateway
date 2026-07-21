package com.powergateway.service.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.FieldMappingRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * FN-11 Task 4 · 配置 → Markdown 单向渲染
 *
 * 面向"人类阅读 / git diff / 客户评审"场景；反导入走 excel 路径（{@link ExcelConfigCodec} / {@link ExcelTemplateCodec}）。
 */
@Service
public class MarkdownConfigCodec {

    private final ObjectMapper objectMapper;

    @Autowired
    public MarkdownConfigCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 接口配置
    // ============================================================

    public String encodeInterface(InterfaceConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 接口：").append(nvl(cfg.getName())).append(" (").append(nvl(cfg.getType())).append(")\n\n");

        sb.append("- **接口ID**: ").append(nvl(cfg.getId())).append('\n');
        sb.append("- **访问路径**: ").append(nvl(cfg.getPath())).append('\n');
        sb.append("- **接口类型**: ").append(nvl(cfg.getType())).append('\n');
        sb.append("- **数据源ID**: ").append(nvl(cfg.getDbConnectionId())).append('\n');
        sb.append("- **状态**: ").append(nvl(cfg.getStatus())).append('\n');
        sb.append("- **响应格式**: ").append(nvl(cfg.getResponseFormat())).append('\n');
        sb.append("- **启用缓存**: ").append(bool(cfg.getCacheEnabled())).append('\n');
        if (Integer.valueOf(1).equals(cfg.getCacheEnabled())) {
            sb.append("- **缓存TTL(秒)**: ").append(nvl(cfg.getCacheTtlSeconds())).append('\n');
            sb.append("- **缓存Key模板**: ").append(nvl(cfg.getCacheKeyTemplate())).append('\n');
        }
        sb.append("- **允许批量删除**: ").append(bool(cfg.getAllowBatchDelete())).append('\n');
        sb.append("- **分库分表配置ID**: ").append(nvl(cfg.getShardConfigId())).append('\n');
        sb.append('\n');

        sb.append("## 配置详情\n\n```json\n");
        sb.append(prettyJson(cfg.getConfigJson()));
        sb.append("\n```\n");
        return sb.toString();
    }

    // ============================================================
    // 转换模板
    // ============================================================

    public String encodeTemplate(ConvertTemplate t) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 转换模板：").append(nvl(t.getName())).append("\n\n");

        sb.append("- **模板ID**: ").append(nvl(t.getId())).append('\n');
        sb.append("- **源格式**: ").append(nvl(t.getSrcFormat())).append('\n');
        sb.append("- **目标格式**: ").append(nvl(t.getTargetFormat())).append('\n');
        sb.append("- **功能号**: ").append(nvl(t.getFunctionCode())).append('\n');
        sb.append("- **版本**: ").append(nvl(t.getVersion())).append('\n');
        sb.append('\n');

        sb.append("## 字段映射\n\n");
        List<FieldMappingRule> mappings = parseMapping(t.getMappingRule());
        if (mappings.isEmpty()) {
            sb.append("_无_\n\n");
        } else {
            sb.append("| 源字段 | 目标字段 | 固定值 |\n");
            sb.append("|--------|----------|--------|\n");
            for (FieldMappingRule r : mappings) {
                sb.append("| ").append(nvl(r.getSrcField()))
                        .append(" | ").append(nvl(r.getTargetField()))
                        .append(" | ").append(nvl(r.getFixedValue()))
                        .append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## 字段加工\n\n");
        List<Map<String, Object>> processRules = parseProcess(t.getProcessRule());
        if (processRules.isEmpty()) {
            sb.append("_无_\n");
        } else {
            sb.append("| 加工类型 | 参数 |\n");
            sb.append("|----------|------|\n");
            for (Map<String, Object> r : processRules) {
                sb.append("| ").append(nvl(r.get("type")))
                        .append(" | ").append(nvl(r.get("params") == null ? "" : r.get("params").toString()))
                        .append(" |\n");
            }
        }
        return sb.toString();
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String prettyJson(String json) {
        if (json == null || json.isEmpty()) return "{}";
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (IOException e) {
            return json; // 不可解析，原样输出
        }
    }

    private List<FieldMappingRule> parseMapping(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<FieldMappingRule> list = objectMapper.readValue(json, new TypeReference<List<FieldMappingRule>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseProcess(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private String nvl(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String bool(Integer v) {
        return Integer.valueOf(1).equals(v) ? "是" : "否";
    }
}
