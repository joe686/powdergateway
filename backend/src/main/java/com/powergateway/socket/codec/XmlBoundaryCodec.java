package com.powergateway.socket.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.Charset;
import java.util.List;

/**
 * XML 自闭合边界分帧编解码(v0.3.0 SOCK-1 · Task 2)。
 *
 * <p><b>解码(入站)</b>:累积字节 · 跳过 {@code <?xml ... ?>} 声明 · 提取首个元素标签名 root · 找 {@code </root>} 结束边界 · 切帧输出。
 * 允许 XML 声明属性顺序变体(如 {@code <?xml  encoding="UTF-8" version="1.0" ?>})+ 无声明的裸 XML。</p>
 *
 * <p><b>编码(出站)</b>:直接写 payload · 不加边界字节 · 边界由 payload 自带的 XML 结束标签承担。</p>
 *
 * <p><b>说明</b>:每帧独立识别根标签 · 支持多帧连续(每帧可以是不同 XML 根)。若数据不足或未收齐边界 · 保留字节等下次 decode。</p>
 */
public class XmlBoundaryCodec {

    private XmlBoundaryCodec() {
    }

    public static XmlBoundaryDecoder decoder(Charset charset) {
        return new XmlBoundaryDecoder(charset);
    }

    public static XmlBoundaryEncoder encoder() {
        return new XmlBoundaryEncoder();
    }

    /**
     * 入站解码器:识别 XML 根标签边界 · 切帧输出 byte[]。
     */
    public static class XmlBoundaryDecoder extends ByteToMessageDecoder {

        private final Charset charset;

        public XmlBoundaryDecoder(Charset charset) {
            this.charset = charset;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            int reader = in.readerIndex();
            int writer = in.writerIndex();
            if (reader >= writer) {
                return;
            }
            String rootTag = detectRootTag(in, reader, writer);
            if (rootTag == null) {
                return;
            }
            byte[] endBytes = ("</" + rootTag + ">").getBytes(charset);
            int endIdx = indexOf(in, reader, writer, endBytes);
            if (endIdx < 0) {
                return;
            }
            int frameEnd = endIdx + endBytes.length;
            int frameLen = frameEnd - reader;
            byte[] frame = new byte[frameLen];
            in.readBytes(frame);
            out.add(frame);
        }

        /**
         * 扫描字节 · 跳过 {@code <?xml ... ?>} 声明 · 提取首个元素的标签名。
         * 数据不足或未识别到完整根标签 · 返回 null。
         */
        private String detectRootTag(ByteBuf buf, int reader, int writer) {
            int i = reader;
            i = skipWhitespace(buf, i, writer);
            if (i >= writer) {
                return null;
            }
            byte b = buf.getByte(i);
            if (b != (byte) '<') {
                return null;
            }
            // 跳过 <?xml ... ?> 声明(若有)
            if (i + 1 < writer && buf.getByte(i + 1) == (byte) '?') {
                int closeQ = findAscii(buf, i + 2, writer, "?>");
                if (closeQ < 0) {
                    return null;
                }
                i = closeQ + 2;
                i = skipWhitespace(buf, i, writer);
            }
            if (i >= writer || buf.getByte(i) != (byte) '<') {
                return null;
            }
            // 跳过注释 / DOCTYPE
            while (i + 3 < writer && buf.getByte(i) == (byte) '<' && buf.getByte(i + 1) == (byte) '!') {
                int closeCmt = findAscii(buf, i, writer, ">");
                if (closeCmt < 0) {
                    return null;
                }
                i = closeCmt + 1;
                i = skipWhitespace(buf, i, writer);
                if (i >= writer || buf.getByte(i) != (byte) '<') {
                    return null;
                }
            }
            // 读取标签名 · 从 i+1 开始到空格 / > / / 结束
            int nameStart = i + 1;
            int nameEnd = nameStart;
            while (nameEnd < writer) {
                byte c = buf.getByte(nameEnd);
                if (c == (byte) ' ' || c == (byte) '\t' || c == (byte) '\r' || c == (byte) '\n'
                        || c == (byte) '>' || c == (byte) '/') {
                    break;
                }
                nameEnd++;
            }
            if (nameEnd >= writer || nameStart == nameEnd) {
                return null;
            }
            byte[] nameBytes = new byte[nameEnd - nameStart];
            buf.getBytes(nameStart, nameBytes);
            return new String(nameBytes, charset);
        }

        private static int skipWhitespace(ByteBuf buf, int from, int to) {
            int i = from;
            while (i < to) {
                byte c = buf.getByte(i);
                if (c == (byte) ' ' || c == (byte) '\t' || c == (byte) '\r' || c == (byte) '\n') {
                    i++;
                } else {
                    break;
                }
            }
            return i;
        }

        /**
         * 在 [from, to) 范围找 ascii 字节序列 · 返回起始 index · 未找到返回 -1。
         */
        private static int findAscii(ByteBuf buf, int from, int to, String ascii) {
            byte[] target = ascii.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            return indexOf(buf, from, to, target);
        }

        private static int indexOf(ByteBuf buf, int from, int to, byte[] target) {
            if (target.length == 0 || to - from < target.length) {
                return -1;
            }
            outer:
            for (int i = from; i <= to - target.length; i++) {
                for (int j = 0; j < target.length; j++) {
                    if (buf.getByte(i + j) != target[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

    /**
     * 出站编码器:直接写 payload · 不加边界。
     * 标记 @Sharable · 无状态 · 单实例可复用。
     */
    @Sharable
    public static class XmlBoundaryEncoder extends MessageToByteEncoder<byte[]> {

        @Override
        protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) {
            out.writeBytes(msg);
        }
    }
}
