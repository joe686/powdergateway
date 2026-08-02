package com.powergateway.socket.inbound;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.UUID;

/**
 * 入站 Socket ChannelHandler 骨架(v0.3.2 SOCK-5-A · Task 2)。
 *
 * <p><b>骨架说明</b>:Task 2 只做 log · 记录收到的 XML。<br>
 * Task 3 InboundSocketOrchestrator 集成后 · 此 handler 调 orchestrator 完成完整链路(XML→JSON→HTTP→JSON→XML→回写)。</p>
 */
public class SocketInboundHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(SocketInboundHandler.class);

    private final Charset charset;

    public SocketInboundHandler(Charset charset) {
        this.charset = charset;
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
            // Task 3 集成:orchestrator.handle(xml, ctx.channel());
            // 骨架期:未实装 · 直接关闭连接
            ctx.close();
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
