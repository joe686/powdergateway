package com.powergateway.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.model.SqlAuditLog;
import com.powergateway.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * SQL 审计 AOP 切面（M2-9）
 *
 * 拦截所有标注 @AuditLog 的方法（未来的 InsertExecutor/UpdateExecutor/DeleteExecutor）：
 *   1. 从 AuditContextHolder 读取本次操作元数据
 *   2. 执行目标方法
 *   3. 根据是否抛异常填写 result=SUCCESS/FAIL
 *   4. 投入 AuditLogService 的异步队列
 *   5. finally 块清理 ThreadLocal，防止上下文泄漏
 */
@Aspect
@Component
public class SqlAuditAspect {

    @Autowired
    private AuditLogService auditLogService;

    @Around("@annotation(com.powergateway.aop.AuditLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        AuditContext ctx = AuditContextHolder.get();
        LocalDateTime opTime = LocalDateTime.now();
        String operator = resolveOperator();
        String opIp = resolveOpIp();

        try {
            Object result = pjp.proceed();
            if (ctx != null) {
                auditLogService.enqueue(buildLog(ctx, operator, opIp, opTime, "SUCCESS", null));
            }
            return result;
        } catch (Throwable t) {
            if (ctx != null) {
                auditLogService.enqueue(buildLog(ctx, operator, opIp, opTime, "FAIL",
                        truncate(t.getMessage(), 1000)));
            }
            throw t;
        } finally {
            AuditContextHolder.clear();
        }
    }

    private SqlAuditLog buildLog(AuditContext ctx, String operator, String opIp,
                                  LocalDateTime opTime, String result, String errorMsg) {
        SqlAuditLog log = new SqlAuditLog();
        log.setInterfaceId(ctx.getInterfaceId());
        log.setSqlText(ctx.getSqlText());
        log.setOpType(ctx.getOpType());
        log.setOperator(operator);
        log.setOpIp(opIp);
        log.setOpTime(opTime);
        log.setTargetDb(ctx.getTargetDb());
        log.setTargetTable(ctx.getTargetTable());
        log.setResult(result);
        log.setErrorMsg(errorMsg);
        log.setBeforeSnapshot(ctx.getBeforeSnapshot());
        return log;
    }

    private String resolveOperator() {
        try {
            Object loginId = StpUtil.getLoginId(null);
            return loginId != null ? loginId.toString() : "system";
        } catch (Exception e) {
            return "system";
        }
    }

    private String resolveOpIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
