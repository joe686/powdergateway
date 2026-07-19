package com.powergateway.controller;

import com.powergateway.model.InterfaceConfig;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.InterfaceFieldSchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * FN-07 字段清单 Excel 导出控制器。
 */
@RestController
@RequestMapping("/api/interface")
@RequiredArgsConstructor
@Tag(name = "字段清单导出", description = "FN-07 导出接口字段清单 Excel（双 Sheet）")
public class InterfaceFieldSchemaController {

    private final InterfaceConfigService interfaceService;
    private final InterfaceFieldSchemaService schemaService;

    @GetMapping("/{id}/field-schema/export")
    @Operation(summary = "FN-07 导出接口字段清单 Excel（双 Sheet：请求字段 / 响应字段）")
    public ResponseEntity<byte[]> export(@PathVariable Long id) throws Exception {
        InterfaceConfig cfg = interfaceService.getById(id);
        byte[] data = schemaService.exportExcel(cfg);
        String filename = URLEncoder.encode(cfg.getName() + "_字段清单.xlsx",
            StandardCharsets.UTF_8.name());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        h.set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename);
        return ResponseEntity.ok().headers(h).body(data);
    }
}
