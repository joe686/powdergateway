package com.powergateway.aop;

import com.powergateway.common.Result;
import com.powergateway.model.PerfStatRecord;
import com.powergateway.service.InterfaceOpTypeCache;
import com.powergateway.service.PerfStatService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
        // Wave6 回归修复：UX-E FN-06 给 ExecController.execute 前置了 HttpServletRequest 参数，
        // 把 interfaceId 从 args[0] 挤到 args[1]，aspect 拿不到 id 直接跳过 enqueue。
        // 改为扫描 args 里第一个 Long（interfaceId 是所有 @PerfStat 方法唯一的 Long 参数）。
        Long interfaceId = null;
        for (Object a : args) {
            if (a instanceof Long) { interfaceId = (Long) a; break; }
        }

        // interfaceId 为 null 时跳过统计（防御性守卫）
        if (interfaceId == null) {
            return pjp.proceed();
        }

        // BUG-009 修复：从 Caffeine 本地缓存读取 opType，避免主线程同步 DB 查询
        String opType = interfaceOpTypeCache.getOpType(interfaceId);

        try {
            Object result = pjp.proceed();
            int success = judgeSuccess(result);
            enqueue(interfaceId, opType, (int) (System.currentTimeMillis() - startMs), success);
            return result;
        } catch (Throwable t) {
            enqueue(interfaceId, opType, (int) (System.currentTimeMillis() - startMs), 0);
            throw t;
        }
    }

    /**
     * 兼容三种返回：
     * - Result<T>：code 非 200 判 fail
     * - ResponseEntity<Result<T>>（UX-E FN-06 Accept=JSON 分支）：抽出 body 后再判
     * - ResponseEntity<byte[]> / 其它（Accept=XML/CSV/FORM_DATA）：靠 HTTP status 判
     */
    private int judgeSuccess(Object result) {
        if (result instanceof Result) {
            return ((Result<?>) result).getCode() == 200 ? 1 : 0;
        }
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> re = (ResponseEntity<?>) result;
            if (!re.getStatusCode().is2xxSuccessful()) return 0;
            Object body = re.getBody();
            if (body instanceof Result) {
                return ((Result<?>) body).getCode() == 200 ? 1 : 0;
            }
            return 1;
        }
        return 1;
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
