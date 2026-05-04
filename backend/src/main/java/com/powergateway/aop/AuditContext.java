package com.powergateway.aop;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 审计上下文数据（M2-9）
 * 执行器在调用 @AuditLog 方法前，通过 AuditContextHolder.set(ctx) 填充。
 */
@Data
@Accessors(chain = true)
public class AuditContext {

    /** 接口配置 ID */
    private Long interfaceId;

    /** 执行的 SQL 文本 */
    private String sqlText;

    /** 操作类型：INSERT / UPDATE / DELETE */
    private String opType;

    /** 目标数据库标识 */
    private String targetDb;

    /** 目标数据表名 */
    private String targetTable;

    /**
     * 修改前数据快照（JSON 字符串）
     * UPDATE / DELETE 场景由执行器在操作前查询并填充
     */
    private String beforeSnapshot;
}
