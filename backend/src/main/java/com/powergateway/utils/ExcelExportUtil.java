package com.powergateway.utils;

import com.powergateway.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Function;

/** FN-07 / FN-10 通用 Excel 导出工具（POI 5.2.3，XSSFWorkbook 内存模式）。 */
public final class ExcelExportUtil {

    private ExcelExportUtil() {}

    public static final class Column<T> {
        public final String header;
        public final Function<T, Object> valueGetter;
        public Column(String header, Function<T, Object> getter) {
            this.header = header; this.valueGetter = getter;
        }
    }

    /** 单 Sheet 导出。 */
    public static <T> byte[] export(String sheetName, List<Column<T>> columns, List<T> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeSheet(wb, sheetName, columns, rows);
            wb.write(out);
            return out.toByteArray();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 导出失败: " + e.getMessage());
        }
    }

    /** 多 Sheet 导出（供 FN-07 请求/响应字段双 Sheet）。 */
    public static byte[] exportMultiSheet(LinkedHashMap<String, SheetSpec<?>> sheets) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (Map.Entry<String, SheetSpec<?>> e : sheets.entrySet()) {
                writeSheetRaw(wb, e.getKey(), e.getValue());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(500, "Excel 多 Sheet 导出失败: " + e.getMessage());
        }
    }

    public static final class SheetSpec<T> {
        public final List<Column<T>> columns;
        public final List<T> rows;
        public SheetSpec(List<Column<T>> c, List<T> r) { this.columns = c; this.rows = r; }
    }

    private static <T> void writeSheet(XSSFWorkbook wb, String name,
                                       List<Column<T>> columns, List<T> rows) {
        Sheet sheet = wb.createSheet(safeSheetName(name));
        CellStyle headerStyle = createHeaderStyle(wb);
        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell c = header.createCell(i);
            c.setCellValue(columns.get(i).header);
            c.setCellStyle(headerStyle);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = sheet.createRow(r + 1);
            T item = rows.get(r);
            for (int c = 0; c < columns.size(); c++) {
                Object v = columns.get(c).valueGetter.apply(item);
                writeCell(row.createCell(c), v);
            }
        }
        for (int i = 0; i < columns.size(); i++) sheet.autoSizeColumn(i);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void writeSheetRaw(XSSFWorkbook wb, String name, SheetSpec spec) {
        writeSheet(wb, name, spec.columns, spec.rows);
    }

    private static void writeCell(Cell cell, Object v) {
        if (v == null) { cell.setCellValue(""); return; }
        if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
        else if (v instanceof Boolean) cell.setCellValue((Boolean) v);
        else cell.setCellValue(v.toString());
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    /** Excel sheet 名不能含 / \ ? * [ ] :，长度 ≤ 31。 */
    private static String safeSheetName(String raw) {
        if (raw == null || raw.isEmpty()) return "Sheet";
        String s = raw.replaceAll("[/\\\\?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
