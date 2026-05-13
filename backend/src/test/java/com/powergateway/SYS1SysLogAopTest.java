package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.SysLogMapper;
import com.powergateway.model.SysLog;
import com.powergateway.service.SysLogService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SYS-1 SysLogAspect AOP 拦截测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SYS1SysLogAopTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SysLogService sysLogService;
    @Autowired private SysLogMapper sysLogMapper;

    @AfterAll
    void cleanup() {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getModule, "认证"));
    }

    @Test
    @Order(1)
    void 登录成功_切面写入INFO日志() throws Exception {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getModule, "认证").eq(SysLog::getAction, "用户登录"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk());

        sysLogService.flushForTest();

        List<SysLog> logs = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>()
                        .eq(SysLog::getModule, "认证")
                        .eq(SysLog::getAction, "用户登录")
                        .eq(SysLog::getLevel, "INFO"));
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getCostMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(2)
    void 登录失败_切面写入ERROR日志() throws Exception {
        sysLogMapper.delete(new LambdaQueryWrapper<SysLog>()
                .eq(SysLog::getModule, "认证").eq(SysLog::getLevel, "ERROR"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong_password\"}"))
                .andExpect(status().isOk()); // GlobalExceptionHandler returns 200 + code:401

        sysLogService.flushForTest();

        List<SysLog> logs = sysLogMapper.selectList(
                new LambdaQueryWrapper<SysLog>()
                        .eq(SysLog::getModule, "认证")
                        .eq(SysLog::getLevel, "ERROR"));
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getErrorMsg()).isNotBlank();
    }
}
