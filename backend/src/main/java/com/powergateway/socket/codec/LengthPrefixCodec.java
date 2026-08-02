package com.powergateway.socket.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

/**
 * 大端定长头分帧编解码(v0.3.0 SOCK-1 · Task 3)。
 *
 * <p><b>4 字节 / 8 字节双宽度可配</b>:构造时按 {@link FramingType#LENGTH_PREFIX_BE4} 或 {@link FramingType#LENGTH_PREFIX_BE8}。
 * 8 字节场景对应用户 2026-08-02 实证的"报文前 8 位报文长度"规格。</p>
 *
 * <p><b>解码</b>:长度头值 = payload 字节数 · 剥掉长度头 · 输出纯 payload byte[]。</p>
 * <p><b>编码</b>:写入 payload.length 到长度头(大端)· 追加 payload。</p>
 *
 * <p>最大帧长默认 10MB · 超限抛 {@link TooLongFrameException}。</p>
 */
public class LengthPrefixCodec {

    /** 最大帧长限制 · 防止内存爆炸 */
    public static final int DEFAULT_MAX_FRAME_LENGTH = 10 * 1024 * 1024;

    private LengthPrefixCodec() {
    }

    public static LengthPrefixDecoder decoder(FramingType framing) {
        return new LengthPrefixDecoder(lengthOf(framing));
    }

    public static LengthPrefixDecoder decoder(FramingType framing, int maxFrameLength) {
        return new LengthPrefixDecoder(lengthOf(framing), maxFrameLength);
    }

    public static LengthPrefixEncoder encoder(FramingType framing) {
        return new LengthPrefixEncoder(lengthOf(framing));
    }

    private static int lengthOf(FramingType framing) {
        if (framing == FramingType.LENGTH_PREFIX_BE4) return 4;
        if (framing == FramingType.LENGTH_PREFIX_BE8) return 8;
        throw new IllegalArgumentException("LengthPrefixCodec 仅支持 LENGTH_PREFIX_BE4/BE8 · 收到:" + framing);
    }

    public static class LengthPrefixDecoder extends ByteToMessageDecoder {

        private final int lengthFieldLength;
        private final int maxFrameLength;

        public LengthPrefixDecoder(int lengthFieldLength) {
            this(lengthFieldLength, DEFAULT_MAX_FRAME_LENGTH);
        }

        public LengthPrefixDecoder(int lengthFieldLength, int maxFrameLength) {
            if (lengthFieldLength != 4 && lengthFieldLength != 8) {
                throw new IllegalArgumentException("lengthFieldLength 仅支持 4 或 8 · 收到:" + lengthFieldLength);
            }
            this.lengthFieldLength = lengthFieldLength;
            this.maxFrameLength = maxFrameLength;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < lengthFieldLength) {
                return;
            }
            int mark = in.readerIndex();
            long payloadLen;
            if (lengthFieldLength == 4) {
                payloadLen = in.getInt(mark) & 0xFFFFFFFFL;
            } else {
                payloadLen = in.getLong(mark);
            }
            if (payloadLen < 0) {
                throw new CorruptedFrameException("长度头为负数:" + payloadLen);
            }
            if (payloadLen > maxFrameLength) {
                throw new TooLongFrameException("帧长 " + payloadLen + " 超过上限 " + maxFrameLength);
            }
            if (in.readableBytes() < lengthFieldLength + payloadLen) {
                return;
            }
            in.skipBytes(lengthFieldLength);
            byte[] payload = new byte[(int) payloadLen];
            in.readBytes(payload);
            out.add(payload);
        }
    }

    @Sharable
    public static class LengthPrefixEncoder extends MessageToByteEncoder<byte[]> {

        private final int lengthFieldLength;

        public LengthPrefixEncoder(int lengthFieldLength) {
            if (lengthFieldLength != 4 && lengthFieldLength != 8) {
                throw new IllegalArgumentException("lengthFieldLength 仅支持 4 或 8 · 收到:" + lengthFieldLength);
            }
            this.lengthFieldLength = lengthFieldLength;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) {
            if (lengthFieldLength == 4) {
                out.writeInt(msg.length);
            } else {
                out.writeLong(msg.length);
            }
            out.writeBytes(msg);
        }
    }
}
