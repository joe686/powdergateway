package com.powergateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.powergateway.dao.*;
import com.powergateway.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-3 验收测试：验证 8 张配置库表 DDL 正确，实体类与 Mapper 可正常 CRUD
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class P03EntityMapperTest {

    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private ConvertTemplateMapper convertTemplateMapper;
    @Autowired private ChannelConfigMapper channelConfigMapper;
    @Autowired private DbConnectionMapper dbConnectionMapper;
    @Autowired private InterfaceConfigMapper interfaceConfigMapper;
    @Autowired private ShardConfigMapper shardConfigMapper;
    @Autowired private FieldFormulaMapper fieldFormulaMapper;
    @Autowired private SysConfigMapper sysConfigMapper;

    // ───────── sys_user ─────────

    @Test
    void sysUser_insertAndQuery() {
        SysUser user = new SysUser();
        user.setUsername("test_admin");
        user.setPassword("$2a$10$mockBcryptHash");
        user.setRole("admin");
        user.setStatus(1);

        int rows = sysUserMapper.insert(user);
        assertThat(rows).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        SysUser found = sysUserMapper.selectById(user.getId());
        assertThat(found.getUsername()).isEqualTo("test_admin");
        assertThat(found.getRole()).isEqualTo("admin");
    }

    @Test
    void sysUser_updateAndDelete() {
        SysUser user = new SysUser();
        user.setUsername("to_be_deleted");
        user.setPassword("hash");
        user.setRole("user");
        user.setStatus(1);
        sysUserMapper.insert(user);

        user.setStatus(0);
        sysUserMapper.updateById(user);
        SysUser updated = sysUserMapper.selectById(user.getId());
        assertThat(updated.getStatus()).isEqualTo(0);

        // sys_user 无软删除字段，使用物理删除
        sysUserMapper.deleteById(user.getId());
        assertThat(sysUserMapper.selectById(user.getId())).isNull();
    }

    // ───────── convert_template ─────────

    @Test
    void convertTemplate_insertAndSoftDelete() {
        ConvertTemplate tpl = new ConvertTemplate();
        tpl.setName("测试模板");
        tpl.setSrcFormat("JSON");
        tpl.setTargetFormat("XML");
        tpl.setMappingRule("[{\"srcField\":\"userId\",\"targetField\":\"user_id\"}]");
        tpl.setProcessRule("[]");
        tpl.setIsLatest(1);
        tpl.setVersion(1);
        tpl.setCreator("admin");

        convertTemplateMapper.insert(tpl);
        assertThat(tpl.getId()).isNotNull();

        // 软删除：deleted = 1，select 自动过滤
        convertTemplateMapper.deleteById(tpl.getId());
        assertThat(convertTemplateMapper.selectById(tpl.getId())).isNull();
    }

    // ───────── channel_config ─────────

    @Test
    void channelConfig_insertAndQuery() {
        ChannelConfig cfg = new ChannelConfig();
        cfg.setChannelCode("CH001");
        cfg.setChannelName("渠道A");
        cfg.setIdentifyField("channelId");
        cfg.setTemplateId(1L);

        channelConfigMapper.insert(cfg);
        assertThat(cfg.getId()).isNotNull();

        ChannelConfig found = channelConfigMapper.selectOne(
            new LambdaQueryWrapper<ChannelConfig>().eq(ChannelConfig::getChannelCode, "CH001"));
        assertThat(found).isNotNull();
        assertThat(found.getChannelName()).isEqualTo("渠道A");
    }

    // ───────── db_connection ─────────

    @Test
    void dbConnection_insertAndQuery() {
        DbConnection conn = new DbConnection();
        conn.setName("测试MySQL连接");
        conn.setDbType("MySQL");
        conn.setUrl("jdbc:mysql://localhost:3306/test");
        conn.setUsername("root");
        conn.setPassword("AES_ENCRYPTED_PWD");
        conn.setEnv("dev");
        conn.setPoolSize(5);
        conn.setTimeout(3000);

        dbConnectionMapper.insert(conn);
        assertThat(conn.getId()).isNotNull();

        DbConnection found = dbConnectionMapper.selectById(conn.getId());
        assertThat(found.getDbType()).isEqualTo("MySQL");
        assertThat(found.getEnv()).isEqualTo("dev");
    }

    // ───────── interface_config ─────────

    @Test
    void interfaceConfig_insertAndStatusCheck() {
        InterfaceConfig ic = new InterfaceConfig();
        ic.setName("查询用户接口");
        ic.setPath("/api/exec/user/list");
        ic.setType("SELECT");
        ic.setDbConnectionId(1L);
        ic.setConfigJson("{\"tables\":[\"sys_user\"]}");
        ic.setStatus("draft");
        ic.setAllowBatchDelete(0);
        ic.setLogEnabled(1);
        ic.setCreator("admin");

        interfaceConfigMapper.insert(ic);
        assertThat(ic.getId()).isNotNull();

        // 状态变更：draft → published
        ic.setStatus("published");
        interfaceConfigMapper.updateById(ic);
        InterfaceConfig found = interfaceConfigMapper.selectById(ic.getId());
        assertThat(found.getStatus()).isEqualTo("published");
    }

    // ───────── shard_config ─────────

    @Test
    void shardConfig_insertAndQuery() {
        ShardConfig sc = new ShardConfig();
        sc.setName("用户分库规则");
        sc.setModuleName("user");
        sc.setRequestField("userId");
        sc.setShardRule("{\"shards\":[{\"range\":\"0-99\",\"db\":\"db0\"}]}");

        shardConfigMapper.insert(sc);
        assertThat(sc.getId()).isNotNull();

        ShardConfig found = shardConfigMapper.selectById(sc.getId());
        assertThat(found.getModuleName()).isEqualTo("user");
        assertThat(found.getShardRule()).contains("db0");
    }

    // ───────── field_formula ─────────

    @Test
    void fieldFormula_insertAndQuery() {
        FieldFormula ff = new FieldFormula();
        ff.setName("年龄计算公式");
        ff.setScene("用户管理");
        ff.setDbConnectionId(1L);
        ff.setFormulaJson("{\"type\":\"age_calc\",\"field\":\"birth_date\"}");
        ff.setRemark("根据生日计算年龄");
        ff.setCreator("admin");

        fieldFormulaMapper.insert(ff);
        assertThat(ff.getId()).isNotNull();

        FieldFormula found = fieldFormulaMapper.selectById(ff.getId());
        assertThat(found.getName()).isEqualTo("年龄计算公式");
        assertThat(found.getScene()).isEqualTo("用户管理");
    }

    // ───────── sys_config ─────────

    @Test
    void sysConfig_preloadedValues() {
        // 验证 init-h2.sql 已预置 4 条系统配置
        List<SysConfig> configs = sysConfigMapper.selectList(null);
        assertThat(configs).hasSizeGreaterThanOrEqualTo(4);

        SysConfig cacheTtl = sysConfigMapper.selectById("cache.query.ttl");
        assertThat(cacheTtl).isNotNull();
        assertThat(cacheTtl.getConfigValue()).isEqualTo("300");
    }

    @Test
    void sysConfig_insertAndUpdate() {
        SysConfig cfg = new SysConfig();
        cfg.setConfigKey("test.feature.enabled");
        cfg.setConfigValue("true");
        cfg.setDescription("测试功能开关");

        sysConfigMapper.insert(cfg);

        SysConfig found = sysConfigMapper.selectById("test.feature.enabled");
        assertThat(found.getConfigValue()).isEqualTo("true");

        found.setConfigValue("false");
        sysConfigMapper.updateById(found);
        assertThat(sysConfigMapper.selectById("test.feature.enabled").getConfigValue()).isEqualTo("false");
    }

    // ───────── 全部 8 张表可查 ─────────

    @Test
    void allEightTables_selectListNoException() {
        // 验证 8 张表均可正常查询（无异常即通过）
        sysUserMapper.selectList(null);
        convertTemplateMapper.selectList(null);
        channelConfigMapper.selectList(null);
        dbConnectionMapper.selectList(null);
        interfaceConfigMapper.selectList(null);
        shardConfigMapper.selectList(null);
        fieldFormulaMapper.selectList(null);
        sysConfigMapper.selectList(null);
    }
}
