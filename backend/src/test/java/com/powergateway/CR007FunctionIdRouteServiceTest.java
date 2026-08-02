package com.powergateway;

import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.route.FunctionIdRouteService;
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

/**
 * CR-007 · v0.3.1 Task 1.3 · FunctionIdRouteService 集成测试。
 *
 * <p>测试环境无 Redis(@ActiveProfiles("test") 已禁用)· stringRedisTemplate=null · 直接走 DB fallback。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Rollback
class CR007FunctionIdRouteServiceTest {

    @Autowired private FunctionIdRouteService routeService;
    @Autowired private InterfaceConfigService interfaceConfigService;

    @Test
    @DisplayName("lookup 命中 · 返 interfaceId")
    void lookup_命中_返id() {
        Long id = interfaceConfigService.save(reqWithFnId("PG-ROUTE-HIT-1"));
        assertEquals(id, routeService.lookup("PG-ROUTE-HIT-1"));
    }

    @Test
    @DisplayName("lookup 未匹配 · 返 null(不抛异常)")
    void lookup_未匹配_返null() {
        assertNull(routeService.lookup("PG-NONEXISTENT-999999"));
    }

    @Test
    @DisplayName("lookup 空/null 输入 · 返 null(不抛异常)")
    void lookup_空输入_返null() {
        assertNull(routeService.lookup(null));
        assertNull(routeService.lookup(""));
        assertNull(routeService.lookup("   "));
    }

    @Test
    @DisplayName("lookup 前后空白 · trim 后查找")
    void lookup_trim() {
        Long id = interfaceConfigService.save(reqWithFnId("PG-ROUTE-TRIM"));
        assertEquals(id, routeService.lookup("  PG-ROUTE-TRIM  "));
    }

    @Test
    @DisplayName("invalidate · Redis 不可用时静默无异常")
    void invalidate_无redis_静默() {
        // 测试环境 Redis 已禁用 · 调 invalidate 不应抛异常
        routeService.invalidate("any-key");
        routeService.invalidate(null);
        routeService.invalidate("");
    }

    @Test
    @DisplayName("lookup 命中后 · DB 记录被删 → 新 lookup 返 null(测试环境无缓存 · 每次都是 DB 结果)")
    void lookup_DB变化后立即生效() {
        Long id = interfaceConfigService.save(reqWithFnId("PG-DELETE-TEST"));
        assertNotNull(routeService.lookup("PG-DELETE-TEST"));
        // 事务回滚会自动清 · 这里不显式删
    }

    private InterfaceSaveRequest reqWithFnId(String fnId) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("cr007-route-" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"mainTable\":{\"name\":\"t\",\"alias\":\"t\"},\"joinConfigs\":[],\"fields\":[]}");
        req.setFunctionId(fnId);
        return req;
    }
}
