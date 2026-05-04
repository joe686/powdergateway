package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库连接配置表实体类，对应 db_connection
 * password 字段 AES-128 加密存储，查询后需在业务层解密
 */
@Data
@TableName("db_connection")
public class DbConnection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 数据库类型：MySQL / Oracle / PostgreSQL */
    private String dbType;

    private String url;

    private String username;

    /** AES 加密存储，不允许明文 */
    private String password;

    /** 环境：dev / test / prod */
    private String env;

    private Integer poolSize;

    /** 连接超时（毫秒） */
    private Integer timeout;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
