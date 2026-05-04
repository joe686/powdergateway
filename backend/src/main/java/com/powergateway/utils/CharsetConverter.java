// backend/src/main/java/com/powergateway/utils/CharsetConverter.java
package com.powergateway.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 字节级字符集转换工具（CHG-002）。
 * 所有方法均为静态方法，无状态，可直接调用。
 */
public class CharsetConverter {

    private CharsetConverter() {}

    /**
     * 将 String 编码为目标字符集的字节数组。
     * charset 为 null 或空时默认 UTF-8。
     */
    public static byte[] encodeToBytes(String text, String charset) {
        if (text == null) return new byte[0];
        return text.getBytes(resolveCharset(charset));
    }

    /**
     * 将字节数组按指定字符集解码为 String。
     * bytes 为 null 时返回空字符串。charset 为 null 或空时默认 UTF-8。
     */
    public static String decodeFromBytes(byte[] bytes, String charset) {
        if (bytes == null || bytes.length == 0) return "";
        return new String(bytes, resolveCharset(charset));
    }

    /**
     * 判断 charset 是否等价于 UTF-8（含 null/空，视为 UTF-8）。
     */
    public static boolean isEffectivelyUtf8(String charset) {
        if (charset == null || charset.trim().isEmpty()) return true;
        try {
            return Charset.forName(charset).equals(StandardCharsets.UTF_8);
        } catch (UnsupportedCharsetException e) {
            return false;
        }
    }

    private static Charset resolveCharset(String charset) {
        if (charset == null || charset.trim().isEmpty()) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(charset);
        } catch (UnsupportedCharsetException e) {
            throw new IllegalArgumentException("不支持的字符集：" + charset, e);
        }
    }
}
