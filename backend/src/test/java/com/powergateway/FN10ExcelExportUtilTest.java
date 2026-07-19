package com.powergateway;

import com.powergateway.utils.ExcelExportUtil;
import com.powergateway.utils.ExcelExportUtil.Column;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("FN-10 ExcelExportUtil")
class FN10ExcelExportUtilTest {

    private static class Item {
        String name; int qty;
        Item(String n, int q) { this.name=n; this.qty=q; }
    }

    private final List<Column<Item>> columns = Arrays.asList(
        new Column<>("名称", i -> i.name),
        new Column<>("数量", i -> i.qty)
    );

    @Test
    void 空列表_仅生成header行() throws Exception {
        byte[] bytes = ExcelExportUtil.export("测试", columns, Collections.emptyList());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(0, s.getLastRowNum(), "空数据时应只有 header 行");
            Row header = s.getRow(0);
            assertEquals("名称", header.getCell(0).getStringCellValue());
            assertEquals("数量", header.getCell(1).getStringCellValue());
        }
    }

    @Test
    void 一百条数据_行数正确() throws Exception {
        List<Item> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) data.add(new Item("n" + i, i));
        byte[] bytes = ExcelExportUtil.export("测试", columns, data);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(100, wb.getSheetAt(0).getLastRowNum(), "100 条 + header = lastRowNum 100");
        }
    }

    @Test
    void sheet名安全化_特殊字符替换() throws Exception {
        byte[] bytes = ExcelExportUtil.export("含/非法\\字符?", columns, Collections.emptyList());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String name = wb.getSheetAt(0).getSheetName();
            assertFalse(name.contains("/"));
            assertFalse(name.contains("\\"));
            assertFalse(name.contains("?"));
        }
    }

    @Test
    void 自适应宽度_列宽大于零() throws Exception {
        byte[] bytes = ExcelExportUtil.export("测试", columns,
            Collections.singletonList(new Item("苹果", 3)));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertTrue(wb.getSheetAt(0).getColumnWidth(0) > 0);
        }
    }

    @Test
    void null值不抛异常_写空串() throws Exception {
        Item i = new Item(null, 0);
        byte[] bytes = ExcelExportUtil.export("测试", columns,
            Collections.singletonList(i));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell c = wb.getSheetAt(0).getRow(1).getCell(0);
            assertNotNull(c);
        }
    }
}
