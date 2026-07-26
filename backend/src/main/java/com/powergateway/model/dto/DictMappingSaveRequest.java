package com.powergateway.model.dto;

import lombok.Data;
import javax.validation.constraints.*;

/**
 * 字典映射保存请求（FN-12）
 */
@Data
public class DictMappingSaveRequest {

    @NotBlank(message = "系统代号必填")
    @Size(max = 64)
    private String systemCode;

    @NotBlank(message = "字典标识必填")
    @Size(max = 128)
    private String dictKey;

    /** 1=出向 2=入向；bidirectional=true 时后端拆两条，此字段仅代表起始方向 */
    @NotNull
    @Min(1) @Max(2)
    private Integer direction;

    @NotBlank
    @Size(max = 255)
    private String sourceValue;

    @NotBlank
    @Size(max = 255)
    private String targetValue;

    @Size(max = 255)
    private String cnLabel;

    private Integer status = 1;

    /** true = 双向（后端拆两条：direction=1 + direction=2） */
    private Boolean bidirectional = false;
}
