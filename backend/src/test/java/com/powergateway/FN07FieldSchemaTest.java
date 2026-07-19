package com.powergateway;

import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.service.InterfaceFieldSchemaService;
import com.powergateway.exception.BusinessException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FN-07 字段清单 Excel 导出")
class FN07FieldSchemaTest {

    @Autowired private InterfaceConfigService service;
    @Autowired private InterfaceFieldSchemaService schemaService;

    private Long createPublishedSelect() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("FN07_SELECT_" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[{\"name\":\"fn07_test\",\"alias\":\"t\"}]," +
            "\"fields\":[{\"table\":\"t\",\"column\":\"id\",\"alias\":\"id\"}]," +
            "\"conditions\":[],\"joins\":[]}");
        return service.save(req);
    }

    private Long createPublishedInsert() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("FN07_INSERT_" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("INSERT");
        req.setConfigJson("{\"tables\":[{\"name\":\"fn07_test\"," +
            "\"fields\":[{\"column\":\"name\",\"source\":\"REQUEST\",\"requestParam\":\"name\"}]}]}");
        return service.save(req);
    }

    @Test
    void SELECT接口导出_双Sheet结构正确() throws Exception {
        Long id = createPublishedSelect();
        InterfaceConfig cfg = service.getById(id);
        byte[] bytes = schemaService.exportExcel(cfg);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets(), "应有 2 个 Sheet");
            assertEquals("请求字段", wb.getSheetAt(0).getSheetName());
            assertEquals("响应字段", wb.getSheetAt(1).getSheetName());
        }
    }

    @Test
    void INSERT接口导出_响应Sheet固定为影响行数() throws Exception {
        Long id = createPublishedInsert();
        byte[] bytes = schemaService.exportExcel(service.getById(id));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet resp = wb.getSheetAt(1);
            assertEquals(1, resp.getLastRowNum(), "响应字段固定为 affectedRows，共 1 行数据");
            assertTrue(resp.getRow(1).getCell(1).getStringCellValue().contains("affected"),
                "字段名应包含 affected");
        }
    }

    @Test
    void 不存在的接口_抛BusinessException() {
        assertThrows(BusinessException.class,
            () -> schemaService.exportExcel(null));
    }
}
