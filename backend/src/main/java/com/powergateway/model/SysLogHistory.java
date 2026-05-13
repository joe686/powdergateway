package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志历史归档实体，对应配置库 sys_log_history 表（SYS-1 CHG-006）
 * 由 SysLogArchiveJob 每天凌晨3点将超过保留期的 sys_log 记录归档至此表
 */
@Data
@TableName("sys_log_history")
public class SysLogHistory {
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
    private LocalDateTime archivedTime;
}
