package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.model.dto.HeaderConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M1-4 渠道报文头配置（CHG-002）")
class M14ChannelHeaderTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    private Long createTemplate() throws Exception {
        String body = "{\"name\":\"hdr-tpl-" + System.currentTimeMillis() + "\",\"srcFormat\":\"JSON\",\"targetFormat\":\"XML\",\"mappingRules\":[]}";
        MvcResult r = mockMvc.perform(post("/api/template/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return Long.valueOf(JsonPath.read(r.getResponse().getContentAsString(), "$.data").toString());
    }

    @Test
    @DisplayName("保存渠道时携带 headerConfig，查询列表后 headerConfig 字段不丢失")
    void saveChannel_withHeaderConfig_persistedAndReturned() throws Exception {
        Long tplId = createTemplate();
        HeaderConfig hc = new HeaderConfig();
        hc.setContentType("application/json");
        hc.setCharset("GBK");
        Map<String, String> reqH = new HashMap<>();
        reqH.put("X-Channel-Id", "CH_TEST");
        hc.setRequestHeaders(reqH);

        Map<String, Object> body = new HashMap<>();
        body.put("channelCode", "CH_HDR_" + System.currentTimeMillis());
        body.put("channelName", "Header测试渠道");
        body.put("identifyField", "channel");
        body.put("templateId", tplId);
        body.put("headerConfig", hc);

        MvcResult saveResult = mockMvc.perform(post("/api/channel/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        Long channelId = Long.valueOf(
                JsonPath.read(saveResult.getResponse().getContentAsString(), "$.data").toString());

        MvcResult listResult = mockMvc.perform(get("/api/channel/list")
                .header("satoken", token))
                .andExpect(status().isOk()).andReturn();

        String listJson = listResult.getResponse().getContentAsString();
        // 用 contains 检查 JSON 字符串里有期望内容
        assertTrue(listJson.contains("\"contentType\":\"application/json\""),
                "listJson should contain contentType");
        assertTrue(listJson.contains("\"charset\":\"GBK\""),
                "listJson should contain charset GBK");
    }

    @Test
    @DisplayName("保存渠道时不传 headerConfig，向后兼容")
    void saveChannel_withoutHeaderConfig_noError() throws Exception {
        Long tplId = createTemplate();
        Map<String, Object> body = new HashMap<>();
        body.put("channelCode", "CH_NO_HDR_" + System.currentTimeMillis());
        body.put("channelName", "无Header渠道");
        body.put("identifyField", "channel");
        body.put("templateId", tplId);

        mockMvc.perform(post("/api/channel/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
