package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.exception.BusinessException;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报文调试辅助工具 API(v0.3.0 SOCK-3 · Task 8)。
 *
 * <p>MessageDebug.vue 前端调用 · 需登录。</p>
 */
@RestController
@RequestMapping("/api/tools")
@Tag(name = "报文调试工具", description = "报文调试辅助功能:XML 扁平化等")
public class MessageToolsController {

    @Autowired private FormatConverter formatConverter;

    /**
     * XML 扁平化预览:解析 XML → 嵌套 Map → 展平为 dot.notation Map。
     *
     * <p>供 SOCK-1 出站 XML 应答字段调试参考 · 与 SocketExecutor 输出格式一致。</p>
     */
    @PostMapping("/xml-flatten")
    @Operation(summary = "XML 扁平化(v0.3.0 SOCK-3)")
    public Result<Map<String, Object>> flattenXml(@RequestBody XmlFlattenRequest req) {
        if (req == null || req.getXml() == null || req.getXml().trim().isEmpty()) {
            throw new BusinessException(400, "xml 字段必填");
        }
        String prefix = req.getPrefix() == null ? "" : req.getPrefix();
        Map<String, Object> nested = formatConverter.parseToMap(req.getXml().trim(), FormatType.XML);
        Map<String, String> flat = FormatConverter.flattenMap(nested, prefix);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flattened", flat);
        result.put("keyCount", flat.size());
        return Result.success(result);
    }

    public static class XmlFlattenRequest {
        private String xml;
        private String prefix;

        public String getXml() { return xml; }
        public void setXml(String xml) { this.xml = xml; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
    }
}
