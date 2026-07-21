package com.powergateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@DisplayName("UX-B 菜单白名单顺序与集合等价")
class UxBMenuPermissionOrderTest {

    @Test
    @DisplayName("ADMIN_MENUS: 接口转换段 模板 < 映射 < 报文格式转换")
    void adminMenus_接口转换段_模板在映射之前_报文格式在末尾() {
        int idxTemplate = MenuPermission.ADMIN_MENUS.indexOf("/convert/template");
        int idxMapping  = MenuPermission.ADMIN_MENUS.indexOf("/convert/field-mapping");
        int idxFormat   = MenuPermission.ADMIN_MENUS.indexOf("/convert/format");
        assertTrue(idxTemplate >= 0 && idxMapping >= 0 && idxFormat >= 0, "三条路径必须都存在");
        assertTrue(idxTemplate < idxMapping, "/convert/template 应在 /convert/field-mapping 之前");
        assertTrue(idxMapping < idxFormat, "/convert/field-mapping 应在 /convert/format 之前");
    }

    @Test
    @DisplayName("ADMIN_MENUS: 集合成员与 SYS-3 原白名单严格等价")
    void adminMenus_集合成员_与SYS3设计等价() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "/dashboard",
            "/convert/format", "/convert/field-mapping", "/convert/field-process",
            "/convert/channel", "/convert/port-route", "/convert/template",
            "/convert/wizard",                              // UX-D append
            "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
            "/interface/insert", "/interface/update", "/interface/delete",
            "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
            "/interface/doc", "/interface/import-export",   // UX-E append
            "/system/log", "/system/stats", "/system/user", "/system/config",
            "/tools/debug", "/tools/swagger",
            "/tools/registry"                                // REG-1 append
        ));
        assertEquals(expected, new HashSet<>(MenuPermission.ADMIN_MENUS));
        assertEquals(28, MenuPermission.ADMIN_MENUS.size(), "ADMIN 白名单元素个数 = 24 SYS-3 基线 + 3 UX-D/E + 1 REG-1");
    }

    @Test
    @DisplayName("USER_MENUS: 接口转换段 模板 < 映射 < 报文格式转换")
    void userMenus_接口转换段_模板在映射之前_报文格式在末尾() {
        int idxTemplate = MenuPermission.USER_MENUS.indexOf("/convert/template");
        int idxMapping  = MenuPermission.USER_MENUS.indexOf("/convert/field-mapping");
        int idxFormat   = MenuPermission.USER_MENUS.indexOf("/convert/format");
        assertTrue(idxTemplate >= 0 && idxMapping >= 0 && idxFormat >= 0);
        assertTrue(idxTemplate < idxMapping);
        assertTrue(idxMapping < idxFormat);
    }

    @Test
    @DisplayName("USER_MENUS: 集合成员与 SYS-3 原白名单严格等价")
    void userMenus_集合成员_与SYS3设计等价() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "/dashboard",
            "/convert/format", "/convert/field-mapping", "/convert/field-process",
            "/convert/channel", "/convert/port-route", "/convert/template",
            "/convert/wizard",                              // UX-D append
            "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
            "/interface/insert", "/interface/update",
            "/interface/list", "/interface/formula", "/interface/cache",
            "/interface/doc", "/interface/import-export",   // UX-E append
            "/system/log", "/system/stats",
            "/tools/debug"                                   // CHG-026: /tools/swagger 收归 admin 独有
        ));
        assertEquals(expected, new HashSet<>(MenuPermission.USER_MENUS));
    }

    @Test
    @DisplayName("READONLY_MENUS: 4 项集合（CHG-026 后移除 /tools/swagger）")
    void readonlyMenus_只含4项() {
        Set<String> expected = new HashSet<>(Arrays.asList(
            "/dashboard",
            "/interface/list", "/interface/cache",
            "/tools/debug"                                   // CHG-026: /tools/swagger 收归 admin 独有
        ));
        assertEquals(expected, new HashSet<>(MenuPermission.READONLY_MENUS));
        assertEquals(4, MenuPermission.READONLY_MENUS.size());
    }
}
