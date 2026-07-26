package com.powergateway.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 字典映射 lookup 结果 DTO（FN-12 · Task 7）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictMappingLookupResult {
    private String targetValue;
    private String cnLabel;
}
