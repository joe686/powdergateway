package com.powergateway.aop;

import java.lang.annotation.*;

/**
 * 操作日志记录注解（SYS-1）
 * 标注在 Controller 方法上，SysLogAspect 拦截后将操作记录异步写入 sys_log 表。
 *
 * 使用方式：
 *   @SysLogRecord(module = "模板管理", action = "保存模板")
 *   public Result<Void> save(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SysLogRecord {
    /** 操作模块，如"模板管理" */
    String module();
    /** 操作动作，如"保存模板" */
    String action();
}
