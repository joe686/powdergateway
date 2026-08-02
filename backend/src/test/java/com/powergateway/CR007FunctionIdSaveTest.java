package com.powergateway;

import com.powergateway.exception.BusinessException;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-007 · v0.3.1 Task 1.1 · interface_config.function_id 唯一校验单元测试。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class CR007FunctionIdSaveTest {

    @Autowired private InterfaceConfigService service;

    @Test
    @DisplayName("function_id 为空 · 允许保存")
    void functionId空_允许保存() {
        Long id = service.save(minimalReq(null));
        assertNotNull(id);
    }

    @Test
    @DisplayName("function_id 有值 · 首次保存成功")
    void functionId有值_首次保存() {
        Long id = service.save(minimalReq("PG-181345"));
        assertNotNull(id);
    }

    @Test
    @DisplayName("function_id 重复 · 新建时抛 BusinessException")
    void functionId重复_新建时抛异常() {
        service.save(minimalReq("PG-DUPLICATE-1"));
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.save(minimalReq("PG-DUPLICATE-1"))
        );
        assertTrue(ex.getMessage().contains("已被其他接口占用"));
        assertTrue(ex.getMessage().contains("PG-DUPLICATE-1"));
    }

    @Test
    @DisplayName("function_id 同一接口更新 · 允许(排除自身)")
    void functionId同一接口更新_允许() {
        Long id = service.save(minimalReq("PG-SELF-UPDATE"));
        InterfaceSaveRequest updateReq = minimalReq("PG-SELF-UPDATE");
        updateReq.setId(id);
        updateReq.setName("更新后的名称");
        Long updatedId = service.save(updateReq);
        assertEquals(id, updatedId);
    }

    @Test
    @DisplayName("function_id 更新为已被占用 · 抛异常")
    void functionId更新为已占用_抛异常() {
        service.save(minimalReq("PG-OCCUPIED-BY-A"));
        Long idB = service.save(minimalReq("PG-B-ORIGINAL"));

        InterfaceSaveRequest bUpdate = minimalReq("PG-OCCUPIED-BY-A");
        bUpdate.setId(idB);
        bUpdate.setName("B 想改成 A 的功能号");
        assertThrows(BusinessException.class, () -> service.save(bUpdate));
    }

    @Test
    @DisplayName("function_id 空串等价于 null · 允许多个接口共存")
    void functionId空串_视作null() {
        Long id1 = service.save(minimalReq(""));
        Long id2 = service.save(minimalReq("   "));
        assertNotNull(id1);
        assertNotNull(id2);
    }

    private InterfaceSaveRequest minimalReq(String functionId) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("cr007-test-" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"mainTable\":{\"name\":\"t\",\"alias\":\"t\"},\"joinConfigs\":[],\"fields\":[]}");
        req.setFunctionId(functionId);
        return req;
    }
}
