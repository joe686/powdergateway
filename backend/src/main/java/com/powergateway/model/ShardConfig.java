package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分库分表配置表实体类，对应 shard_config
 * shard_rule 以 JSON 字符串存储库表映射规则
 */
@Data
@TableName("shard_config")
public class ShardConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 业务模块名 */
    private String moduleName;

    /** 请求中用于路由的字段名 */
    private String requestField;

    /** 库表映射规则 JSON 字符串 */
    private String shardRule;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
