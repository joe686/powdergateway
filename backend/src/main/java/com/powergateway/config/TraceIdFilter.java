package com.powergateway.config;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求级 trace_id 生成 Filter(v0.3.1 · Task 6 · Q23=A)。
 *
 * <p>请求进入时生成 UUID(去 -)塞入 MDC.traceId + 响应头 X-Trace-Id · finally 清理。</p>
 * <p>三 AOP(SqlAuditAspect / SysLogAspect / PerfStatAspect)从 MDC 读并写入实体的 traceId 字段。</p>
 * <p>如上游透传 X-Trace-Id 头 · 沿用(不重新生成)· 支持跨服务追溯。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER_NAME = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String traceId = null;
        if (request instanceof HttpServletRequest) {
            String upstream = ((HttpServletRequest) request).getHeader(HEADER_NAME);
            if (upstream != null && !upstream.trim().isEmpty()) {
                traceId = upstream.trim();
            }
        }
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, traceId);
        if (response instanceof HttpServletResponse) {
            ((HttpServletResponse) response).setHeader(HEADER_NAME, traceId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 从 MDC 读当前请求的 traceId · null 表示不在请求上下文(可能是异步/定时任务)*/
    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }
}
