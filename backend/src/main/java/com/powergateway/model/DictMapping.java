package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 字典映射表实体（FN-12 · v0.2.0 ①）· 对应 dict_mapping
 */
@Data
@TableName("dict_mapping")
public class DictMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 使用场景（CR-004 · v0.2.5）：1=接口转换M1侧 2=可视化接口M2侧 3=通用共享 */
    private Integer scope;

    /** 对端系统标识（业务代号，自由文本，前端下拉去重） */
    private String systemCode;

    /** 字典标识，如 GENDER / ACCT_STATUS */
    private String dictKey;

    /** 1=出向(PG→对端)  2=入向(对端→PG) */
    private Integer direction;

    private String sourceValue;

    /** 目标值（多对一允许 target 重复） */
    private String targetValue;

    /** 中文含义 */
    private String cnLabel;

    /** 1=启用 0=停用 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
