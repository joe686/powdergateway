package com.powergateway.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class StatsSummaryDTO {
    private List<String> timeline;
    private List<Long> successCounts;
    private List<Long> failCounts;
    private List<Long> avgCostMs;
}
