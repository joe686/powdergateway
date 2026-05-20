package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CallTrendDTO {
    private List<String> timeline;
    private List<Long>   successCounts;
    private List<Long>   failCounts;
}
