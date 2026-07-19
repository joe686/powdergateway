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
        // 接口转换配置 · 向导（UX-D）
        "/convert/wizard",
        // 接口转换配置 · 基础配置
        "/convert/template", "/convert/channel",
        // 接口转换配置 · 转换规则
        "/convert/field-mapping", "/convert/field-process",
        // 接口转换配置 · 发布测试
        "/convert/port-route", "/convert/format",
        // 可视化接口开发
        "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
        "/interface/insert", "/interface/update", "/interface/delete",
        "/interface/list", "/interface/shard", "/interface/formula", "/interface/cache",
        "/interface/doc",
        // 系统管理
        "/system/log", "/system/stats", "/system/user", "/system/config",
        // 辅助工具
        "/tools/debug", "/tools/swagger"
    );

    public static final List<String> USER_MENUS = Arrays.asList(
        "/dashboard",
        // 接口转换配置 · 向导（UX-D）
        "/convert/wizard",
        // 接口转换配置 · 基础配置
        "/convert/template", "/convert/channel",
        // 接口转换配置 · 转换规则
        "/convert/field-mapping", "/convert/field-process",
        // 接口转换配置 · 发布测试
        "/convert/port-route", "/convert/format",
        // 可视化接口开发（user 不含 delete / shard）
        "/interface/db", "/interface/table", "/interface/wizard", "/interface/dev",
        "/interface/insert", "/interface/update",
        "/interface/list", "/interface/formula", "/interface/cache",
        "/interface/doc",
        // 系统管理（user 不含 user / config）
        "/system/log", "/system/stats",
        // 辅助工具
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
