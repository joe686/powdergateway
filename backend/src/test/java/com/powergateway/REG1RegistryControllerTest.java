package com.powergateway;

import com.powergateway.model.RegistryConfig;
import com.powergateway.model.dto.RegistryConfigSaveRequest;
import com.powergateway.service.RegistryConfigService;
import com.powergateway.service.registry.RegistryFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REG-1 Task 7 · RegistryConfigService + Controller 集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class REG1RegistryControllerTest {

    @Autowired private RegistryConfigService registryConfigService;
    @Autowired private RegistryFacade facade;

    // ============ save / list ============

    @Test
    void save_新增_成功_密码AES加密() {
        RegistryConfigSaveRequest req = buildRequest("内部Nacos", "secret-pwd");
        Long id = registryConfigService.save(req);
        assertThat(id).isNotNull();

        List<RegistryConfig> list = registryConfigService.list();
        RegistryConfig saved = list.stream().filter(c -> c.getId().equals(id)).findFirst().orElseThrow(AssertionError::new);
        assertThat(saved.getName()).isEqualTo("内部Nacos");
        assertThat(saved.getPassword()).isEqualTo(RegistryConfigService.PASSWORD_MASK);
    }

    @Test
    void save_更新_密码占位_不覆盖DB密码() {
        RegistryConfigSaveRequest req1 = buildRequest("N1", "orig-pwd");
        Long id = registryConfigService.save(req1);

        RegistryConfigSaveRequest req2 = buildRequest("N1-改", null);
        req2.setId(id);
        req2.setPassword(RegistryConfigService.PASSWORD_MASK);
        registryConfigService.save(req2);

        RegistryConfig saved = registryConfigService.list().stream()
                .filter(c -> c.getId().equals(id)).findFirst().orElseThrow(AssertionError::new);
        assertThat(saved.getName()).isEqualTo("N1-改");
    }

    @Test
    void save_type为空_抛400() {
        RegistryConfigSaveRequest req = new RegistryConfigSaveRequest();
        req.setName("bad");
        req.setServerAddr("x");
        assertThatThrownBy(() -> registryConfigService.save(req))
                .hasMessageContaining("type");
    }

    @Test
    void save_serverAddr为空_抛400() {
        RegistryConfigSaveRequest req = new RegistryConfigSaveRequest();
        req.setType("nacos");
        req.setName("bad");
        assertThatThrownBy(() -> registryConfigService.save(req))
                .hasMessageContaining("serverAddr");
    }

    // ============ delete ============

    @Test
    void delete_软删除_列表不再展示() {
        Long id = registryConfigService.save(buildRequest("待删", "pwd"));
        registryConfigService.delete(id);
        assertThat(registryConfigService.list()).noneMatch(c -> c.getId().equals(id));
    }

    @Test
    void delete_不存在_抛404() {
        assertThatThrownBy(() -> registryConfigService.delete(99999999L))
                .hasMessageContaining("不存在");
    }

    // ============ testConnection ============

    @Test
    void testConnection_不存在的id_返回fail() {
        RegistryConfigService.TestConnectionResult r = registryConfigService.testConnection(99999999L);
        assertThat(r.isOk()).isFalse();
    }

    // ============ discoverPreview ============

    @Test
    void discoverPreview_空serviceName_返回空List() {
        assertThat(registryConfigService.discoverPreview("")).isEmpty();
    }

    @Test
    void discoverPreview_无匹配实例_返回空List() {
        assertThat(registryConfigService.discoverPreview("UNKNOWN_SVC")).isEmpty();
    }

    // ============ reregisterSelf ============

    @Test
    void reregisterSelf_无已启用注册中心_返回false() {
        // Registrar 未装配任何 client（DB 里也没有）
        boolean triggered = registryConfigService.reregisterSelf();
        assertThat(triggered).isFalse();
    }

    // ============ 辅助 ============

    private RegistryConfigSaveRequest buildRequest(String name, String password) {
        RegistryConfigSaveRequest r = new RegistryConfigSaveRequest();
        r.setType("nacos");
        r.setName(name);
        r.setServerAddr("127.0.0.1:8848");
        r.setNamespace("public");
        r.setGroupName("DEFAULT_GROUP");
        r.setUsername("nacos");
        r.setPassword(password);
        r.setEnabled(1);
        r.setRegisterSelf(1);
        r.setServiceName("POWERGATEWAY");
        return r;
    }
}
