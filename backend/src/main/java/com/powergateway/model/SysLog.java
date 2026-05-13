package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String action;
    private String operator;
    private String opIp;
    private LocalDateTime opTime;
    private String level;
    private String errorMsg;
    private Integer costMs;
}
