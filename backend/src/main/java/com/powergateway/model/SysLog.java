package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志实体，对应配置库 sys_log 表（SYS-1）
 * 日志记录由 SysLogAspect 异步写入，过期记录由 SysLogArchiveJob 归档到 sys_log_history
 */
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
