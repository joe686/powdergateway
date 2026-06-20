package com.powergateway.aop;

import com.powergateway.common.Result;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.service.InterfaceOpTypeCache;
import com.powergateway.service.PerfStatService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 性能统计切面（SYS-2）
 *
 * BUG-009 修复：原实现在主线程同步查询 interface_config 表获取 opType，
 * 违反审计"旁观者"原则。现改为从 InterfaceOpTypeCache（Caffeine 本地缓存）读取，
 * 缓存未命中时由缓存组件回源查询并回填，大幅减少主线程 DB 开销。
 */
@Aspect
@Component
public class PerfStatAspect {

    @Autowired private PerfStatService perfStatService;
    @Autowired private InterfaceOpTypeCache interfaceOpTypeCache;

    @Around("@annotation(com.powergateway.aop.PerfStat)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long startMs = System.currentTimeMillis();
        Object[] args = pjp.getArgs();
        Long interfaceId = args.length > 0 && args[0] instanceof Long ? (Long) args[0] : null;

        // interfaceId 为 null 时跳过统计（防御性守卫）
        if (interfaceId == null) {
            return pjp.proceed();
        }

        // BUG-009 修复：从 Caffeine 本地缓存读取 opType，避免主线程同步 DB 查询
        String opType = interfaceOpTypeCache.getOpType(interfaceId);

        try {
            Object result = pjp.proceed();
            int success = 1;
            if (result instanceof Result && ((Result<?>) result).getCode() != 200) {
                success = 0;
            }
            enqueue(interfaceId, opType, (int) (System.currentTimeMillis() - startMs), success);
            return result;
        } catch (Throwable t) {
            enqueue(interfaceId, opType, (int) (System.currentTimeMillis() - startMs), 0);
            throw t;
        }
    }

    private void enqueue(Long interfaceId, String opType, int costMs, int success) {
        PerfStatRecord record = new PerfStatRecord();
        record.setInterfaceId(interfaceId);
        record.setOpType(opType);
        record.setCostMs(costMs);
        record.setSuccess(success);
        record.setStatTime(LocalDateTime.now());
        perfStatService.enqueue(record);
    }
}
