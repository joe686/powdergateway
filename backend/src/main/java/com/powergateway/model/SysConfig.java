package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置表实体类（KV 结构），对应 sys_config
 * 主键为 config_key（字符串），无 id 自增列，无软删除字段
 */
@Data
@TableName("sys_config")
public class SysConfig {

    /** 配置键，主键 */
    @TableId(type = IdType.INPUT)
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;

    @TableField("value_type")
    private String valueType;

    @TableField("group_name")
    private String groupName;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
