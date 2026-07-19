package com.powergateway.model.dto;

import lombok.Data;

/**
 * 转换模板列表查询请求（M1-5）
 */
@Data
public class TemplateQueryRequest {

    /** 当前页码，从 1 开始 */
    private int page = 1;

    /** 每页条数 */
    private int size = 10;

    /** 模板名称关键词（模糊匹配） */
    private String keyword;

    /** 仅查最新版本（默认 true） */
    private boolean latestOnly = true;

    /** 功能号精确匹配（UX-D），可空 */
    private String functionCode;
}
