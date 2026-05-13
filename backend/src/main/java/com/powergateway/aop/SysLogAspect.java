package com.powergateway.aop;

import cn.dev33.satoken.stp.StpUtil;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志 AOP 切面（SYS-1）
 *
 * 拦截所有标注 @SysLogRecord 的方法：
 *   1. 记录开始时间
 *   2. 执行目标方法
 *   3. 成功 → level=INFO；抛异常 → level=ERROR，截取 error_msg
 *   4. 投入 SysLogService 的异步队列
 */
@Aspect
@Component
public class SysLogAspect {

    @Autowired
    private SysLogService sysLogService;

    @Around("@annotation(sysLogRecord)")
    public Object around(ProceedingJoinPoint pjp, SysLogRecord sysLogRecord) throws Throwable {
        long startMs = System.currentTimeMillis();
        LocalDateTime opTime = LocalDateTime.now();
        String operator = resolveOperator();
        String opIp = resolveOpIp();

        try {
            Object result = pjp.proceed();
            sysLogService.enqueue(buildLog(sysLogRecord, operator, opIp, opTime,
                    (int) (System.currentTimeMillis() - startMs), "INFO", null));
            return result;
        } catch (Throwable t) {
            sysLogService.enqueue(buildLog(sysLogRecord, operator, opIp, opTime,
                    (int) (System.currentTimeMillis() - startMs), "ERROR",
                    truncate(t.getMessage(), 1000)));
            throw t;
        }
    }

    private SysLog buildLog(SysLogRecord annotation, String operator, String opIp,
                             LocalDateTime opTime, int costMs, String level, String errorMsg) {
        SysLog log = new SysLog();
        log.setModule(annotation.module());
        log.setAction(annotation.action());
        log.setOperator(operator);
        log.setOpIp(opIp);
        log.setOpTime(opTime);
        log.setCostMs(costMs);
        log.setLevel(level);
        log.setErrorMsg(errorMsg);
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
            if (attrs != null) return attrs.getRequest().getRemoteAddr();
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
