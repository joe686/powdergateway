package com.powergateway.model.dto;

import lombok.Data;

/**
 * M1-6 报文转换调用接口请求体
 * <p>
 * 传入源报文后，系统按以下优先级确定使用哪个转换模板：
 * <ol>
 *   <li>直接指定 {@code templateId}</li>
 *   <li>不传 {@code templateId} 时，通过报文中的渠道标识字段自动路由（M1-4）</li>
 * </ol>
 */
@Data
public class ConvertRequest {

    /**
     * 转换模板 ID（与渠道路由二选一，优先使用此字段）。
     * 为 null 时系统通过渠道路由匹配模板。
     */
    private Long templateId;

    /** 源报文字符串（必填） */
    private String message;

    /** 源格式（必填）：JSON / XML / CSV / FORM_DATA */
    private String srcFormat;
}
