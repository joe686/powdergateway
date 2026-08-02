package com.powergateway.socket.inbound;

import io.netty.channel.ChannelHandlerContext;
import org.slf4j.MDC;

/**
 * 入站 Socket 上下文(v0.3.2 SOCK-5-A · Task 2)。
 *
 * <p>ThreadLocal 保存 ChannelHandlerContext · orchestrator 处理完拿回来从原 Channel 回写(同步应答通道)。</p>
 * <p>请求进入时同步塞 MDC.traceId(与 TraceIdFilter 语义一致 · 供 sys_log/sql_audit_log/perf_stat AOP 读取)。</p>
 *
 * <p><b>限制</b>(v0.3.2 · Q20=C 短连接场景足够):ThreadLocal 只在单请求-单线程范式下工作。
 * 高并发或异步链路可能需要 request-scoped 上下文 · v0.4.0+ 优化。</p>
 */
public final class SocketInboundContext {

    public static final String MDC_TRACE_ID = "traceId";

    private static final ThreadLocal<ChannelHandlerContext> CHANNEL_CTX = new ThreadLocal<>();

    private SocketInboundContext() {
    }

    /**
     * 请求进入时设置:保存 Channel + 生成 traceId 塞入 MDC(若上游未透传)。
     *
     * @param ctx     Netty ChannelHandlerContext(用于回写应答)
     * @param traceId 已生成的 UUID 或从上游 X-Trace-Id 透传值 · null 时不 override MDC
     */
    public static void enter(ChannelHandlerContext ctx, String traceId) {
        CHANNEL_CTX.set(ctx);
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
    }

    /** 请求结束时清理 · 避免线程复用泄露 */
    public static void exit() {
        CHANNEL_CTX.remove();
        MDC.remove(MDC_TRACE_ID);
    }

    public static ChannelHandlerContext currentChannel() {
        return CHANNEL_CTX.get();
    }

    public static String currentTraceId() {
        return MDC.get(MDC_TRACE_ID);
    }
}
