package com.powergateway.model.dto;

import lombok.Data;

@Data
public class AlertConfigRequest {
    private Double failRate;
    private Integer responseMs;
}
