package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheStatDTO {
    private Long    interfaceId;
    private String  interfaceName;
    private Integer cacheEnabled;
    private Integer cacheTtlSeconds;
    private String  cacheKeyTemplate;
    private long    hitCount;
    private long    missCount;
}
