package com.powergateway;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.powergateway.model.PortRoute;
import com.powergateway.model.dto.PortRouteSaveRequest;
import com.powergateway.service.PortRouteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UX-D Task 4：port_route.function_code + function_name 字段测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UXDPortRouteFunctionCodeTest {

    @Autowired
    PortRouteService svc;

    @Test
    void 保存路由并回读_functionCode与functionName生效() {
        PortRouteSaveRequest req = new PortRouteSaveRequest();
        req.setChannelCode("UXD_CH_1");
        req.setPortAddress("http://localhost:9999/mock");
        req.setFunctionCode("UXD_TEST_01");
        req.setFunctionName("UXD 测试01");
        Long id = svc.saveRoute(req);
        PortRoute r = svc.getById(id);
        assertNotNull(r, "路由应存在");
        assertEquals("UXD_TEST_01", r.getFunctionCode());
        assertEquals("UXD 测试01", r.getFunctionName());
    }

    @Test
    void listRoutes_按functionCode精确过滤() {
        // 构造 2 条：fc=A、fc=B，断言按 A 查只返回一条
        PortRouteSaveRequest reqA = new PortRouteSaveRequest();
        reqA.setChannelCode("UXD_CH_A");
        reqA.setPortAddress("http://localhost:9999/a");
        reqA.setFunctionCode("FC_FILTER_A");
        svc.saveRoute(reqA);

        PortRouteSaveRequest reqB = new PortRouteSaveRequest();
        reqB.setChannelCode("UXD_CH_B");
        reqB.setPortAddress("http://localhost:9999/b");
        reqB.setFunctionCode("FC_FILTER_B");
        svc.saveRoute(reqB);

        Page<PortRoute> page = svc.listRoutes(1, 10, null, "FC_FILTER_A");
        assertEquals(1, page.getRecords().size());
        assertEquals("FC_FILTER_A", page.getRecords().get(0).getFunctionCode());
    }

    @Test
    void listRoutes_functionCode为空_仍走原channelCode过滤() {
        PortRouteSaveRequest req = new PortRouteSaveRequest();
        req.setChannelCode("UXD_CHAN_X");
        req.setPortAddress("http://localhost:9999/x");
        req.setFunctionCode("FC_X");
        svc.saveRoute(req);

        // functionCode 为 null 时按 channelCode 过滤
        Page<PortRoute> page = svc.listRoutes(1, 10, "UXD_CHAN_X", null);
        assertTrue(page.getRecords().stream().anyMatch(r -> "UXD_CHAN_X".equals(r.getChannelCode())));
    }
}
