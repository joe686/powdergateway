package com.powergateway.controller;

import com.powergateway.common.Result;
import com.powergateway.model.dto.ConvertRequest;
import com.powergateway.service.ConvertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * M1-6 报文转换调用接口
 * <p>
 * 串联全流程：读模板（Redis 缓存 TTL 10min）→ 格式转换 → 字段映射 → 字段加工 → 返回结果
 */
@Tag(name = "M1-6 报文转换调用接口", description = "串联格式转换→字段映射→字段加工全流程，支持按模板ID或渠道自动路由")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ConvertController {

    private final ConvertService convertService;

    /**
     * 报文转换入口
     *
     * <p>请求体示例（按模板 ID）：
     * <pre>
     * {
     *   "templateId": 1,
     *   "message": "{\"user_id\":\"001\",\"name\":\"Alice\"}",
     *   "srcFormat": "JSON"
     * }
     * </pre>
     *
     * <p>响应示例：
     * <pre>
     * {
     *   "code": 200,
     *   "data": {
     *     "result": "&lt;root&gt;&lt;userId&gt;001&lt;/userId&gt;&lt;/root&gt;",
     *     "targetFormat": "XML",
     *     "costMs": 12
     *   }
     * }
     * </pre>
     */
    @Operation(
            summary = "报文转换",
            description = "串联格式转换→字段映射→字段加工，返回目标格式报文。"
                    + "templateId 和渠道路由二选一，templateId 优先。"
    )
    @PostMapping("/convert")
    public Result<Map<String, Object>> convert(@RequestBody ConvertRequest req) {
        return Result.success(convertService.convert(req));
    }
}
