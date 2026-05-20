package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceStatsDTO {
    private long total;
    private long draft;
    private long published;
    private long disabled;
}
