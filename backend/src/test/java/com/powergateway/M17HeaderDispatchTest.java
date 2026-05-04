package com.powergateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.powergateway.model.dto.HeaderConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("M1-7 报文头适配（CHG-002）")
class M17HeaderDispatchTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;
    private String token;
    private static final String B_URL = "http://mock-b-hdr/api/receive";

    @BeforeEach
    void setup() throws Exception {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andReturn();
        token = JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    private Long createTemplate() throws Exception {
        String body = "{\"name\":\"hdr-disp-" + System.currentTimeMillis() + "\",\"srcFormat\":\"JSON\",\"targetFormat\":\"JSON\",\"mappingRules\":[]}";
        MvcResult r = mockMvc.perform(post("/api/template/save")
                .header("satoken", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        return Long.valueOf(JsonPath.read(r.getResponse().getContentAsString(), "$.data").toString());
    }

    private String createChannel(String channelCode, HeaderConfig hc) throws Exception {
        Long tplId = createTemplate();
        Map<String, Object> body = new HashMap<>();
        body.put("channelCode", channelCode);
        body.put("channelName", "Header测试渠道");
        body.put("identifyField", "channel");
        body.put("templateId", tplId);
        if (hc != null) body.put("headerConfig", hc);
        mockMvc.perform(post("/api/channel/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        return channelCode;
    }

    private void createRoute(String channelCode, HeaderConfig hc) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("channelCode", channelCode);
        body.put("portAddress", B_URL);
        body.put("portMethod", "POST");
        body.put("timeout", 3000);
        body.put("retryCount", 1);
        if (hc != null) body.put("headerConfig", hc);
        mockMvc.perform(post("/api/port-route/save")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("dispatch 时出向请求携带渠道级别 Content-Type 和自定义 Header")
    void dispatch_channelHeaderConfig_injectedIntoForwardRequest() throws Exception {
        String code = "CH_HDR_DISP_" + System.currentTimeMillis();

        HeaderConfig channelHc = new HeaderConfig();
        channelHc.setContentType("application/json");
        Map<String, String> reqH = new HashMap<>();
        reqH.put("X-Channel-Tag", "chg002");
        channelHc.setRequestHeaders(reqH);

        createChannel(code, channelHc);
        createRoute(code, null);

        mockServer.expect(requestTo(B_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("application/json")))
                .andExpect(header("X-Channel-Tag", equalTo("chg002")))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        String message = "{\"channel\":\"" + code + "\",\"data\":\"test\"}";
        mockMvc.perform(post("/api/dispatch")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildDispatchBody(message))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockServer.verify();
    }

    @Test
    @DisplayName("端口路由 Header 覆盖渠道默认值：Content-Type 和同名 key 以路由配置为准")
    void dispatch_routeHeaderOverridesChannelHeader() throws Exception {
        String code = "CH_HDR_OVR_" + System.currentTimeMillis();

        HeaderConfig channelHc = new HeaderConfig();
        channelHc.setContentType("text/plain");
        Map<String, String> chReqH = new HashMap<>();
        chReqH.put("X-Common", "from-channel");
        chReqH.put("X-Channel-Only", "yes");
        channelHc.setRequestHeaders(chReqH);

        HeaderConfig routeHc = new HeaderConfig();
        routeHc.setContentType("application/xml");
        Map<String, String> rtReqH = new HashMap<>();
        rtReqH.put("X-Common", "from-route");
        routeHc.setRequestHeaders(rtReqH);

        createChannel(code, channelHc);
        createRoute(code, routeHc);

        mockServer.expect(requestTo(B_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("application/xml")))
                .andExpect(header("X-Common", equalTo("from-route")))
                .andExpect(header("X-Channel-Only", equalTo("yes")))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String message = "{\"channel\":\"" + code + "\"}";
        mockMvc.perform(post("/api/dispatch")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildDispatchBody(message))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockServer.verify();
    }

    @Test
    @DisplayName("不配置 headerConfig 时，dispatch 仍正常运行（向后兼容）")
    void dispatch_noHeaderConfig_backwardCompatible() throws Exception {
        String code = "CH_HDR_COMPAT_" + System.currentTimeMillis();
        createChannel(code, null);
        createRoute(code, null);

        mockServer.expect(requestTo(B_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("compat-ok", MediaType.TEXT_PLAIN));

        String message = "{\"channel\":\"" + code + "\"}";
        mockMvc.perform(post("/api/dispatch")
                .header("satoken", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildDispatchBody(message))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.response").value("compat-ok"));
    }

    private Map<String, Object> buildDispatchBody(String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("srcFormat", "JSON");
        return body;
    }
}
