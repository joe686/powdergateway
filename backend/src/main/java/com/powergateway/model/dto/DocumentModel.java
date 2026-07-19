package com.powergateway.model.dto;

import lombok.Data;
import java.util.*;

/**
 * FN-09 接口文档数据模型（Markdown/HTML 渲染数据结构）
 */
@Data
public class DocumentModel {
    private String type;              // TRANSFORM / VISUAL
    private String title;             // 顶级标题
    private String summary;           // 一句话概览
    private Map<String, Object> meta; // 基本信息 KV
    private List<Section> sections;   // 章节列表

    @Data
    public static class Section {
        private String heading;
        private String description;
        private List<List<String>> table;  // 首行为表头
        private String codeBlock;          // 代码块内容
        private String codeLang;           // json / xml / sql
    }
}
