package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.InterfaceConfigMapper;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.InterfaceSaveRequest;
import com.powergateway.service.InterfaceConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CR-007 · v0.3.1 Task 1.4 · RouteController 集成测试。
 *
 * <p>免登(/api/route 已加 excludePathPatterns)· 不带 satoken header 也能调。</p>
 * <p>测试环境无 Redis · 走 DB fallback · 无字典数据 · ChannelMapper 走 fallback 直接返原值。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback
class CR007RouteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InterfaceConfigService interfaceConfigService;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;

    @Test
    @DisplayName("免登 · 不带 satoken · 也能命中(与 /api/exec/** 一致)")
    void 免登_不带satoken() throws Exception {
        Long id = publishSelect("PG-ROUTE-INT-1");

        Map<String, Object> body = new HashMap<>();
        body.put("functionId", "PG-ROUTE-INT-1"); // 渠道直送 PG 功能号 · fallback 命中

        // 无 satoken · 不 401
        // 命中路由:断言业务错误消息 not contains 路由前置校验的错(功能号缺失/未找到/未发布)
        // 说明路由成功进入 dispatch(后续 executeQuery 因测试环境无真实 db_connection 会失败,那是业务层)
        String resp = mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        assertRouteReached(resp);
    }

    @Test
    @DisplayName("请求不带 functionId · 400")
    void 不带functionId_400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("other", "value");

        mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("功能号缺失")));
    }

    @Test
    @DisplayName("functionId 未匹配任何接口 · 404")
    void functionId未匹配_404() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("functionId", "PG-NEVER-EXIST-99999");

        mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未找到匹配接口")));
    }

    @Test
    @DisplayName("body 空 · 400")
    void body空_400() throws Exception {
        // 空对象 {} · 不带 functionId → 400 缺失
        mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("字段名 fallback 到 sys_config · 默认 functionId 可用")
    void 字段名_sysconfig_默认() throws Exception {
        // 未在 sys_config 里配 route.function.id.field · getString fallback 默认 "functionId"
        Long id = publishSelect("PG-FIELD-DEFAULT");
        Map<String, Object> body = new HashMap<>();
        body.put("functionId", "PG-FIELD-DEFAULT");

        String resp = mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        assertRouteReached(resp);
    }

    /**
     * 断言路由已成功进入 dispatch(可能后续业务执行因测试环境无真实 db 而 fail,不影响路由验证)。
     */
    private void assertRouteReached(String responseBody) {
        // 消息不应含路由前置校验的失败标识
        org.junit.jupiter.api.Assertions.assertFalse(
                responseBody.contains("功能号缺失") ||
                responseBody.contains("未找到匹配接口") ||
                responseBody.contains("接口未发布") ||
                responseBody.contains("接口已禁用") ||
                responseBody.contains("请求体不能为空"),
                "路由前置校验应通过 · 实际响应:" + responseBody);
    }

    // ============ Helper ============

    private Long publishSelect(String fnId) {
        InterfaceSaveRequest req = new InterfaceSaveRequest();
        req.setName("cr007-route-ctrl-" + System.nanoTime());
        req.setDbConnectionId(1L);
        req.setType("SELECT");
        req.setConfigJson("{\"mainTable\":{\"name\":\"sys_config\",\"alias\":\"c\"}," +
                "\"joinConfigs\":[]," +
                "\"fields\":[{\"tableAlias\":\"c\",\"columnName\":\"config_key\",\"alias\":\"key\"}]}");
        req.setFunctionId(fnId);
        Long id = interfaceConfigService.save(req);
        // 通过 mapper 直接改 status=published 绕过 publish 的额外校验(测试聚焦路由分发前置校验)
        InterfaceConfig cfg = interfaceConfigMapper.selectById(id);
        cfg.setStatus("published");
        interfaceConfigMapper.updateById(cfg);
        return id;
    }
}
