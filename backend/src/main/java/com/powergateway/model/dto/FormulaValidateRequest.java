package com.powergateway.model.dto;

import lombok.Data;

@Data
public class FormulaValidateRequest {
    private Long dbConnectionId;
    private String formulaJson;
}
