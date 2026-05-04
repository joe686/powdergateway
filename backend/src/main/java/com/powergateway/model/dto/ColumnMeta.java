package com.powergateway.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ColumnMeta {
    private String name;
    private String type;
    @JsonProperty("isPrimary")
    private boolean isPrimary;
    private boolean nullable;
    private String remarks;
}
