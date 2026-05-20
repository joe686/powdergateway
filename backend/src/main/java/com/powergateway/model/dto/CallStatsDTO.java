package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CallStatsDTO {
    private long totalCalls;
    private BigDecimal successRate;
    private long avgCostMs;
    private BigDecimal cacheHitRate;
}
