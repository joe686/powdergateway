package com.powergateway;

import com.powergateway.socket.codec.XmlBoundaryCodec;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SOCK-1 · XmlBoundaryCodec 分帧编解码单元测试(v0.3.0 · Task 2)。
 *
 * <p>验证:完整帧 · 分片重组 · 多帧连续 · {@code <?xml} 声明变体容错 · 裸 XML · 编码器透传。</p>
 */
@ActiveProfiles("test")
class SOCK1XmlBoundaryCodecTest {

    private final Charset UTF8 = StandardCharsets.UTF_8;
    private final Charset GBK = Charset.forName("GBK");

    private EmbeddedChannel newDecoderChannel(Charset charset) {
        return new EmbeddedChannel(XmlBoundaryCodec.decoder(charset));
    }

    // ============ 完整帧 ============

    @Test
    @DisplayName("单帧完整 UTF-8 XML · 一次解码输出整帧")
    void 单帧完整_UTF8_一次解码() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Transaction><FunctionId>181345</FunctionId></Transaction>";
        EmbeddedChannel ch = newDecoderChannel(UTF8);
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(xml.getBytes(UTF8)));

        byte[] frame = ch.readInbound();
        assertNotNull(frame, "应解出一帧");
        assertEquals(xml, new String(frame, UTF8));
        assertNull(ch.readInbound(), "只有一帧");
    }

    @Test
    @DisplayName("裸 XML 无 <?xml 声明 · 也能识别根标签")
    void 裸XML_无声明_识别根标签() {
        String xml = "<Req><Body>data</Body></Req>";
        EmbeddedChannel ch = newDecoderChannel(UTF8);
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(xml.getBytes(UTF8)));

        byte[] frame = ch.readInbound();
        assertNotNull(frame);
        assertEquals(xml, new String(frame, UTF8));
    }

    // ============ 分片(边界跨包)============

    @Test
    @DisplayName("分片写入 · 累积到完整帧才输出")
    void 分片_累积到完整才输出() {
        String xml = "<Transaction><FunctionId>181345</FunctionId></Transaction>";
        byte[] all = xml.getBytes(UTF8);
        EmbeddedChannel ch = newDecoderChannel(UTF8);

        // 第一片:前 20 字节
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(all, 0, 20));
        assertNull(ch.readInbound(), "第一片不完整 · 不应输出");

        // 第二片:剩余
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(all, 20, all.length - 20));
        byte[] frame = ch.readInbound();
        assertNotNull(frame);
        assertArrayEquals(all, frame);
    }

    @Test
    @DisplayName("分片:</根标签> 结束标签跨包 · 也能正确切帧")
    void 分片_结束标签跨包() {
        String xml = "<Req><Data>abc</Data></Req>";
        byte[] all = xml.getBytes(UTF8);
        EmbeddedChannel ch = newDecoderChannel(UTF8);
        // 拆到 "</Re" 与 "q>" 之间
        int splitAt = xml.indexOf("</Req>") + 3; // "</Re" 结束
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(all, 0, splitAt));
        assertNull(ch.readInbound());
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(all, splitAt, all.length - splitAt));
        byte[] frame = ch.readInbound();
        assertNotNull(frame);
        assertArrayEquals(all, frame);
    }

    // ============ 多帧连续 ============

    @Test
    @DisplayName("多帧连续同一次写入 · 逐帧输出")
    void 多帧连续同一次写入() {
        String frame1 = "<Req><Id>1</Id></Req>";
        String frame2 = "<Req><Id>2</Id></Req>";
        String frame3 = "<Ack>ok</Ack>";
        String combined = frame1 + frame2 + frame3;
        EmbeddedChannel ch = newDecoderChannel(UTF8);
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(combined.getBytes(UTF8)));

        byte[] out1 = ch.readInbound();
        byte[] out2 = ch.readInbound();
        byte[] out3 = ch.readInbound();
        assertEquals(frame1, new String(out1, UTF8));
        assertEquals(frame2, new String(out2, UTF8));
        assertEquals(frame3, new String(out3, UTF8));
        assertNull(ch.readInbound());
    }

    // ============ <?xml 声明变体容错 ============

    @Test
    @DisplayName("<?xml 属性顺序反(encoding 在 version 前)· 双空格 · 仍能识别根标签")
    void xml声明_属性顺序反_双空格_容错() {
        // host 报文里的实际写法:<?xml  encoding="UTF-8" version="1.0" ?>
        String xml = "<?xml  encoding=\"UTF-8\" version=\"1.0\" ?><Transaction><FunctionId>181345</FunctionId></Transaction>";
        EmbeddedChannel ch = newDecoderChannel(UTF8);
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(xml.getBytes(UTF8)));

        byte[] frame = ch.readInbound();
        assertNotNull(frame);
        assertEquals(xml, new String(frame, UTF8));
    }

    @Test
    @DisplayName("<?xml 声明前有空白 · 也能识别")
    void xml声明前有空白_容错() {
        String xml = "  \r\n<?xml version=\"1.0\"?><R><a>1</a></R>";
        EmbeddedChannel ch = newDecoderChannel(UTF8);
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(xml.getBytes(UTF8)));

        byte[] frame = ch.readInbound();
        assertNotNull(frame);
        // 输出会保留前置空白(切帧从 readerIndex 开始)· 与原字节一致
        assertEquals(xml, new String(frame, UTF8));
    }

    // ============ GBK 编码 ============

    @Test
    @DisplayName("GBK 编码 · 含中文根标签 · 正确切帧")
    void GBK编码_中文报文_切帧() {
        String xml = "<?xml version=\"1.0\" encoding=\"GBK\"?><交易><功能号>181345</功能号></交易>";
        EmbeddedChannel ch = newDecoderChannel(GBK);
        ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(xml.getBytes(GBK)));

        byte[] frame = ch.readInbound();
        assertNotNull(frame);
        assertEquals(xml, new String(frame, GBK));
    }

    // ============ 编码器(出站)============

    @Test
    @DisplayName("编码器透传 payload · 不加边界字节")
    void 编码器透传_不加边界() {
        String xml = "<Req>hello</Req>";
        EmbeddedChannel ch = new EmbeddedChannel(XmlBoundaryCodec.encoder());
        ch.writeOutbound(xml.getBytes(UTF8));

        io.netty.buffer.ByteBuf out = ch.readOutbound();
        assertNotNull(out);
        byte[] bytes = new byte[out.readableBytes()];
        out.readBytes(bytes);
        assertArrayEquals(xml.getBytes(UTF8), bytes, "编码器不应加任何前后缀");
        out.release();
    }
}
