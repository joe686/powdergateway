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
        "/interface/doc", "/interface/import-export",
        // 系统管理
        "/system/log", "/system/stats", "/system/user", "/system/config",
        // 辅助工具（REG-1 追加 /tools/registry；FN-11 追加 /tools/docs 未来 Task 5/6 时启用）
        "/tools/debug", "/tools/swagger", "/tools/registry"
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
        "/interface/doc", "/interface/import-export",
        // 系统管理（user 不含 user / config）
        "/system/log", "/system/stats",
        // 辅助工具（CHG-026：/tools/swagger 收归 admin 独有，此处移除）
        "/tools/debug"
    );

    public static final List<String> READONLY_MENUS = Arrays.asList(
        "/dashboard",
        "/interface/list", "/interface/cache",
        // CHG-026：/tools/swagger 收归 admin 独有，此处移除
        "/tools/debug"
    );

    /**
     * TEST-1 · TESTER 角色专属菜单（独立于 ADMIN/USER/READONLY）
     * 仅内含测试模块相关路由，生产环境不预置 tester 用户则完全不可达
     */
    public static final List<String> TESTER_MENUS = Arrays.asList(
        "/dashboard",
        "/testkit/demo-db", "/testkit/mock-rules", "/testkit/mock-history",
        "/tools/debug"
    );

    /** sys_config 日志菜单开关的 key */
    public static final String LOG_MENU_CONFIG_KEY = "log_menu_enabled";
    /** 受开关控制的菜单路由 */
    public static final String LOG_MENU_PATH = "/system/log";
}
