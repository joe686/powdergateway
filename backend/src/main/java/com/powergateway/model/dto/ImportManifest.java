package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * FN-11 导入 zip 中 manifest.json 的反序列化 DTO
 */
@Data
public class ImportManifest {
    /** "POWERGATEWAY_CONFIG_EXPORT" */
    private String type;
    /** 导出时间戳（ISO-8601） */
    private String exportedAt;
    /** 转换模板列表（name + 文件路径） */
    private List<ManifestEntry> templates;
    /** 可视化接口列表（name + 文件路径） */
    private List<ManifestEntry> interfaces;

    @Data
    public static class ManifestEntry {
        private String name;
        private String file;
        /** 仅模板使用：用于冲突检测三元组 */
        private String srcFormat;
        private String targetFormat;
    }
}
