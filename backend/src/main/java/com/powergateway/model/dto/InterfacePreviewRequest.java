package com.powergateway.model.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 接口预览请求 DTO（M2-3 预览查询接口）。
 */
@Data
public class InterfacePreviewRequest {

    /** 运行时参数，key 对应 conditionDef.paramKey */
    private Map<String, Object> params = new HashMap<>();
}
