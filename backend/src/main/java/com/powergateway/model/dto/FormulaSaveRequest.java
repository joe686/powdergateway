package com.powergateway.model.dto;

import lombok.Data;

@Data
public class FormulaSaveRequest {
    /** id 为空 = 新增；非空 = 更新 */
    private Long id;
    private String name;
    private String scene;
    private Long dbConnectionId;
    /** 公式配置 JSON 字符串 */
    private String formulaJson;
    private String remark;
}
