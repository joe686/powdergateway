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

    // ─── Controller 测试 ─────────────────────────────────────────────────────────

    @Test @Order(10)
    @DisplayName("POST /api/shard/save → 200 返回 id")
    void api_save_returns200() throws Exception {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("name", "API_" + System.currentTimeMillis());
        reqMap.put("shardRule", moduloRule(testDbId));
        String body = objectMapper.writeValueAsString(reqMap);
        MvcResult r = mockMvc.perform(post("/api/shard/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();
        Long id = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.data")).longValue();
        shardConfigMapper.deleteById(id);
    }

    @Test @Order(11)
    @DisplayName("GET /api/shard/list → 200 返回数组")
    void api_list_returns200() throws Exception {
        mockMvc.perform(get("/api/shard/list").header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(12)
    @DisplayName("DELETE /api/shard/{id} → 200")
    void api_delete_returns200() throws Exception {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("待删_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        mockMvc.perform(delete("/api/shard/" + id).header("satoken", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test @Order(13)
    @DisplayName("POST /api/shard/{id}/preview → 200 返回路由结果")
    void api_preview_returns200WithTableName() throws Exception {
        ShardSaveRequest req = new ShardSaveRequest();
        req.setName("预览API_" + System.currentTimeMillis());
        req.setShardRule(moduloRule(testDbId));
        Long id = shardConfigService.save(req);
        try {
            mockMvc.perform(post("/api/shard/" + id + "/preview")
                            .header("satoken", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"params\":{\"userId\":\"3\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.tableName").value("orders_3"))
                    .andExpect(jsonPath("$.data.dbConnectionId").isNumber());
        } finally {
            shardConfigMapper.deleteById(id);
        }
    }

    @Test @Order(14)
    @DisplayName("未登录访问 /api/shard/list → 401")
    void api_noToken_401() throws Exception {
        mockMvc.perform(get("/api/shard/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ─── Exec 集成测试：分片路由替换主表名 ────────────────────────────────────────

    @Test @Order(20)
    @DisplayName("executeQuery_配置分片路由_替换主表名后成功查询")
    void execQuery_withSharding_replacesTableNameAndSucceeds() throws Exception {
        // Shard rule: RANGE 0~MAX → sys_user (a table that exists in H2)
        ShardSaveRequest shardReq = new ShardSaveRequest();
        shardReq.setName("ExecShardTest_" + System.currentTimeMillis());
        shardReq.setShardRule("{" +
            "\"routingField\":\"userId\"," +
            "\"algorithm\":{\"type\":\"RANGE\"}," +
            "\"shards\":[{\"rangeStart\":0,\"rangeEnd\":9999999999,\"dbConnectionId\":" + testDbId +
            ",\"tableName\":\"sys_user\"}]}");
        Long shardId = shardConfigService.save(shardReq);

        // Interface config: SELECT from "nonexistent_table" — sharding will replace with sys_user
        String configJson = "{" +
            "\"tables\":[{\"name\":\"nonexistent_table\",\"alias\":\"u\"}]," +
            "\"joins\":[]," +
            "\"fields\":[{\"table\":\"u\",\"column\":\"id\",\"alias\":\"uid\"}]," +
            "\"conditions\":[]," +
            "\"processRules\":[]}";

        java.util.Map<String, Object> saveBody = new java.util.LinkedHashMap<>();
        saveBody.put("name", "ShardExec_" + System.currentTimeMillis());
        saveBody.put("dbConnectionId", testDbId);
        saveBody.put("type", "SELECT");
        saveBody.put("configJson", configJson);
        saveBody.put("shardConfigId", shardId);

        MvcResult saveRes = mockMvc.perform(post("/api/interface/save")
                        .header("satoken", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveBody)))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Long interfaceId = ((Number) JsonPath.read(saveRes.getResponse().getContentAsString(), "$.data")).longValue();

        try {
            // Publish
            mockMvc.perform(post("/api/interface/" + interfaceId + "/publish")
                            .header("satoken", token))
                    .andExpect(jsonPath("$.code").value(200));

            // Execute: userId=5 → RANGE matches → tableName=sys_user → query returns data
            mockMvc.perform(post("/api/exec/" + interfaceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"params\":{\"userId\":\"5\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        } finally {
            shardConfigMapper.deleteById(shardId);
            mockMvc.perform(post("/api/interface/" + interfaceId + "/disable")
                    .header("satoken", token));
            mockMvc.perform(delete("/api/interface/" + interfaceId)
                    .header("satoken", token));
        }
    }
}
