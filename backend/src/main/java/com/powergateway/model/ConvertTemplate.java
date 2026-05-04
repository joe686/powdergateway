package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转换模板表实体类，对应 convert_template
 * mapping_rule / process_rule 以 JSON 字符串存储，业务层用 Jackson 反序列化
 */
@Data
@TableName("convert_template")
public class ConvertTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 源格式：JSON / XML / CSV / FORM */
    private String srcFormat;

    /** 目标格式：JSON / XML / CSV / FORM */
    private String targetFormat;

    /** 字段映射规则列表（JSON 字符串） */
    private String mappingRule;

    /** 字段加工规则列表（JSON 字符串） */
    private String processRule;

    /** 是否最新版本：1=是，0=否 */
    private Integer isLatest;

    /** 版本号 */
    private Integer version;

    @TableLogic
    private Integer deleted;

    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
