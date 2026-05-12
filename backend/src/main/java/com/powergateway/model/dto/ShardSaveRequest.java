package com.powergateway.model.dto;

import lombok.Data;

@Data
public class ShardSaveRequest {
    /** null = 新增，非 null = 更新 */
    private Long id;
    private String name;
    /** 完整 shard_rule JSON 字符串 */
    private String shardRule;
}
