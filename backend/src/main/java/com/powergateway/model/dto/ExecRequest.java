package com.powergateway.model.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class ExecRequest {
    private Map<String, Object> params = new HashMap<>();
    private Integer page;
    private Integer pageSize;
}
