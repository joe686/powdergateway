package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.dao.DbConnectionMapper;
import com.powergateway.dao.ShardConfigMapper;
import com.powergateway.model.DbConnection;
import com.powergateway.model.ShardConfig;
import com.powergateway.model.dto.ShardRouteResult;
import com.powergateway.model.dto.ShardSaveRequest;
import com.powergateway.service.ShardConfigService;
import com.powergateway.utils.AesUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("M2-8 分库分表配置")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class M28ShardConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ShardConfigService shardConfigService;
    @Autowired private ShardConfigMapper shardConfigMapper;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private Long testDbId;

    private static final String H2_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String AES_KEY = "PowerGateway128K";

    /** Helper: build a valid MODULO shard_rule JSON using the given dbConnectionId */
    private String moduloRule(Long dbId) {
        return "{" +
            "\"routingField\":\"userId\"," +
            "\"algorithm\":{\"type\":\"MODULO\",\"divisor\":16}," +
            "\"dbSegments\":[" +
                "{\"dbConnectionId\":" + dbId + ",\"tablePrefix\":\"orders_\",\"indexStart\":0,\"indexEnd\":7,\"indexPadding\":0}," +
                "{\"dbConnectionId\":" + dbId + ",\"tablePrefix\":\"orders_\",\"indexStart\":8,\"indexEnd\":15,\"indexPadding\":0}" +
            "]}";
    }

    @BeforeAll
    void setUp() {
        DbConnection conn = new DbConnection();
        conn.setName("H2_Shard_" + System.currentTimeMillis());
        conn.setDbType("MySQL");
        conn.setUrl(H2_URL);
        conn.setUsername("sa");
        conn.setPassword(AesUtil.encrypt("", AES_KEY));
        conn.setEnv("test");
        conn.setPoolSize(2);
        conn.setTimeout(3000);
        dbConnectionMapper.insert(conn);
        testDbId = conn.getId();
    }

    @BeforeEach
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    @AfterAll
    void cleanup() {
        if (testDbId != null) dbConnectionMapper.deleteById(testDbId);
    }

    // ─── Service CRUD ────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("save_新增_返回ID且能查到")
    void save_new_returnIdAndListable() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("测试规则_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        assertThat(id).isNotNull().isPositive();
        List<ShardConfig> list = shardConfigService.list(null, 1, 100);
        assertThat(list).anyMatch(c -> c.getId().equals(id));
        shardConfigMapper.deleteById(id);
    }

    @Test @Order(2)
    @DisplayName("save_名称为空_抛异常")
    void save_emptyName_throws() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setShardRule(moduloRule(testDbId));
        assertThatThrownBy(() -> shardConfigService.save(req))
                .hasMessageContaining("名称不能为空");
    }

    @Test @Order(3)
    @DisplayName("save_分片规则JSON格式错误_抛异常")
    void save_invalidJson_throws() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("bad");
        req.setShardRule("not-json{{");
        assertThatThrownBy(() -> shardConfigService.save(req))
                .hasMessageContaining("JSON 格式错误");
    }

    @Test @Order(4)
    @DisplayName("delete_逻辑删除后list查不到")
    void delete_notInListAfterDelete() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("待删_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        shardConfigService.delete(id);
        assertThat(shardConfigService.list(null, 1, 100)).noneMatch(c -> c.getId().equals(id));
    }

    @Test @Order(5)
    @DisplayName("preview_取模路由_直接从参数取值_返回正确库表名")
    void preview_modulo_direct_correctTableName() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("预览_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", "3"); // 3 % 16 = 3 → orders_3
            ShardRouteResult result = shardConfigService.preview(id, params);
            assertThat(result.getDbConnectionId()).isEqualTo(testDbId);
            assertThat(result.getTableName()).isEqualTo("orders_3");
            assertThat(result.getDbName()).isNotNull();
        } finally {
            shardConfigMapper.deleteById(id);
        }
    }

    @Test @Order(6)
    @DisplayName("preview_路由字段不在参数中_抛异常")
    void preview_missingRoutingField_throws() {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("缺参数_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        try {
            assertThatThrownBy(() -> shardConfigService.preview(id, new HashMap<>()))
                    .hasMessageContaining("路由字段");
        } finally {
            shardConfigMapper.deleteById(id);
        }
    }
}
