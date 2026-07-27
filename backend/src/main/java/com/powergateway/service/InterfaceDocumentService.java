package com.powergateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.DocumentModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * FN-09 接口文档 Service：生成 Markdown / HTML 文档，支持全量 zip 导出。
 * v0.2.0 ④ Task 1：读真实 configJson/mappingRule，提取 DICT_MAP dictKey，md/html 加"字典 key"列。
 */
@Service
@RequiredArgsConstructor
public class InterfaceDocumentService {

    private final TemplateService templateService;
    private final InterfaceConfigService interfaceService;

    /** 复用同一 ObjectMapper 实例，避免每次 new */
    private static final ObjectMapper OM = new ObjectMapper();

    // ─── 对外接口 ──────────────────────────────────────────────────────────────

    public String buildMarkdownForTemplate(Long id) {
        return renderMarkdown(buildTransformModel(id));
    }

    public String buildHtmlForTemplate(Long id) {
        return renderHtml(buildTransformModel(id));
    }

    public String buildMarkdownForVisual(Long id) {
        return renderMarkdown(buildVisualModel(id));
    }

    public String buildHtmlForVisual(Long id) {
        return renderHtml(buildVisualModel(id));
    }

    public byte[] exportAllTransformZip() {
        List<Map<String, Object>> summaries = templateService.listAllSummary();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<Map<String, Object>> manifest = new ArrayList<>();
        for (Map<String, Object> s : summaries) {
            Long id = ((Number) s.get("id")).longValue();
            String name = (String) s.get("name");
            String safe = safeFileName(name);
            try {
                String md = buildMarkdownForTemplate(id);
                String html = buildHtmlForTemplate(id);
                String mdKey = "transform/" + safe + ".md";
                String htmlKey = "transform/" + safe + ".html";
                entries.put(mdKey, md.getBytes(StandardCharsets.UTF_8));
                entries.put(htmlKey, html.getBytes(StandardCharsets.UTF_8));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id);
                item.put("name", name);
                item.put("md", mdKey);
                item.put("html", htmlKey);
                manifest.add(item);
            } catch (Exception ignore) { /* 单个失败不影响其他 */ }
        }
        Map<String, Object> mf = new LinkedHashMap<>();
        mf.put("type", "TRANSFORM");
        mf.put("count", manifest.size());
        mf.put("entries", manifest);
        try {
            String manifestJson = OM.writerWithDefaultPrettyPrinter().writeValueAsString(mf);
            entries.put("manifest.json", manifestJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessException(500, "manifest 序列化失败");
        }
        return pack(entries);
    }

    public byte[] exportAllVisualZip() {
        List<Map<String, Object>> summaries = interfaceService.listAllSummary();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<Map<String, Object>> manifest = new ArrayList<>();
        for (Map<String, Object> s : summaries) {
            Long id = ((Number) s.get("id")).longValue();
            String name = (String) s.get("name");
            String safe = safeFileName(name);
            try {
                String md = buildMarkdownForVisual(id);
                String html = buildHtmlForVisual(id);
                String mdKey = "visual/" + safe + ".md";
                String htmlKey = "visual/" + safe + ".html";
                entries.put(mdKey, md.getBytes(StandardCharsets.UTF_8));
                entries.put(htmlKey, html.getBytes(StandardCharsets.UTF_8));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", id);
                item.put("name", name);
                item.put("md", mdKey);
                item.put("html", htmlKey);
                manifest.add(item);
            } catch (Exception ignore) { }
        }
        Map<String, Object> mf = new LinkedHashMap<>();
        mf.put("type", "VISUAL");
        mf.put("count", manifest.size());
        mf.put("entries", manifest);
        try {
            String manifestJson = OM.writerWithDefaultPrettyPrinter().writeValueAsString(mf);
            entries.put("manifest.json", manifestJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessException(500, "manifest 序列化失败");
        }
        return pack(entries);
    }

    // ─── DocumentModel 构造 ────────────────────────────────────────────────────

    private DocumentModel buildTransformModel(Long tplId) {
        ConvertTemplate tpl = templateService.getById(tplId);
        DocumentModel m = new DocumentModel();
        m.setType("TRANSFORM");
        m.setTitle("转换模板文档：" + tpl.getName());
        m.setSummary("本文档描述转换模板「" + tpl.getName() + "」的源格式、目标格式与字段映射规则。");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("模板名称", tpl.getName());
        meta.put("源格式", tpl.getSrcFormat());
        meta.put("目标格式", tpl.getTargetFormat());
        meta.put("版本", tpl.getVersion());
        m.setMeta(meta);

        List<DocumentModel.Section> sections = new ArrayList<>();

        // 字段映射表（v0.2.0 ④：header 追加"字典 key"列，从 mappingRule 读取真实行）
        DocumentModel.Section mapping = new DocumentModel.Section();
        mapping.setHeading("字段映射规则");
        List<List<String>> table = new ArrayList<>();
        table.add(Arrays.asList("序号", "源字段", "目标字段", "转换方式", "字典 key"));
        List<Map<String, Object>> mappingRows = parseMappingRules(tpl.getMappingRule());
        // 解析 processRule 列中的字段加工规则（含 DICT_MAP 字典规则）
        List<Map<String, Object>> processRules = parseProcessRules(tpl.getProcessRule());
        if (mappingRows.isEmpty()) {
            table.add(Arrays.asList("—", "（无）", "（无）", "—", ""));
        } else {
            mapping.setDescription("字段映射规则（摘要）");
            int seq = 1;
            for (Map<String, Object> row : mappingRows) {
                String srcField    = row.getOrDefault("srcField",    "") != null ? row.getOrDefault("srcField",    "").toString() : "";
                String targetField = row.getOrDefault("targetField", "") != null ? row.getOrDefault("targetField", "").toString() : "";
                String fixedValue  = row.getOrDefault("fixedValue",  "") != null ? row.getOrDefault("fixedValue",  "").toString() : "";
                // 显示来源：srcField 优先，无则显示 fixedValue（固定值）
                String src = srcField.isEmpty() ? (fixedValue.isEmpty() ? "" : "[固定:" + fixedValue + "]") : srcField;
                // 从 processRule 列按 targetField 提取真实 dictKey
                String dictKey = extractDictKeyForField(processRules, targetField);
                table.add(Arrays.asList(String.valueOf(seq++), src, targetField, "", dictKey));
            }
        }
        mapping.setTable(table);
        sections.add(mapping);

        // 示例
        DocumentModel.Section example = new DocumentModel.Section();
        example.setHeading("示例");
        example.setDescription("调用本模板时，请以" + tpl.getSrcFormat() + "格式传入报文，返回" + tpl.getTargetFormat() + "格式结果。");
        sections.add(example);

        m.setSections(sections);
        return m;
    }

    private DocumentModel buildVisualModel(Long id) {
        InterfaceConfig cfg = interfaceService.getById(id);
        DocumentModel m = new DocumentModel();
        m.setType("VISUAL");
        m.setTitle("可视化接口文档：" + cfg.getName());
        m.setSummary("本文档描述可视化接口「" + cfg.getName() + "」的请求/响应字段规范与调用方式。");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("接口名称", cfg.getName());
        meta.put("接口类型", cfg.getType());
        meta.put("状态", cfg.getStatus());
        meta.put("默认响应格式", cfg.getResponseFormat() != null ? cfg.getResponseFormat() : "JSON");
        if (cfg.getPath() != null) meta.put("访问路径", cfg.getPath());
        m.setMeta(meta);

        List<DocumentModel.Section> sections = new ArrayList<>();

        // 解析 configJson 中的顶层 processRules（含 DICT_MAP 字典规则）
        List<Map<String, Object>> processRules = parseProcessRules(cfg.getConfigJson());
        // 解析 SELECT 接口的返回字段列表（{table, column, alias}）
        List<Map<String, Object>> cfgFields = parseFields(cfg.getConfigJson());

        // 请求字段（v0.2.0 ④：header 追加"字典 key"列，从 configJson.fields 读真实字段）
        DocumentModel.Section reqSection = new DocumentModel.Section();
        reqSection.setHeading("请求字段");
        List<List<String>> reqTable = new ArrayList<>();
        reqTable.add(Arrays.asList("字段名", "类型", "必填", "说明", "字典 key"));
        if (cfgFields.isEmpty()) {
            // configJson 无 fields 时保持固定行（SELECT 通用参数），dictKey 填空串
            reqTable.add(Arrays.asList("params", "Object", "N", "请求参数 Map（key-value 对）", ""));
            if ("SELECT".equals(cfg.getType())) {
                reqTable.add(Arrays.asList("page", "Integer", "N", "分页页码（默认 1）", ""));
                reqTable.add(Arrays.asList("pageSize", "Integer", "N", "每页条数（默认 20）", ""));
            }
        } else {
            for (Map<String, Object> f : cfgFields) {
                // alias 优先用作展示字段名，其次 column
                String alias  = f.getOrDefault("alias",  "") != null ? f.getOrDefault("alias",  "").toString() : "";
                String column = f.getOrDefault("column", "") != null ? f.getOrDefault("column", "").toString() : "";
                String fieldName = alias.isEmpty() ? column : alias;
                // 从顶层 processRules 按 field 名找 DICT_MAP dictKey
                String dk = extractDictKeyForField(processRules, fieldName);
                reqTable.add(Arrays.asList(fieldName, "", "", "", dk));
            }
        }
        reqSection.setTable(reqTable);
        sections.add(reqSection);

        // 响应字段（v0.2.0 ④：header 追加"字典 key"列；configJson 无独立响应字段表时沿用固定行）
        DocumentModel.Section respSection = new DocumentModel.Section();
        respSection.setHeading("响应字段");
        List<List<String>> respTable = new ArrayList<>();
        respTable.add(Arrays.asList("字段名", "类型", "说明", "字典 key"));
        if ("SELECT".equals(cfg.getType())) {
            respTable.add(Arrays.asList("data", "Array", "查询结果列表", ""));
            respTable.add(Arrays.asList("code", "Integer", "200=成功", ""));
        } else {
            respTable.add(Arrays.asList("data", "Integer", "影响行数（affectedRows）", ""));
            respTable.add(Arrays.asList("code", "Integer", "200=成功", ""));
        }
        respSection.setTable(respTable);
        sections.add(respSection);

        // 调用示例
        DocumentModel.Section callSection = new DocumentModel.Section();
        callSection.setHeading("调用示例");
        String path = cfg.getPath() != null ? cfg.getPath() : "/api/exec/" + id;
        callSection.setCodeBlock("POST " + path + "\nContent-Type: application/json\n\n{\"params\":{}}");
        callSection.setCodeLang("http");
        sections.add(callSection);

        m.setSections(sections);
        return m;
    }

    // ─── 字段解析与 dictKey 提取 ───────────────────────────────────────────────

    /**
     * 从字段加工规则列表中提取第一条 DICT_MAP 规则的 dictKey。
     * <p>
     * 实际 configJson.processRules 结构（SYS-5 向导生成）：
     * <pre>
     *   [{
     *     "type": "DICT_MAP",
     *     "field": "gender",
     *     "params": {"system": "CIF", "dictKey": "GENDER", "direction": "1"}
     *   }]
     * </pre>
     * params 可能是 Map（DICT_MAP 配置为对象），也可能是 String（其他规则用字符串 "key=val"）。
     * public static 供测试直接调用（测试包 com.powergateway 与本类不同包）。
     *
     * @param processRules 规则列表（每条为 Map），null 或空列表返回 ""
     * @return dictKey 字符串；无 DICT_MAP 规则时返回空串 ""
     */
    public static String extractDictKey(List<Map<String, Object>> processRules) {
        if (processRules == null) return "";
        for (Map<String, Object> r : processRules) {
            if ("DICT_MAP".equals(r.get("type"))) {
                Object params = r.get("params");
                if (params instanceof Map) {
                    Object dk = ((Map<?, ?>) params).get("dictKey");
                    return dk == null ? "" : dk.toString();
                }
            }
        }
        return "";
    }

    /**
     * 解析 configJson 中的 processRules 顶层数组。
     * 失败时静默降级，返回空 list，不阻塞文档生成。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseProcessRules(String configJson) {
        if (configJson == null || configJson.isEmpty()) return Collections.emptyList();
        try {
            Map<String, Object> root = OM.readValue(configJson,
                    new TypeReference<Map<String, Object>>() {});
            Object rules = root.get("processRules");
            if (rules instanceof List) return (List<Map<String, Object>>) rules;
        } catch (Exception e) { /* 静默降级 */ }
        return Collections.emptyList();
    }

    /**
     * 解析 configJson 中的 fields 数组（SELECT 接口：{table, column, alias}）。
     * 失败时静默降级，返回空 list。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFields(String configJson) {
        if (configJson == null || configJson.isEmpty()) return Collections.emptyList();
        try {
            Map<String, Object> root = OM.readValue(configJson,
                    new TypeReference<Map<String, Object>>() {});
            Object fields = root.get("fields");
            if (fields instanceof List) return (List<Map<String, Object>>) fields;
        } catch (Exception e) { /* 静默降级 */ }
        return Collections.emptyList();
    }

    /**
     * 解析 mappingRule JSON 数组（转换模板：[{srcField, targetField, fixedValue}]）。
     * 失败时静默降级，返回空 list。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMappingRules(String mappingRule) {
        if (mappingRule == null || mappingRule.isEmpty()
                || "[]".equals(mappingRule.trim())) return Collections.emptyList();
        try {
            return OM.readValue(mappingRule,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) { /* 静默降级 */ }
        return Collections.emptyList();
    }

    /**
     * 从顶层 processRules 中找到指定 field 对应的 DICT_MAP dictKey。
     *
     * @param processRules 配置的 processRules 列表
     * @param fieldName    字段名（alias 或 column）
     * @return dictKey 或 ""
     */
    private String extractDictKeyForField(List<Map<String, Object>> processRules, String fieldName) {
        if (processRules == null || fieldName == null) return "";
        for (Map<String, Object> r : processRules) {
            if ("DICT_MAP".equals(r.get("type"))
                    && fieldName.equals(r.get("field"))) {
                Object params = r.get("params");
                if (params instanceof Map) {
                    Object dk = ((Map<?, ?>) params).get("dictKey");
                    return dk == null ? "" : dk.toString();
                }
            }
        }
        return "";
    }

    // ─── 渲染器 ────────────────────────────────────────────────────────────────

    private String renderMarkdown(DocumentModel m) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(m.getTitle()).append("\n\n");
        sb.append("> ").append(m.getSummary()).append("\n\n");
        if (m.getMeta() != null) {
            m.getMeta().forEach((k, v) ->
                sb.append("- **").append(k).append("**: ").append(v).append("\n"));
        }
        sb.append("\n");
        if (m.getSections() != null) {
            for (DocumentModel.Section s : m.getSections()) {
                sb.append("## ").append(s.getHeading()).append("\n\n");
                if (s.getDescription() != null) sb.append(s.getDescription()).append("\n\n");
                if (s.getTable() != null && !s.getTable().isEmpty()) {
                    List<List<String>> t = s.getTable();
                    sb.append("| ").append(String.join(" | ", t.get(0))).append(" |\n");
                    sb.append("|").append(repeat("---|", t.get(0).size())).append("\n");
                    for (int i = 1; i < t.size(); i++) {
                        sb.append("| ").append(String.join(" | ", t.get(i))).append(" |\n");
                    }
                    sb.append("\n");
                }
                if (s.getCodeBlock() != null) {
                    sb.append("```").append(s.getCodeLang() == null ? "" : s.getCodeLang()).append("\n");
                    sb.append(s.getCodeBlock()).append("\n```\n\n");
                }
            }
        }
        return sb.toString();
    }

    private String renderHtml(DocumentModel m) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\"><head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<title>").append(esc(m.getTitle())).append("</title>\n")
          .append("<style>")
          .append("body{font-family:-apple-system,'Microsoft YaHei',sans-serif;max-width:920px;margin:24px auto;padding:0 16px;color:#222}")
          .append("table{border-collapse:collapse;width:100%;margin:12px 0}")
          .append("th,td{border:1px solid #d0d7de;padding:6px 10px;text-align:left}")
          .append("th{background:#f5f7fa}")
          .append("pre{background:#f5f7fa;padding:10px;border-radius:4px;overflow:auto}")
          .append("</style>\n</head><body>\n");
        sb.append("<h1>").append(esc(m.getTitle())).append("</h1>\n");
        sb.append("<p>").append(esc(m.getSummary())).append("</p>\n");
        if (m.getMeta() != null) {
            sb.append("<ul>");
            m.getMeta().forEach((k, v) -> sb.append("<li><b>").append(esc(k))
                .append("</b>: ").append(esc(String.valueOf(v))).append("</li>"));
            sb.append("</ul>\n");
        }
        if (m.getSections() != null) {
            for (DocumentModel.Section s : m.getSections()) {
                sb.append("<h2>").append(esc(s.getHeading())).append("</h2>\n");
                if (s.getDescription() != null)
                    sb.append("<p>").append(esc(s.getDescription())).append("</p>\n");
                if (s.getTable() != null && !s.getTable().isEmpty()) {
                    sb.append("<table>\n<tr>");
                    for (String h : s.getTable().get(0))
                        sb.append("<th>").append(esc(h)).append("</th>");
                    sb.append("</tr>\n");
                    for (int i = 1; i < s.getTable().size(); i++) {
                        sb.append("<tr>");
                        for (String c : s.getTable().get(i))
                            sb.append("<td>").append(esc(c)).append("</td>");
                        sb.append("</tr>\n");
                    }
                    sb.append("</table>\n");
                }
                if (s.getCodeBlock() != null) {
                    sb.append("<pre><code>").append(esc(s.getCodeBlock())).append("</code></pre>\n");
                }
            }
        }
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private String safeFileName(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[/\\\\?*\\[\\]:\\s]", "_");
    }

    // ─── zip 打包 ──────────────────────────────────────────────────────────────

    private byte[] pack(Map<String, byte[]> entries) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
            zos.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "zip 打包失败: " + e.getMessage());
        }
    }
}
