package com.powergateway;

import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
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
@DisplayName("FN-10 InterfaceConfigService exportList")
class FN10InterfaceListExportTest {

    @Autowired private InterfaceConfigService interfaceService;

    private Long createInterface(String name) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName(name);
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        return interfaceService.save(req);
    }

    @Test
    void 按name过滤_导出行数与列表一致() throws Exception {
        createInterface("测试导出A");
        createInterface("测试导出B");
        createInterface("别名");

        byte[] bytes = interfaceService.exportList("测试导出");
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(2, s.getLastRowNum(),
                "过滤 name='测试导出' 应导出 2 行数据 + 1 行 header");
        }
    }

    @Test
    void 全量导出_包含所有记录() throws Exception {
        createInterface("ExportAll_A");
        createInterface("ExportAll_B");

        byte[] bytes = interfaceService.exportList(null);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheetAt(0);
            assertTrue(s.getLastRowNum() >= 2, "全量导出行数应 >= 2");
        }
    }
}
