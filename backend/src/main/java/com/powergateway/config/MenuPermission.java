package com.powergateway.config;

import java.util.Arrays;
import java.util.List;

/**
 * 角色菜单权限白名单（SYS-3）。
 * 权限在此处硬编码，sys_config 开关可在运行时叠加覆盖。
 */
public class MenuPermission {

    private MenuPermission() {}

    public static final List<String> ADMIN_MENUS = Arrays.asList(
        "/dashboard",
        "/convert/format", "/convert/field-mapping", "/convert/field-process",
        "/convert/channel", "/convert/port-route", "/convert/template",
        "/interface/db", "/interface/table", "/interface/dev",
        "/interface/insert", "/interface/update", "/interface/delete",
        "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
        "/system/log", "/system/stats", "/system/user", "/system/config",
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> USER_MENUS = Arrays.asList(
        "/dashboard",
        "/convert/format", "/convert/field-mapping", "/convert/field-process",
        "/convert/channel", "/convert/port-route", "/convert/template",
        "/interface/db", "/interface/table", "/interface/dev",
        "/interface/insert", "/interface/update",
        "/interface/list", "/interface/formula", "/interface/cache",
        "/system/log", "/system/stats",
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> READONLY_MENUS = Arrays.asList(
        "/dashboard",
        "/interface/list", "/interface/cache",
        "/tools/debug", "/tools/swagger"
    );

    /** sys_config 日志菜单开关的 key */
    public static final String LOG_MENU_CONFIG_KEY = "log_menu_enabled";
    /** 受开关控制的菜单路由 */
    public static final String LOG_MENU_PATH = "/system/log";
}
