package com.powergateway;

import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("M2-1 AesUtil 加解密工具")
class M21AesUtilTest {

    private static final String TEST_KEY = "PowerGateway128K"; // 16字节

    @Test
    @DisplayName("加密后解密还原原文")
    void 加密后解密还原() {
        String original = "mySecretPassword";
        String encrypted = AesUtil.encrypt(original, TEST_KEY);
        assertNotEquals(original, encrypted);
        assertEquals(original, AesUtil.decrypt(encrypted, TEST_KEY));
    }

    @Test
    @DisplayName("中文和特殊字符_加密解密正确")
    void 中文和特殊字符_加密解密正确() {
        String original = "密码@123!测试";
        assertEquals(original, AesUtil.decrypt(AesUtil.encrypt(original, TEST_KEY), TEST_KEY));
    }

    @Test
    @DisplayName("长密码_加密解密正确")
    void 长密码_加密解密正确() {
        String original = "ThisIsAVeryLongPassword1234567890!@#$%^&*()";
        assertEquals(original, AesUtil.decrypt(AesUtil.encrypt(original, TEST_KEY), TEST_KEY));
    }

    @Test
    @DisplayName("加密结果是合法 Base64")
    void 加密结果是Base64字符串() {
        String encrypted = AesUtil.encrypt("test123", TEST_KEY);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
    }
}
