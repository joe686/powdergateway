package com.powergateway.utils;

import com.powergateway.model.dto.DictMappingSaveRequest;
import com.powergateway.model.dto.DictMappingVO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FN-12 字典映射 Excel 读写工具（Apache POI 5.2.3）
 * 列顺序：system_code | dict_key | direction | source_value | target_value | cn_label | status
 * direction 支持数字 1/2、中文 出向/入向、英文 OUT/IN（大小写均可）
 */
public class DictMappingExcelHelper {

    private static final String[] HEADERS = {
        "系统代号 system_code", "字典标识 dict_key", "方向 direction",
        "源值 source_value", "目标值 target_value", "中文含义 cn_label", "状态 status"
    };

    /**
     * 解析 Excel 输入流，返回逐行 SaveRequest。
     * 表头行（行0）跳过；空行跳过；direction 非法时抛 IllegalArgumentException。
     */
    public static List<DictMappingSaveRequest> parse(InputStream in) throws IOException {
        List<DictMappingSaveRequest> list = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) continue;
                list.add(parseRow(row));
            }
        }
        return list;
    }

    /**
     * 解析单行为 SaveRequest（direction 非法时抛 IllegalArgumentException）。
     * 供 DictMappingService.importExcel 逐行调用，以实现 per-row 错误捕获。
     */
    public static DictMappingSaveRequest parseRow(Row row) {
        DictMappingSaveRequest req = new DictMappingSaveRequest();
        req.setSystemCode(cellStr(row, 0));
        req.setDictKey(cellStr(row, 1));
        req.setDirection(parseDirection(cellStr(row, 2)));
        req.setSourceValue(cellStr(row, 3));
        req.setTargetValue(cellStr(row, 4));
        req.setCnLabel(cellStr(row, 5));
        String st = cellStr(row, 6);
        req.setStatus(st.isEmpty() ? 1 : Integer.parseInt(st));
        req.setBidirectional(false);
        return req;
    }

    /**
     * 将字典映射列表输出为 .xlsx 字节流。
     */
    public static byte[] build(List<DictMappingVO> data) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("字典映射");
            // 表头行
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            // 数据行
            for (int i = 0; i < data.size(); i++) {
                DictMappingVO v = data.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(nz(v.getSystemCode()));
                row.createCell(1).setCellValue(nz(v.getDictKey()));
                row.createCell(2).setCellValue(v.getDirection() == null ? "" : v.getDirection().toString());
                row.createCell(3).setCellValue(nz(v.getSourceValue()));
                row.createCell(4).setCellValue(nz(v.getTargetValue()));
                row.createCell(5).setCellValue(nz(v.getCnLabel()));
                row.createCell(6).setCellValue(v.getStatus() == null ? "1" : v.getStatus().toString());
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ──────────────── 私有辅助方法 ────────────────

    /** 读取单元格字符串值（强制转 STRING 类型），null 或空返回 "" */
    private static String cellStr(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return "";
        c.setCellType(CellType.STRING);
        String s = c.getStringCellValue();
        return s == null ? "" : s.trim();
    }

    /**
     * direction 字段解析：支持 "1"/"出向"/"OUT" → 1，"2"/"入向"/"IN" → 2。
     * 不匹配则抛 IllegalArgumentException。
     */
    private static int parseDirection(String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("direction 不能为空");
        }
        if ("1".equals(s) || "出向".equals(s) || "OUT".equalsIgnoreCase(s)) return 1;
        if ("2".equals(s) || "入向".equals(s) || "IN".equalsIgnoreCase(s))  return 2;
        throw new IllegalArgumentException(
            "direction 必须为 1/2 或 出向/入向 或 OUT/IN，实际值=" + s);
    }

    /** 判断是否为空行（前5列均为空） */
    public static boolean isBlankRow(Row row) {
        for (int i = 0; i < 5; i++) {
            if (!cellStr(row, i).isEmpty()) return false;
        }
        return true;
    }

    /** null 安全空字符串 */
    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
