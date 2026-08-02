package com.powergateway.socket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SOCKET 接口执行编排(v0.3.0 SOCK-1 · Task 6)。
 *
 * <p>链路:反解 config_json.socket → 校验 connectionMode → 渲染 requestTemplate → SocketClient.send
 * → parseXml → flattenMap → SocketExecResponse。</p>
 *
 * <p>由 {@link com.powergateway.controller.ExecController#dispatchByType} 在 {@code type=SOCKET} 分支调用。</p>
 */
@Service
public class SocketExecutor {

    private final SocketClient client;
    private final FormatConverter formatConverter;
    private final ObjectMapper objectMapper;

    @Autowired
    public SocketExecutor(FormatConverter formatConverter, ObjectMapper objectMapper) {
        this(new SocketClient(), formatConverter, objectMapper);
    }

    /** 测试用 · 允许注入 mock SocketClient */
    public SocketExecutor(SocketClient client, FormatConverter formatConverter, ObjectMapper objectMapper) {
        this.client = client;
        this.formatConverter = formatConverter;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 SOCKET 接口:发送请求 · 收 XML 应答 · 扁平化 · 返回响应。
     *
     * @param config 接口配置 · type=SOCKET · config_json 含 socket 段
     * @param params exec 请求 params(用于渲染 requestTemplate 的 {name} 占位)
     * @return SocketExecResponse(rawXml + flattened + latencyMs)
     */
    public SocketExecResponse execute(InterfaceConfig config, Map<String, Object> params) {
        SocketExecRequest req = parseRequest(config);
        SocketClient.checkConnectionMode(req.getConnectionMode());
        String requestXml = renderTemplate(req.getRequestTemplate(), params == null ? Collections.emptyMap() : params);

        long start = System.currentTimeMillis();
        byte[] respBytes = client.send(
                req.getIp(), req.getPort(),
                requestXml.getBytes(req.getCharset()),
                req.getFraming(),
                req.getCharset(),
                req.getConnTimeoutMs(),
                req.getReadTimeoutMs()
        );
        long latency = System.currentTimeMillis() - start;

        String rawResp = new String(respBytes, req.getCharset());
        Map<String, Object> respMap = formatConverter.parseToMap(rawResp, FormatType.XML);
        Map<String, String> flat = FormatConverter.flattenMap(respMap, req.getResponseFlattenPrefix());
        return new SocketExecResponse(rawResp, flat, latency);
    }

    /**
     * 从 config_json 反解 socket 段(先反序列化 JSON 字符串到 Map · 取 socket 子对象)。
     */
    @SuppressWarnings("unchecked")
    SocketExecRequest parseRequest(InterfaceConfig config) {
        if (config.getConfigJson() == null || config.getConfigJson().isEmpty()) {
            throw new BusinessException("SOCKET 接口 config_json 为空");
        }
        Map<String, Object> configMap;
        try {
            configMap = objectMapper.readValue(config.getConfigJson(),
                    new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("SOCKET 接口 config_json 解析失败:" + e.getMessage());
        }
        Object socketObj = configMap.get("socket");
        if (!(socketObj instanceof Map)) {
            throw new BusinessException("SOCKET 接口 config_json 缺 socket 段");
        }
        return SocketExecRequest.fromMap((Map<String, Object>) socketObj);
    }

    /**
     * 简单占位替换:{name} → params[name].toString。null 值替换为空串。
     * public 便于单元测试直接验证。
     */
    public static String renderTemplate(String template, Map<String, Object> params) {
        if (template == null || params == null || params.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String placeholder = "{" + e.getKey() + "}";
            String value = e.getValue() == null ? "" : e.getValue().toString();
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
