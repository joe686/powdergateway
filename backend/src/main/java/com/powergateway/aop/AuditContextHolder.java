package com.powergateway.aop;

/**
 * 审计上下文 ThreadLocal 持有者（M2-9）
 * 用于在执行器与 AOP 切面之间传递本次操作的元数据，生命周期为单次请求线程。
 */
public class AuditContextHolder {

    private static final ThreadLocal<AuditContext> HOLDER = new ThreadLocal<>();

    public static void set(AuditContext ctx) {
        HOLDER.set(ctx);
    }

    public static AuditContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private AuditContextHolder() {}
}
