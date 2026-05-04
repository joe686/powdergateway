// backend/src/test/java/com/powergateway/CharsetConverterTest.java
package com.powergateway;

import com.powergateway.utils.CharsetConverter;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class CharsetConverterTest {

    @Test
    void encodeToBytes_utf8_returnsUtf8Bytes() {
        byte[] bytes = CharsetConverter.encodeToBytes("hello", "UTF-8");
        assertArrayEquals("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8), bytes);
    }

    @Test
    void encodeToBytes_nullCharset_defaultsToUtf8() {
        byte[] bytes = CharsetConverter.encodeToBytes("hello", null);
        assertArrayEquals("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8), bytes);
    }

    @Test
    void decodeFromBytes_utf8_returnsOriginalString() {
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("hello", CharsetConverter.decodeFromBytes(bytes, "UTF-8"));
    }

    @Test
    void decodeFromBytes_nullBytes_returnsEmpty() {
        assertEquals("", CharsetConverter.decodeFromBytes(null, "UTF-8"));
    }

    @Test
    void roundTrip_gbk_encodeDecodePreservesAscii() {
        // ASCII 字符在 UTF-8 和 GBK 中字节相同，往返无损
        String original = "Hello PowerGateway 123";
        byte[] encoded = CharsetConverter.encodeToBytes(original, "GBK");
        String decoded = CharsetConverter.decodeFromBytes(encoded, "GBK");
        assertEquals(original, decoded);
    }

    @Test
    void isEffectivelyUtf8_nullOrEmpty_returnsTrue() {
        assertTrue(CharsetConverter.isEffectivelyUtf8(null));
        assertTrue(CharsetConverter.isEffectivelyUtf8(""));
        assertTrue(CharsetConverter.isEffectivelyUtf8("UTF-8"));
        assertTrue(CharsetConverter.isEffectivelyUtf8("utf-8"));
    }

    @Test
    void isEffectivelyUtf8_gbk_returnsFalse() {
        assertFalse(CharsetConverter.isEffectivelyUtf8("GBK"));
        assertFalse(CharsetConverter.isEffectivelyUtf8("ISO-8859-1"));
    }

    @Test
    void encodeToBytes_unsupportedCharset_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> CharsetConverter.encodeToBytes("hello", "INVALID-CHARSET-XYZ"));
    }
}
