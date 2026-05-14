package com.powergateway.utils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * H2 测试环境专用：模拟 MySQL DATE_FORMAT 函数。
 * 通过 CREATE ALIAS DATE_FORMAT FOR "..." 注册到 H2，
 * 仅在 test profile 的 H2 内存库中使用，不参与生产代码逻辑。
 */
public class H2DateFormatAlias {

    /**
     * H2 ALIAS 方法签名必须为 public static。
     * 将 MySQL 格式串（如 '%H:00'、'%Y-%m-%d'）转为 Java DateTimeFormatter 格式。
     */
    public static String format(Timestamp ts, String pattern) {
        if (ts == null || pattern == null) return null;
        LocalDateTime ldt = ts.toLocalDateTime();
        // 将 MySQL 格式串转为 Java DateTimeFormatter 格式串
        String javaPattern = pattern
                .replace("%Y", "yyyy")
                .replace("%m", "MM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%i", "mm")
                .replace("%s", "ss");
        return ldt.format(DateTimeFormatter.ofPattern(javaPattern));
    }
}
