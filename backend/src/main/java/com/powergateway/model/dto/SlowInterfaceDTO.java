package com.powergateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlowInterfaceDTO {
    private Long   interfaceId;
    private String interfaceName;
    private long   avgCostMs;
    private long   callCount;
}
