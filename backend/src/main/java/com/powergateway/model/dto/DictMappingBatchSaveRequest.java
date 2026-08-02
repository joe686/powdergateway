package com.powergateway.model.dto;

import lombok.Data;
import javax.validation.constraints.*;
import java.util.List;

/**
 * 字典映射批量保存请求（v0.2.5 CR-004 · FB-048）。
 * <p>顶部锁 {system, dictKey, direction, scope} 四字段整批共享,
 * items 每条只需 {source, target, cnLabel}。
 * <p>单次上限 200 条 · 超限提示走 FN-11 Excel 导入。
 */
@Data
public class DictMappingBatchSaveRequest {

    /** 使用场景 · 1=接口转换M1侧 2=可视化接口M2侧 3=通用共享(默认) */
    @Min(1) @Max(3)
    private Integer scope = 3;

    @NotBlank(message = "系统代号必填")
    @Size(max = 64)
    private String systemCode;

    @NotBlank(message = "字典标识必填")
    @Size(max = 128)
    private String dictKey;

    @NotNull @Min(1) @Max(2)
    private Integer direction;

    @NotNull @Size(min = 1, max = 200, message = "items 单次上限 200 条")
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank
        @Size(max = 255)
        private String sourceValue;

        @NotBlank
        @Size(max = 255)
        private String targetValue;

        @Size(max = 255)
        private String cnLabel;
    }
}
