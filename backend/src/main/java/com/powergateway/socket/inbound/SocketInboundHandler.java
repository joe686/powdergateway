package com.powergateway.socket.inbound;

import com.powergateway.socket.route.InboundSocketOrchestrator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.UUID;

/**
 * 入站 Socket ChannelHandler(v0.3.2 SOCK-5-A + SOCK-5-C · Task 2 骨架 + Task 7 整合)。
 *
 * <p>收到帧 → 生成 traceId · MDC 塞入 → 调 orchestrator 完成完整链路 → 清理 MDC。</p>
 */
public class SocketInboundHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(SocketInboundHandler.class);

    private final Charset charset;
    private final InboundSocketOrchestrator orchestrator;

    public SocketInboundHandler(Charset charset, InboundSocketOrchestrator orchestrator) {
        this.charset = charset;
        this.orchestrator = orchestrator;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] frame) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        SocketInboundContext.enter(ctx, traceId);
        try {
            String xml = new String(frame, charset);
            log.info("SOCK-5-A · 收到入站请求 · traceId={} · from={} · bytes={} · preview={}",
                    traceId, ctx.channel().remoteAddress(), frame.length,
                    xml.length() > 200 ? xml.substring(0, 200) + "..." : xml);
            if (orchestrator != null) {
                orchestrator.handle(xml, ctx.channel(), charset);
            } else {
                // 无 orchestrator(骨架/测试)· 直接关连接
                ctx.close();
            }
        } finally {
            SocketInboundContext.exit();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("SOCK-5-A · 入站异常 · {}", cause.getMessage());
        ctx.close();
    }
}
