package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.model.ConvertTemplate;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.model.dto.FieldMappingRule;
import com.powergateway.service.codec.MarkdownConfigCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FN-11 Task 4 · MarkdownConfigCodec 单元测试
 */
@ActiveProfiles("test")
class FN11MarkdownCodecTest {

    private ObjectMapper objectMapper;
    private MarkdownConfigCodec codec;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        codec = new MarkdownConfigCodec(objectMapper);
    }

    // ============ 接口配置 ============

    @Test
    void encodeInterface_含标题_元数据_configJson代码块() {
        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setId(101L);
        cfg.setName("按用户名查用户");
        cfg.setType("SELECT");
        cfg.setPath("/api/exec/101");
        cfg.setDbConnectionId(1L);
        cfg.setStatus("published");
        cfg.setResponseFormat("JSON");
        cfg.setCacheEnabled(1);
        cfg.setCacheTtlSeconds(300);
        cfg.setConfigJson("{\"tables\":[{\"name\":\"sys_user\",\"alias\":\"u\"}]}");

        String md = codec.encodeInterface(cfg);
        assertThat(md).contains("# 接口：按用户名查用户");
        assertThat(md).contains("SELECT");
        assertThat(md).contains("**接口ID**");
        assertThat(md).contains("**访问路径**: /api/exec/101");
        assertThat(md).contains("**启用缓存**: 是");
        assertThat(md).contains("```json");
        assertThat(md).contains("sys_user");
    }

    @Test
    void encodeInterface_缓存未启用_显示否() {
        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setName("无缓存");
        cfg.setType("SELECT");
        cfg.setCacheEnabled(0);
        cfg.setConfigJson("{}");
        String md = codec.encodeInterface(cfg);
        assertThat(md).contains("**启用缓存**: 否");
    }

    @Test
    void encodeInterface_configJson为空_不崩溃() {
        InterfaceConfig cfg = new InterfaceConfig();
        cfg.setName("空配置");
        cfg.setType("SELECT");
        String md = codec.encodeInterface(cfg);
        assertThat(md).contains("# 接口：空配置");
    }

    // ============ 转换模板 ============

    @Test
    void encodeTemplate_含标题_元数据_映射表_加工表() throws Exception {
        ConvertTemplate t = new ConvertTemplate();
        t.setId(11L);
        t.setName("扁平模板示例");
        t.setSrcFormat("JSON");
        t.setTargetFormat("XML");
        t.setFunctionCode("BIZ_001");

        FieldMappingRule r1 = new FieldMappingRule();
        r1.setSrcField("userId");
        r1.setTargetField("uid");
        FieldMappingRule r2 = new FieldMappingRule();
        r2.setSrcField(null);
        r2.setTargetField("version");
        r2.setFixedValue("1.0.0");
        t.setMappingRule(objectMapper.writeValueAsString(Arrays.asList(r1, r2)));

        t.setProcessRule("[{\"type\":\"TRIM\",\"params\":{\"mode\":\"BOTH\"}}]");

        String md = codec.encodeTemplate(t);
        assertThat(md).contains("# 转换模板：扁平模板示例");
        assertThat(md).contains("JSON");
        assertThat(md).contains("XML");
        assertThat(md).contains("BIZ_001");
        assertThat(md).contains("## 字段映射");
        assertThat(md).contains("userId");
        assertThat(md).contains("uid");
        assertThat(md).contains("1.0.0");
        assertThat(md).contains("## 字段加工");
        assertThat(md).contains("TRIM");
    }

    @Test
    void encodeTemplate_空规则_显示无() {
        ConvertTemplate t = new ConvertTemplate();
        t.setName("空模板");
        t.setSrcFormat("JSON");
        t.setTargetFormat("JSON");
        t.setMappingRule("[]");
        t.setProcessRule("[]");

        String md = codec.encodeTemplate(t);
        assertThat(md).contains("_无_");
    }
}
