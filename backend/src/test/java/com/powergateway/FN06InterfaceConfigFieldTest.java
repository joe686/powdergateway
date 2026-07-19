package com.powergateway;

import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("FN-06 interface_config responseFormat/responseHeaders 字段落库")
class FN06InterfaceConfigFieldTest {

    @Autowired private InterfaceConfigService service;

    @Test
    void 保存接口_默认responseFormat为JSON() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("FN06默认格式_" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        Long id = service.save(req);
        InterfaceConfig cfg = service.getById(id);
        assertEquals("JSON", cfg.getResponseFormat(), "未指定响应格式时应默认为 JSON");
    }

    @Test
    void 保存接口_指定XML与自定义响应头_可回读() {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("FN06XML_" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"tables\":[],\"fields\":[],\"conditions\":[],\"joins\":[]}");
        req.setResponseFormat("XML");
        req.setResponseHeaders("{\"X-Foo\":\"bar\"}");
        Long id = service.save(req);
        InterfaceConfig cfg = service.getById(id);
        assertEquals("XML", cfg.getResponseFormat());
        assertTrue(cfg.getResponseHeaders().contains("X-Foo"));
    }
}
