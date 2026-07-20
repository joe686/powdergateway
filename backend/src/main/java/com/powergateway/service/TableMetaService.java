package com.powergateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.DbConnection;
import com.powergateway.model.dto.ColumnMeta;
import com.powergateway.model.dto.TableMeta;
import com.powergateway.utils.AesUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TableMetaService {

    private static final String CACHE_PREFIX = "table_meta:";
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired
    private DbConnectionMapper dbConnectionMapper;

    @Autowired
    private AesUtil aesUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectProvider<StringRedisTemplate> redisProvider;

    // ─── 公开方法 ───────────────────────────────────

    /**
     * 查询指定连接的表结构（优先读缓存）。
     */
    public List<TableMeta> getTables(Long dbId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        String cacheKey = CACHE_PREFIX + dbId;

        if (redis != null) {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<List<TableMeta>>() {});
                } catch (Exception e) {
                    log.warn("[M2-2] 缓存解析失败，重新查询: {}", e.getMessage());
                }
            }
        }

        List<TableMeta> tables = queryFromJdbc(dbId);

        if (redis != null) {
            try {
                redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(tables),
                        CACHE_TTL_HOURS, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("[M2-2] 写入缓存失败: {}", e.getMessage());
            }
        }

        return tables;
    }

    /**
     * 清除缓存并重新从数据库查询。
     */
    public List<TableMeta> refreshCache(Long dbId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            redis.delete(CACHE_PREFIX + dbId);
        }
        return getTables(dbId);
    }

    /**
     * 导出表结构为 Excel（每张表一个 Sheet）。
     */
    public byte[] exportExcel(Long dbId) {
        List<TableMeta> tables = getTables(dbId);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            for (TableMeta table : tables) {
                String sheetName = sanitizeSheetName(table.getTableName());
                XSSFSheet sheet = wb.createSheet(sheetName);

                // 表头行
                XSSFRow header = sheet.createRow(0);
                String[] cols = {"列名", "类型", "主键", "可空", "备注"};
                for (int i = 0; i < cols.length; i++) {
                    XSSFCell cell = header.createCell(i);
                    cell.setCellValue(cols[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, 5000);
                }

                // 数据行
                int rowIdx = 1;
                for (ColumnMeta col : table.getColumns()) {
                    XSSFRow row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(col.getName());
                    row.createCell(1).setCellValue(col.getType());
                    row.createCell(2).setCellValue(col.isPrimary() ? "是" : "否");
                    row.createCell(3).setCellValue(col.isNullable() ? "是" : "否");
                    row.createCell(4).setCellValue(col.getRemarks() != null ? col.getRemarks() : "");
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 导出失败: " + e.getMessage());
        }
    }

    // ─── 私有方法 ───────────────────────────────────

    private List<TableMeta> queryFromJdbc(Long dbId) {
        DbConnection conn = dbConnectionMapper.selectById(dbId);
        if (conn == null) throw new BusinessException(404, "数据库连接不存在");

        String password = aesUtil.decrypt(conn.getPassword());
        List<TableMeta> tables = new ArrayList<>();

        try (Connection jdbc = DriverManager.getConnection(normalizeUrlForMetaQuery(conn.getUrl()), conn.getUsername(), password)) {
            DatabaseMetaData meta = jdbc.getMetaData();
            String catalog = jdbc.getCatalog();

            // 查询所有用户表
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    // 过滤系统 Schema
                    if (isSystemSchema(schema)) continue;

                    TableMeta table = new TableMeta();
                    table.setTableName(rs.getString("TABLE_NAME"));
                    table.setComment(rs.getString("REMARKS"));
                    table.setColumns(new ArrayList<>());
                    tables.add(table);
                }
            }

            // 获取每张表的列信息和主键信息
            for (TableMeta table : tables) {
                Set<String> primaryKeys = getPrimaryKeys(meta, catalog, table.getTableName());
                try (ResultSet cs = meta.getColumns(catalog, null, table.getTableName(), "%")) {
                    while (cs.next()) {
                        ColumnMeta col = new ColumnMeta();
                        col.setName(cs.getString("COLUMN_NAME"));
                        col.setType(cs.getString("TYPE_NAME"));
                        col.setNullable(cs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                        col.setRemarks(cs.getString("REMARKS"));
                        col.setPrimary(primaryKeys.contains(cs.getString("COLUMN_NAME")));
                        table.getColumns().add(col);
                    }
                }
                // 标记唯一索引列
                Set<String> uniqueCols = getUniqueIndexColumns(meta, catalog, table.getTableName());
                for (ColumnMeta col : table.getColumns()) {
                    if (uniqueCols.contains(col.getName())) {
                        col.setUnique(true);
                    }
                }
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "查询表结构失败: " + e.getMessage());
        }

        return tables;
    }

    private Set<String> getPrimaryKeys(DatabaseMetaData meta, String catalog, String tableName) {
        Set<String> pks = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, null, tableName)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.warn("[M2-2] 获取主键信息失败: {}", e.getMessage());
        }
        return pks;
    }

    private Set<String> getUniqueIndexColumns(DatabaseMetaData meta, String catalog, String tableName) {
        Set<String> uniqueCols = new HashSet<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, null, tableName, true, false)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if (colName != null) {
                    uniqueCols.add(colName);
                }
            }
        } catch (Exception e) {
            log.warn("[M2-2] 获取唯一索引失败: {}", e.getMessage());
        }
        return uniqueCols;
    }

    /**
     * MySQL Connector/J 读 DatabaseMetaData.REMARKS 时，若 URL 缺少 useInformationSchema=true
     * 会回退到 SHOW FULL COLUMNS，其字符集协商不走 URL 的 characterEncoding，导致 utf8mb4 中
     * 文注释被按 latin1/GBK 解析后乱码（如"渠道编码"→"娓犻亾缂栫爜"）。
     * 此方法在不修改用户原始 URL 的前提下，临时为元数据查询会话补齐 utf-8 协议参数。
     *
     * Wave6 回归修复：这些参数仅对 MySQL 有意义；H2 (jdbc:h2:...) 会把 `?useInformation...`
     * 拼进 SET MODE 语句报语法错，Oracle / PostgreSQL 也不识别，故仅对 jdbc:mysql:// 生效。
     */
    private String normalizeUrlForMetaQuery(String url) {
        if (url == null || url.isEmpty()) return url;
        String lower = url.toLowerCase();
        if (!lower.startsWith("jdbc:mysql:") && !lower.startsWith("jdbc:mariadb:")) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        char sep = url.contains("?") ? '&' : '?';
        if (!url.contains("useInformationSchema=")) {
            sb.append(sep).append("useInformationSchema=true");
            sep = '&';
        }
        if (!url.contains("useUnicode=")) {
            sb.append(sep).append("useUnicode=true");
            sep = '&';
        }
        if (!url.contains("characterEncoding=")) {
            sb.append(sep).append("characterEncoding=utf8");
        }
        return sb.toString();
    }

    private boolean isSystemSchema(String schema) {
        if (schema == null) return false;
        String upper = schema.toUpperCase();
        return upper.equals("INFORMATION_SCHEMA")
                || upper.equals("PERFORMANCE_SCHEMA")
                || upper.equals("DEFINITION_SCHEMA")
                || upper.equals("MYSQL")
                || upper.equals("SYS");
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private String sanitizeSheetName(String name) {
        if (name == null) return "Sheet";
        // Excel sheet 名称限制 31 字符，不能含特殊字符
        String sanitized = name.replaceAll("[\\\\/:*?\\[\\]]", "_");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }
}
