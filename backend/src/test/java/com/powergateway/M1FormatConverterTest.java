package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1-1 格式转换引擎单元测试
 * <p>
 * 覆盖 12 种转换组合（4×4 排除自身，即 4×3=12 种），
 * 断言每种转换结果的字段值与源数据一致。
 * <p>
 * 纯工具类测试，无需 Spring 上下文，运行速度快。
 */
@DisplayName("M1-1 格式转换引擎 - 12种组合")
class M1FormatConverterTest {

    private FormatConverter converter;
    private ObjectMapper mapper;

    /** 测试用扁平数据集（三个字段） */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        EXPECTED.put("name",  "Alice");
        EXPECTED.put("age",   "30");
        EXPECTED.put("city",  "Beijing");
    }

    /** 各格式的源报文，由 EXPECTED 生成，确保内容一致 */
    private String srcJson;
    private String srcXml;
    private String srcCsv;
    private String srcFormData;

    @BeforeEach
    void setUp() throws Exception {
        mapper    = new ObjectMapper();
        converter = new FormatConverter(mapper);

        srcJson     = "{\"name\":\"Alice\",\"age\":\"30\",\"city\":\"Beijing\"}";
        srcXml      = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<root><name>Alice</name><age>30</age><city>Beijing</city></root>";
        srcCsv      = "\"name\",\"age\",\"city\"\n\"Alice\",\"30\",\"Beijing\"";
        srcFormData = "name=Alice&age=30&city=Beijing";
    }

    // ==================== JSON 作为源 ====================

    @Test
    @DisplayName("JSON → XML")
    void jsonToXml() throws Exception {
        String result = converter.convert(srcJson, FormatType.JSON, FormatType.XML);
        Map<String, String> actual = parseXml(result);
        assertFields(actual, "JSON→XML");
    }

    @Test
    @DisplayName("JSON → CSV")
    void jsonToCsv() throws Exception {
        String result = converter.convert(srcJson, FormatType.JSON, FormatType.CSV);
        Map<String, String> actual = parseCsv(result);
        assertFields(actual, "JSON→CSV");
    }

    @Test
    @DisplayName("JSON → FormData")
    void jsonToFormData() throws Exception {
        String result = converter.convert(srcJson, FormatType.JSON, FormatType.FORM_DATA);
        Map<String, String> actual = parseFormData(result);
        assertFields(actual, "JSON→FormData");
    }

    // ==================== XML 作为源 ====================

    @Test
    @DisplayName("XML → JSON")
    void xmlToJson() throws Exception {
        String result = converter.convert(srcXml, FormatType.XML, FormatType.JSON);
        Map<String, String> actual = parseJson(result);
        assertFields(actual, "XML→JSON");
    }

    @Test
    @DisplayName("XML → CSV")
    void xmlToCsv() throws Exception {
        String result = converter.convert(srcXml, FormatType.XML, FormatType.CSV);
        Map<String, String> actual = parseCsv(result);
        assertFields(actual, "XML→CSV");
    }

    @Test
    @DisplayName("XML → FormData")
    void xmlToFormData() throws Exception {
        String result = converter.convert(srcXml, FormatType.XML, FormatType.FORM_DATA);
        Map<String, String> actual = parseFormData(result);
        assertFields(actual, "XML→FormData");
    }

    // ==================== CSV 作为源 ====================

    @Test
    @DisplayName("CSV → JSON")
    void csvToJson() throws Exception {
        String result = converter.convert(srcCsv, FormatType.CSV, FormatType.JSON);
        Map<String, String> actual = parseJson(result);
        assertFields(actual, "CSV→JSON");
    }

    @Test
    @DisplayName("CSV → XML")
    void csvToXml() throws Exception {
        String result = converter.convert(srcCsv, FormatType.CSV, FormatType.XML);
        Map<String, String> actual = parseXml(result);
        assertFields(actual, "CSV→XML");
    }

    @Test
    @DisplayName("CSV → FormData")
    void csvToFormData() throws Exception {
        String result = converter.convert(srcCsv, FormatType.CSV, FormatType.FORM_DATA);
        Map<String, String> actual = parseFormData(result);
        assertFields(actual, "CSV→FormData");
    }

    // ==================== FormData 作为源 ====================

    @Test
    @DisplayName("FormData → JSON")
    void formDataToJson() throws Exception {
        String result = converter.convert(srcFormData, FormatType.FORM_DATA, FormatType.JSON);
        Map<String, String> actual = parseJson(result);
        assertFields(actual, "FormData→JSON");
    }

    @Test
    @DisplayName("FormData → XML")
    void formDataToXml() throws Exception {
        String result = converter.convert(srcFormData, FormatType.FORM_DATA, FormatType.XML);
        Map<String, String> actual = parseXml(result);
        assertFields(actual, "FormData→XML");
    }

    @Test
    @DisplayName("FormData → CSV")
    void formDataToCsv() throws Exception {
        String result = converter.convert(srcFormData, FormatType.FORM_DATA, FormatType.CSV);
        Map<String, String> actual = parseCsv(result);
        assertFields(actual, "FormData→CSV");
    }

    // ==================== 特殊字符 & 编码测试 ====================

    @Test
    @DisplayName("FormData 特殊字符编码（含空格和中文）")
    void formDataSpecialChars() throws Exception {
        String jsonWithSpecial = "{\"desc\":\"hello world\",\"remark\":\"测试\"}";
        String formData = converter.convert(jsonWithSpecial, FormatType.JSON, FormatType.FORM_DATA);
        // 转回 JSON 验证值完整
        String backToJson = converter.convert(formData, FormatType.FORM_DATA, FormatType.JSON);
        @SuppressWarnings("unchecked")
        Map<String, String> result = mapper.readValue(backToJson, LinkedHashMap.class);
        assertEquals("hello world", result.get("desc"), "含空格的值应正确编解码");
        assertEquals("测试", result.get("remark"), "中文值应正确编解码");
    }

    @Test
    @DisplayName("嵌套 JSON → XML → JSON 往返一致")
    void nestedJsonRoundTrip() throws Exception {
        String nestedJson = "{\"user\":{\"id\":\"1\",\"name\":\"Bob\"}}";
        String xml  = converter.convert(nestedJson, FormatType.JSON, FormatType.XML);
        String back = converter.convert(xml, FormatType.XML, FormatType.JSON);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = mapper.readValue(back, LinkedHashMap.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertNotNull(user, "嵌套对象 user 应存在");
        assertEquals("Bob", user.get("name"), "嵌套字段 name 应为 Bob");
    }

    // ==================== 断言辅助 ====================

    private void assertFields(Map<String, String> actual, String label) {
        EXPECTED.forEach((key, expectedVal) ->
            assertEquals(expectedVal, actual.get(key),
                label + " 转换后字段 [" + key + "] 值不匹配")
        );
    }

    // ==================== 解析辅助（验证用） ====================

    @SuppressWarnings("unchecked")
    private Map<String, String> parseJson(String json) throws Exception {
        return mapper.readValue(json, LinkedHashMap.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseXml(String xml) throws Exception {
        Document doc  = DocumentHelper.parseText(xml);
        Element root  = doc.getRootElement();
        Map<String, String> map = new LinkedHashMap<>();
        for (Element el : (java.util.List<Element>) root.elements()) {
            map.put(el.getName(), el.getText());
        }
        return map;
    }

    private Map<String, String> parseCsv(String csv) throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader(csv.trim()))) {
            String[] headers = reader.readNext();
            String[] values  = reader.readNext();
            Map<String, String> map = new LinkedHashMap<>();
            if (headers != null && values != null) {
                for (int i = 0; i < headers.length; i++) {
                    map.put(headers[i].trim(), i < values.length ? values[i] : "");
                }
            }
            return map;
        }
    }

    private Map<String, String> parseFormData(String formData) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : formData.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key   = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                String value = idx < pair.length() - 1
                        ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                        : "";
                map.put(key, value);
            }
        }
        return map;
    }
}
