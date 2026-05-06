package com.powergateway.model.dto;

import lombok.Data;

@Data
public class CacheConfigRequest {
    private Integer cacheEnabled;
    private Integer cacheTtlSeconds;
    private String  cacheKeyTemplate;
}
