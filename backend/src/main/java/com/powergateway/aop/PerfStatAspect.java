package com.powergateway.aop;

import com.powergateway.common.Result;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.service.PerfStatService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
public class PerfStatAspect {

    @Autowired private PerfStatService perfStatService;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    @Around("@annotation(com.powergateway.aop.PerfStat)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long startMs = System.currentTimeMillis();
        Object[] args = pjp.getArgs();
        Long interfaceId = args.length > 0 && args[0] instanceof Long ? (Long) args[0] : null;

        // interfaceId 为 null 时跳过统计（防御性守卫）
        if (interfaceId == null) {
            return pjp.proceed();
        }

        InterfaceConfig config = interfaceConfigMapper.selectById(interfaceId);
        String opType = config != null ? config.getType() : "UNKNOWN";

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
