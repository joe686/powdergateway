package com.powergateway;

import com.powergateway.model.dto.TemplateSaveRequest;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.ConfigExportService;
import com.powergateway.service.TemplateService;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FN-11 ConfigExportService 全量导出")
class FN11ConfigExportServiceTest {

    @Autowired private ConfigExportService exportService;
    @Autowired private TemplateService templateService;
    @Autowired private InterfaceConfigService interfaceService;

    private Long createTemplate(String name) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setName(name);
        req.setSrcFormat("JSON");
        req.setTargetFormat("XML");
        req.setMappingRules(Collections.emptyList());
        return templateService.saveTemplate(req);
    }

    private Long createInterface(String name) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName(name);
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        return interfaceService.save(req);
    }

    @Test
    void 导出zip_包含manifest() throws Exception {
        createTemplate("ExportTpl1");
        createInterface("ExportIface1");

        byte[] zip = exportService.exportAll();
        assertNotNull(zip);
        assertTrue(zip.length > 0);

        boolean hasManifest = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(e.getName())) hasManifest = true;
                zis.closeEntry();
            }
        }
        assertTrue(hasManifest, "zip 应包含 manifest.json");
    }

    @Test
    void 导出zip_包含模板和接口JSON文件() throws Exception {
        createTemplate("TplForZip");
        createInterface("IfaceForZip");

        byte[] zip = exportService.exportAll();
        int templateFiles = 0;
        int interfaceFiles = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.startsWith("templates/") && name.endsWith(".json")) templateFiles++;
                if (name.startsWith("interfaces/") && name.endsWith(".json")) interfaceFiles++;
                zis.closeEntry();
            }
        }
        assertTrue(templateFiles >= 1, "应包含至少 1 个模板文件");
        assertTrue(interfaceFiles >= 1, "应包含至少 1 个接口文件");
    }

    @Test
    void 空数据库_导出zip_仍包含manifest() throws Exception {
        byte[] zip = exportService.exportAll();
        assertNotNull(zip);
        boolean hasManifest = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("manifest.json".equals(e.getName())) hasManifest = true;
                zis.closeEntry();
            }
        }
        assertTrue(hasManifest, "即使无数据也应包含 manifest.json");
    }
}
