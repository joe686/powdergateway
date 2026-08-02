package com.powergateway;

import com.powergateway.socket.codec.FramingType;
import com.powergateway.socket.codec.LengthPrefixCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-1 · LengthPrefixCodec 4/8 字节双宽度单元测试(v0.3.0 · Task 3)。
 */
@ActiveProfiles("test")
class SOCK1LengthPrefixCodecTest {

    // ============ 4 字节大端 ============

    @Test
    @DisplayName("4 字节头 · 单帧 encode + decode 回环")
    void be4_单帧回环() {
        String payload = "<Req>hello</Req>";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        EmbeddedChannel enc = new EmbeddedChannel(LengthPrefixCodec.encoder(FramingType.LENGTH_PREFIX_BE4));
        enc.writeOutbound(payloadBytes);
        ByteBuf onWire = enc.readOutbound();
        assertNotNull(onWire);
        assertEquals(4 + payloadBytes.length, onWire.readableBytes(), "长度头 4 字节 + payload");
        // 前 4 字节大端 == payload.length
        assertEquals(payloadBytes.length, onWire.getInt(0));

        EmbeddedChannel dec = new EmbeddedChannel(LengthPrefixCodec.decoder(FramingType.LENGTH_PREFIX_BE4));
        dec.writeInbound(onWire);
        byte[] decoded = dec.readInbound();
        assertArrayEquals(payloadBytes, decoded);
    }

    @Test
    @DisplayName("4 字节头 · 长度字段值实证 = payload.length(大端)")
    void be4_长度字段值实证大端() {
        byte[] payload = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        EmbeddedChannel enc = new EmbeddedChannel(LengthPrefixCodec.encoder(FramingType.LENGTH_PREFIX_BE4));
        enc.writeOutbound(payload);
        ByteBuf onWire = enc.readOutbound();
        byte[] bytes = new byte[onWire.readableBytes()];
        onWire.readBytes(bytes);
        onWire.release();
        // 大端 4 字节 = 00 00 00 05
        assertArrayEquals(new byte[]{0, 0, 0, 5, 1, 2, 3, 4, 5}, bytes);
    }

    // ============ 8 字节大端(用户 2026-08-02 实证 "报文前 8 位报文长度")============

    @Test
    @DisplayName("8 字节头 · 单帧 encode + decode 回环 · Q5=C 用户实证")
    void be8_单帧回环() {
        String payload = "<Transaction><FunctionId>181345</FunctionId></Transaction>";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        EmbeddedChannel enc = new EmbeddedChannel(LengthPrefixCodec.encoder(FramingType.LENGTH_PREFIX_BE8));
        enc.writeOutbound(payloadBytes);
        ByteBuf onWire = enc.readOutbound();
        assertEquals(8 + payloadBytes.length, onWire.readableBytes(), "长度头 8 字节 + payload");
        assertEquals(payloadBytes.length, onWire.getLong(0));

        EmbeddedChannel dec = new EmbeddedChannel(LengthPrefixCodec.decoder(FramingType.LENGTH_PREFIX_BE8));
        dec.writeInbound(onWire);
        byte[] decoded = dec.readInbound();
        assertArrayEquals(payloadBytes, decoded);
    }

    @Test
    @DisplayName("8 字节头 · 长度字段值实证 = payload.length(大端 · 高位全 0)")
    void be8_长度字段值实证大端() {
        byte[] payload = new byte[]{0x0A, 0x0B};
        EmbeddedChannel enc = new EmbeddedChannel(LengthPrefixCodec.encoder(FramingType.LENGTH_PREFIX_BE8));
        enc.writeOutbound(payload);
        ByteBuf onWire = enc.readOutbound();
        byte[] bytes = new byte[onWire.readableBytes()];
        onWire.readBytes(bytes);
        onWire.release();
        // 大端 8 字节 = 00 00 00 00 00 00 00 02 + payload
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 2, 0x0A, 0x0B}, bytes);
    }

    // ============ 多帧连续 ============

    @Test
    @DisplayName("多帧连续 · 4 字节头 · 逐帧解码")
    void be4_多帧连续() {
        byte[] p1 = "<R1/>".getBytes(StandardCharsets.UTF_8);
        byte[] p2 = "<R2/>".getBytes(StandardCharsets.UTF_8);
        byte[] p3 = "<Ack>ok</Ack>".getBytes(StandardCharsets.UTF_8);

        // 手工拼接:len4+p1 | len4+p2 | len4+p3
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(p1.length); buf.writeBytes(p1);
        buf.writeInt(p2.length); buf.writeBytes(p2);
        buf.writeInt(p3.length); buf.writeBytes(p3);

        EmbeddedChannel dec = new EmbeddedChannel(LengthPrefixCodec.decoder(FramingType.LENGTH_PREFIX_BE4));
        dec.writeInbound(buf);
        assertArrayEquals(p1, dec.readInbound());
        assertArrayEquals(p2, dec.readInbound());
        assertArrayEquals(p3, dec.readInbound());
        assertNull(dec.readInbound());
    }

    // ============ 分片 ============

    @Test
    @DisplayName("分片写入 · 长度头/payload 都跨包 · 累积到完整才输出")
    void 分片_累积完整() {
        byte[] payload = "<Req><Body>abcdefghij</Body></Req>".getBytes(StandardCharsets.UTF_8);
        ByteBuf full = Unpooled.buffer();
        full.writeInt(payload.length);
        full.writeBytes(payload);
        byte[] full2 = new byte[full.readableBytes()];
        full.readBytes(full2);
        full.release();

        EmbeddedChannel dec = new EmbeddedChannel(LengthPrefixCodec.decoder(FramingType.LENGTH_PREFIX_BE4));
        // 拆到长度头中间
        dec.writeInbound(Unpooled.wrappedBuffer(full2, 0, 2));
        assertNull(dec.readInbound());
        // 补齐长度头 + payload 前半
        dec.writeInbound(Unpooled.wrappedBuffer(full2, 2, 10));
        assertNull(dec.readInbound());
        // 剩余
        dec.writeInbound(Unpooled.wrappedBuffer(full2, 12, full2.length - 12));
        byte[] out = dec.readInbound();
        assertArrayEquals(payload, out);
    }

    // ============ 超大 payload / 非法值 ============

    @Test
    @DisplayName("超过 maxFrameLength · 抛 TooLongFrameException")
    void 超过上限_抛异常() {
        int maxLen = 100;
        EmbeddedChannel dec = new EmbeddedChannel(LengthPrefixCodec.decoder(FramingType.LENGTH_PREFIX_BE4, maxLen));
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(200);  // 声明 200 · 超过 100 上限
        buf.writeBytes(new byte[10]);
        assertThrows(TooLongFrameException.class, () -> dec.writeInbound(buf));
    }

    @Test
    @DisplayName("长度头为负 · 抛异常(拒绝畸形帧)")
    void 长度头为负_抛异常() {
        EmbeddedChannel dec = new EmbeddedChannel(LengthPrefixCodec.decoder(FramingType.LENGTH_PREFIX_BE8));
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(-1L);
        buf.writeBytes(new byte[1]);
        assertThrows(Exception.class, () -> dec.writeInbound(buf));
    }

    // ============ 参数校验 ============

    @Test
    @DisplayName("构造器拒绝非 4/8 lengthFieldLength")
    void 构造器_非4非8_抛异常() {
        assertThrows(IllegalArgumentException.class, () -> new LengthPrefixCodec.LengthPrefixDecoder(2));
        assertThrows(IllegalArgumentException.class, () -> new LengthPrefixCodec.LengthPrefixDecoder(16));
        assertThrows(IllegalArgumentException.class, () -> new LengthPrefixCodec.LengthPrefixEncoder(1));
    }

    @Test
    @DisplayName("factory decoder/encoder 拒绝 XML_BOUNDARY framing")
    void factory_拒绝XmlBoundary() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LengthPrefixCodec.decoder(FramingType.XML_BOUNDARY)
        );
        assertTrue(ex.getMessage().contains("LENGTH_PREFIX"));
    }
}
