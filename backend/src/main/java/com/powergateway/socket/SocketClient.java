package com.powergateway.socket;

import com.powergateway.socket.codec.FramingType;

import java.nio.charset.Charset;

/**
 * TCP Socket 客户端门面(v0.3.0 SOCK-1 骨架)。
 *
 * <p>短连接 · 三分帧 · 双编码 · 同步 send-receive。</p>
 *
 * <p><b>骨架说明</b>:Task 1 仅定义 API 签名 · 具体实装在 Task 4(依赖 XmlBoundaryCodec/LengthPrefixCodec)。</p>
 */
public class SocketClient {

    /**
     * 同步发送 payload 到 host:port · 等待应答字节数组。
     *
     * @param host           目标 IP
     * @param port           目标端口
     * @param payload        请求字节(已按 charset 编码)
     * @param framing        分帧策略
     * @param charset        编码(UTF-8 / GBK)· 供 pipeline 编解码器用
     * @param connTimeoutMs  连接超时毫秒
     * @param readTimeoutMs  读超时毫秒
     * @return 应答字节(未按 charset 解码 · 由调用方按 charset 解码)
     */
    public byte[] send(String host,
                       int port,
                       byte[] payload,
                       FramingType framing,
                       Charset charset,
                       int connTimeoutMs,
                       int readTimeoutMs) {
        throw new UnsupportedOperationException(
                "SocketClient.send 尚未实装 · Task 1 骨架 · 实装见 v0.3.0 SOCK-1 Task 4");
    }
}
