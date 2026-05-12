package com.powergateway.model.dto;

import lombok.Data;

@Data
public class ShardRouteResult {
    private Long dbConnectionId;
    /** 由 ShardConfigService 查询后回填，ShardRouter 不填充此字段 */
    private String dbName;
    private String tableName;
}
