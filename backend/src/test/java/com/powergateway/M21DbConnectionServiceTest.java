package com.powergateway;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.powergateway.model.dto.DbConnectionSaveRequest;
import com.powergateway.model.dto.DbConnectionVO;
import com.powergateway.service.DbConnectionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("M2-1 DbConnectionService")
class M21DbConnectionServiceTest {

    @Autowired
    private DbConnectionService service;

    @Autowired
    private DataSource dataSource;

    private Long savedId;

    // ── 辅助方法 ──────────────────────────────────────────
    private DbConnectionSaveRequest buildRequest(Long id) {
        DbConnectionSaveRequest req = new DbConnectionSaveRequest();
        req.setId(id);
        req.setName("测试MySQL连接");
        req.setDbType("MySQL");
        req.setUrl("jdbc:mysql://localhost:3306/test_nonexist");
        req.setUsername("root");
        req.setPassword("testPassword@123");
        req.setEnv("dev");
        req.setPoolSize(2);
        req.setTimeout(3000);
        return req;
    }

    @AfterAll
    void cleanup() {
        if (savedId != null) {
            DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
            String key = "db_" + savedId;
            if (dds.getDataSources().containsKey(key)) {
                dds.removeDataSource(key);
            }
        }
    }

    // ── 测试用例 ──────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("save_新建_密码加密存储_数据源注册")
    void save_新建_密码已加密_数据源已注册() {
        savedId = service.save(buildRequest(null));
        assertNotNull(savedId);

        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        assertTrue(dds.getDataSources().containsKey("db_" + savedId),
                "保存后 DynamicRoutingDataSource 中应存在对应 key");
    }

    @Test
    @Order(2)
    @DisplayName("list_返回列表_password字段脱敏为***")
    void list_密码脱敏() {
        List<DbConnectionVO> list = service.list();
        assertFalse(list.isEmpty());
        list.forEach(vo -> assertEquals("***", vo.getPassword(),
                "接口返回的 password 必须脱敏为 ***"));
    }

    @Test
    @Order(3)
    @DisplayName("save_更新_密码传***不覆盖原值")
    void save_更新_密码传星号_原密码不变() {
        DbConnectionSaveRequest req = buildRequest(savedId);
        req.setPassword("***");
        req.setName("更新后连接名");
        assertDoesNotThrow(() -> service.save(req));

        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        assertTrue(dds.getDataSources().containsKey("db_" + savedId),
                "更新后数据源应仍注册");
    }

    @Test
    @Order(4)
    @DisplayName("delete_软删除_数据源注销")
    void delete_软删除_数据源注销() {
        service.delete(savedId);

        DynamicRoutingDataSource dds = (DynamicRoutingDataSource) dataSource;
        assertFalse(dds.getDataSources().containsKey("db_" + savedId),
                "删除后 DynamicRoutingDataSource 中应移除对应 key");

        savedId = null; // 避免 @AfterAll 重复清理
    }
}
