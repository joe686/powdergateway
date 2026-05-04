package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M1-1 报文格式转换接口
 * <p>
 * 提供独立的格式互转能力：JSON ↔ XML ↔ CSV ↔ FormData
 */
@Tag(name = "M1-1 报文格式转换", description = "JSON/XML/CSV/FormData 四种格式互转（12种组合）")
@RestController
@RequestMapping("/api/format-convert")
@RequiredArgsConstructor
public class FormatConvertController {

    private final FormatConverter formatConverter;

    /**
     * 格式转换
     * <p>
     * 请求体示例：
     * <pre>
     * {
     *   "message": "{\"userId\":\"001\",\"name\":\"Alice\"}",
     *   "srcFormat": "JSON",
     *   "targetFormat": "XML"
     * }
     * </pre>
     */
    @Operation(summary = "报文格式转换", description = "将源格式报文转换为目标格式，支持 JSON/XML/CSV/FORM_DATA 互转")
    @PostMapping("/convert")
    public Result<Map<String, Object>> convert(@RequestBody ConvertRequest req) {
        FormatType from = FormatType.parse(req.getSrcFormat());
        FormatType to = FormatType.parse(req.getTargetFormat());
        String result = formatConverter.convert(req.getMessage(), from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("result", result);
        data.put("srcFormat", from.name());
        data.put("targetFormat", to.name());
        return Result.success(data);
    }

    /**
     * 解析报文为字段列表（用于前端预览字段结构）
     */
    @Operation(summary = "解析报文字段", description = "将报文解析为字段 Map，便于前端预览字段结构")
    @PostMapping("/parse")
    public Result<Map<String, Object>> parse(@RequestBody ParseRequest req) {
        FormatType format = FormatType.parse(req.getFormat());
        Map<String, Object> fields = formatConverter.parseToMap(req.getMessage(), format);
        return Result.success(fields);
    }

    // ==================== DTO ====================

    public static class ConvertRequest {
        private String message;
        private String srcFormat;
        private String targetFormat;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSrcFormat() { return srcFormat; }
        public void setSrcFormat(String srcFormat) { this.srcFormat = srcFormat; }
        public String getTargetFormat() { return targetFormat; }
        public void setTargetFormat(String targetFormat) { this.targetFormat = targetFormat; }
    }

    public static class ParseRequest {
        private String message;
        private String format;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }
}
