package com.powergateway.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL 审计日志实体，对应审计库 sql_audit_log 表（M2-9）
 * 审计表无软删除字段，过期记录由定时清理任务物理删除
 */
@Data
@TableName("sql_audit_log")
public class SqlAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 interface_config.id */
    private Long interfaceId;

    /** 执行的 SQL 文本 */
    private String sqlText;

    /** 操作类型：INSERT / UPDATE / DELETE */
    private String opType;

    /** 操作人（来自 Sa-Token 登录态，未登录时为 system） */
    private String operator;

    /** 操作来源 IP */
    private String opIp;

    /** 操作时间 */
    private LocalDateTime opTime;

    /** 目标数据库标识 */
    private String targetDb;

    /** 目标数据表名 */
    private String targetTable;

    /** 执行结果：SUCCESS / FAIL */
    private String result;

    /** 失败时的错误信息 */
    private String errorMsg;

    /** 修改前数据快照（JSON 字符串，UPDATE/DELETE 时由执行器填充到 AuditContext） */
    private String beforeSnapshot;
}
