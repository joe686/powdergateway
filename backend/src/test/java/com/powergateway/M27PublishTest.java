package com.powergateway;

import com.powergateway.exception.BusinessException;
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
@DisplayName("M2-7 接口发布/禁用/删除校验/executeQuery")
class M27PublishTest {

    @Autowired private InterfaceConfigService service;

    private Long createInterface(String type, String configJson) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("M27测试接口_" + type);
        req.setDbConnectionId(1L);
        req.setType(type);
        req.setConfigJson(configJson);
        return service.save(req);
    }

    private static final String SELECT_CONFIG =
        "{\"tables\":[{\"name\":\"m27_test\",\"alias\":\"t\"}]," +
        "\"fields\":[{\"table\":\"t\",\"column\":\"id\",\"alias\":\"id\"}]," +
        "\"conditions\":[],\"joins\":[]}";

    @Test
    void 发布接口_状态变为published且path写入正确() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        InterfaceConfig cfg = service.getById(id);
        assertEquals("published", cfg.getStatus());
        assertEquals("/api/exec/" + id, cfg.getPath());
    }

    @Test
    void 禁用已发布接口_状态变为disabled() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        service.disable(id);
        assertEquals("disabled", service.getById(id).getStatus());
    }

    @Test
    void 禁用草稿接口_也可以禁用() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.disable(id);
        assertEquals("disabled", service.getById(id).getStatus());
    }

    @Test
    void 重复发布_幂等不报错() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        assertDoesNotThrow(() -> service.publish(id));
        assertEquals("published", service.getById(id).getStatus());
    }

    @Test
    void 禁用后重新发布_状态恢复published() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        service.disable(id);
        service.publish(id);
        assertEquals("published", service.getById(id).getStatus());
    }

    @Test
    void 删除已发布接口_抛出BusinessException() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.publish(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(id));
        assertTrue(ex.getMessage().contains("禁用"), "异常信息应提示先禁用");
    }

    @Test
    void 删除草稿接口_成功() {
        Long id = createInterface("SELECT", SELECT_CONFIG);
        service.delete(id);
        assertThrows(BusinessException.class, () -> service.getById(id));
    }
}
