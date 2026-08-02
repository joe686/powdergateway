package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.socket.codec.CharsetSupport;
import com.powergateway.socket.codec.FramingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SOCK-1 · SocketClient 骨架 + 支撑类单元测试(v0.3.0 · Task 1)。
 *
 * <p>Red 阶段:SocketClient.send 尚未实装 · 抛 UnsupportedOperationException。</p>
 * <p>FramingType 三值合法 · CharsetSupport 白名单 UTF-8/GBK。</p>
 */
@ActiveProfiles("test")
class SOCK1SocketClientTest {

    // ============ SocketClient checkConnectionMode 契约 ============
    // (send 完整语义走 SOCK1SocketClientIntegrationTest · Task 4 已实装)

    // ============ FramingType 枚举 ============

    @Test
    @DisplayName("FramingType 三值合法 · Q5=C 全支持")
    void framingType_三值全支持() {
        assertEquals(FramingType.XML_BOUNDARY, FramingType.parse("xml_boundary"));
        assertEquals(FramingType.LENGTH_PREFIX_BE4, FramingType.parse("length_prefix_be4"));
        assertEquals(FramingType.LENGTH_PREFIX_BE8, FramingType.parse("length_prefix_be8"));
    }

    @Test
    @DisplayName("FramingType.parse 大小写不敏感")
    void framingType_大小写不敏感() {
        assertEquals(FramingType.XML_BOUNDARY, FramingType.parse("XML_BOUNDARY"));
        assertEquals(FramingType.LENGTH_PREFIX_BE4, FramingType.parse("Length_Prefix_BE4"));
    }

    @Test
    @DisplayName("FramingType.parse null 抛 IllegalArgumentException")
    void framingType_null_抛异常() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FramingType.parse(null)
        );
        assertTrue(ex.getMessage().contains("必填"), "错误消息应说明必填");
    }

    @Test
    @DisplayName("FramingType.parse 非法值抛 IllegalArgumentException · 消息附白名单")
    void framingType_非法值_抛异常() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FramingType.parse("unknown_framing")
        );
        assertTrue(ex.getMessage().contains("xml_boundary"), "错误消息应列出合法值");
        assertTrue(ex.getMessage().contains("unknown_framing"), "错误消息应回显收到的值");
    }

    // ============ CharsetSupport 白名单 ============

    @Test
    @DisplayName("CharsetSupport.of 白名单接受 UTF-8 / GBK · Q6=B 双编码")
    void charsetSupport_白名单() {
        Charset utf8 = CharsetSupport.of("UTF-8");
        Charset gbk = CharsetSupport.of("GBK");
        assertNotNull(utf8);
        assertNotNull(gbk);
        assertEquals("UTF-8", utf8.name());
        assertEquals("GBK", gbk.name());
    }

    @Test
    @DisplayName("CharsetSupport.of 大小写不敏感")
    void charsetSupport_大小写不敏感() {
        assertNotNull(CharsetSupport.of("utf-8"));
        assertNotNull(CharsetSupport.of("gbk"));
    }

    @Test
    @DisplayName("CharsetSupport.of 白名单外抛 BusinessException")
    void charsetSupport_白名单外_抛异常() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> CharsetSupport.of("ISO-8859-1")
        );
        assertTrue(ex.getMessage().contains("UTF-8"), "错误消息应列出白名单");
        assertTrue(ex.getMessage().contains("ISO-8859-1"), "错误消息应回显收到的值");
    }

    @Test
    @DisplayName("CharsetSupport.of null 抛 BusinessException")
    void charsetSupport_null_抛异常() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> CharsetSupport.of(null)
        );
        assertTrue(ex.getMessage().contains("必填"));
    }

    @Test
    @DisplayName("CharsetSupport.isSupported 判定")
    void charsetSupport_isSupported() {
        assertTrue(CharsetSupport.isSupported("UTF-8"));
        assertTrue(CharsetSupport.isSupported("GBK"));
        assertTrue(CharsetSupport.isSupported("utf-8"));
        assertFalse(CharsetSupport.isSupported("ISO-8859-1"));
        assertFalse(CharsetSupport.isSupported(null));
    }
}
