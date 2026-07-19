package com.powergateway;

import com.powergateway.config.MenuPermission;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UX-D Task 5：断言 /convert/wizard 在 ADMIN/USER 白名单，且不在 READONLY（防 CHG-011 E2E-5 重演）
 */
@ActiveProfiles("test")
class UXDMenuPermissionTest {

    @Test
    void ADMIN_MENUS_包含转换向导路径() {
        assertTrue(MenuPermission.ADMIN_MENUS.contains("/convert/wizard"),
            "ADMIN_MENUS 必须包含 /convert/wizard");
    }

    @Test
    void USER_MENUS_包含转换向导路径() {
        assertTrue(MenuPermission.USER_MENUS.contains("/convert/wizard"),
            "USER_MENUS 必须包含 /convert/wizard");
    }

    @Test
    void READONLY_MENUS_不包含转换向导路径() {
        assertFalse(MenuPermission.READONLY_MENUS.contains("/convert/wizard"),
            "READONLY_MENUS 不得包含 /convert/wizard");
    }
}
