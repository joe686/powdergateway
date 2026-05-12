package com.powergateway.model.dto;

import lombok.Data;

@Data
public class ShardRouteResult {
    private Long dbConnectionId;
    private String dbName;
    private String tableName;
}
