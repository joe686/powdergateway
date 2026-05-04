package com.powergateway.model.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 接口执行请求 DTO（M2-4 INSERT 执行）。
 */
@Data
public class InterfaceExecuteRequest {

    /** 运行时参数，key 对应 fieldInsertConfig.paramKey */
    private Map<String, Object> params = new HashMap<>();
}
