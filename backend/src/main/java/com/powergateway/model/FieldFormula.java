package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用字段公式表实体类，对应 field_formula
 * formula_json 以 JSON 字符串存储公式配置
 */
@Data
@TableName("field_formula")
public class FieldFormula {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公式名称，唯一 */
    private String name;

    /** 所属业务场景 */
    private String scene;

    /** 关联数据库连接 db_connection.id */
    private Long dbConnectionId;

    /** 公式配置 JSON 字符串（条件、运算、接口字段关联） */
    private String formulaJson;

    private String remark;

    @TableLogic
    private Integer deleted;

    private String creator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
