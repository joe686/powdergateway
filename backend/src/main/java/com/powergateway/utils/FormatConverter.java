package com.powergateway.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import com.powergateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * M1-1 格式转换引擎
 * <p>
 * 支持 JSON / XML / CSV / FormData 四种格式互转（12种组合）。
 * 转换路径：源格式 → 中间 Map&lt;String, Object&gt; → 目标格式。
 */
@Component
@RequiredArgsConstructor
public class FormatConverter {

    private final ObjectMapper objectMapper;

    /**
     * 将源报文解析为中间 Map（M1-2 字段映射预览使用）
     *
     * @param src    源报文字符串
     * @param format 源格式
     * @return 解析后的字段 Map
     */
    public Map<String, Object> parseToMap(String src, FormatType format) {
        try {
            return parse(src, format);
        } catch (Exception e) {
            throw new BusinessException(400, "报文解析失败 [" + format + "]: " + e.getMessage(), e);
        }
    }

    /**
     * 格式转换入口
     *
     * @param src  源报文字符串
     * @param from 源格式
     * @param to   目标格式
     * @return 目标格式的报文字符串
     */
    public String convert(String src, FormatType from, FormatType to) {
        try {
            Map<String, Object> intermediate = parse(src, from);
            return serialize(intermediate, to);
        } catch (Exception e) {
            throw new BusinessException(500, "格式转换失败 [" + from + "->" + to + "]: " + e.getMessage(), e);
        }
    }

    /**
     * 将中间 Map 直接序列化为目标格式，避免经过 JSON 字符串中转。
     * ConvertService 在字段映射/加工后调用此方法输出最终报文。
     */
    public String serializeMap(Map<String, Object> data, FormatType format) {
        try {
            return serialize(data, format);
        } catch (Exception e) {
            throw new BusinessException(500, "报文序列化失败 [" + format + "]: " + e.getMessage(), e);
        }
    }

    // ==================== 解析（各格式 → Map） ====================

    private Map<String, Object> parse(String src, FormatType format) throws Exception {
        switch (format) {
            case JSON:      return parseJson(src);
            case XML:       return parseXml(src);
            case CSV:       return parseCsv(src);
            case FORM_DATA: return parseFormData(src);
            default: throw new IllegalArgumentException("不支持的源格式: " + format);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String src) throws Exception {
        return objectMapper.readValue(src, LinkedHashMap.class);
    }

    private Map<String, Object> parseXml(String src) throws Exception {
        Document doc = DocumentHelper.parseText(src.trim());
        return xmlElementToMap(doc.getRootElement());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> xmlElementToMap(Element element) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Element child : (List<Element>) element.elements()) {
            List<Element> grandChildren = child.elements();
            if (grandChildren.isEmpty()) {
                result.put(child.getName(), child.getText());
            } else {
                result.put(child.getName(), xmlElementToMap(child));
            }
        }
        return result;
    }

    private Map<String, Object> parseCsv(String src) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(src.trim()))) {
            String[] headers = reader.readNext();
            String[] values  = reader.readNext();
            if (headers == null || values == null) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                result.put(headers[i].trim(), i < values.length ? values[i] : "");
            }
            return result;
        }
    }

    private Map<String, Object> parseFormData(String src) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        if (src == null || src.isEmpty()) {
            return result;
        }
        for (String pair : src.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key   = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                String value = idx < pair.length() - 1
                        ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                        : "";
                result.put(key, value);
            }
        }
        return result;
    }

    // ==================== 序列化（Map → 各格式） ====================

    private String serialize(Map<String, Object> data, FormatType format) throws Exception {
        switch (format) {
            case JSON:      return toJson(data);
            case XML:       return toXml(data);
            case CSV:       return toCsv(data);
            case FORM_DATA: return toFormData(data);
            default: throw new IllegalArgumentException("不支持的目标格式: " + format);
        }
    }

    private String toJson(Map<String, Object> data) throws Exception {
        return objectMapper.writeValueAsString(data);
    }

    private String toXml(Map<String, Object> data) {
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("root");
        appendMapToXml(root, data);
        return doc.asXML();
    }

    @SuppressWarnings("unchecked")
    private void appendMapToXml(Element parent, Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key   = sanitizeXmlTag(entry.getKey());
            Object value = entry.getValue();
            Element el   = parent.addElement(key);
            if (value instanceof Map) {
                appendMapToXml(el, (Map<String, Object>) value);
            } else {
                el.setText(value == null ? "" : value.toString());
            }
        }
    }

    /** XML 标签名不能以数字开头，不能含特殊字符 */
    private String sanitizeXmlTag(String name) {
        String s = name.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (s.isEmpty()) return "field";
        if (Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    private String toCsv(Map<String, Object> data) throws Exception {
        Map<String, String> flat = flattenMap(data, "");
        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(flat.keySet().toArray(new String[0]));
            writer.writeNext(flat.values().toArray(new String[0]));
        }
        // CSVWriter 末尾含换行，trim() 保持整洁
        return sw.toString().trim();
    }

    private String toFormData(Map<String, Object> data) throws Exception {
        Map<String, String> flat = flattenMap(data, "");
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : flat.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
        }
        return sb.toString();
    }

    /**
     * 嵌套 Map 展平：{a: {b: "v"}} → {"a.b": "v"}
     * FormData / CSV 序列化时使用，保证兼容嵌套 JSON/XML。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> flattenMap(Map<String, Object> data, String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key   = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.putAll(flattenMap((Map<String, Object>) value, key));
            } else {
                result.put(key, value == null ? "" : value.toString());
            }
        }
        return result;
    }
}
