package com.powergateway.aop;

import java.lang.annotation.*;

/**
 * SQL 审计注解（M2-9）
 * 标注在执行器的写操作方法上（INSERT/UPDATE/DELETE），
 * SqlAuditAspect 拦截后将执行结果异步写入审计库。
 *
 * 使用方式：
 *   1. 调用方通过 AuditContextHolder.set(ctx) 填写本次操作的元数据
 *   2. 在执行器方法上标注 @AuditLog
 *   3. AOP 自动在方法返回/抛异常后写入审计记录，并清理 ThreadLocal
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
}
