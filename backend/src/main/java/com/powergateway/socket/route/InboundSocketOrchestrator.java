package com.powergateway.socket.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powergateway.aop.AuditContext;
import com.powergateway.aop.AuditContextHolder;
import com.powergateway.exception.BusinessException;
import com.powergateway.model.InterfaceConfig;
import com.powergateway.route.ChannelFunctionIdMapper;
import com.powergateway.route.FunctionIdRouteService;
import com.powergateway.service.InterfaceConfigService;
import com.powergateway.socket.inbound.SocketInboundContext;
import com.powergateway.socket.outbound.HttpOutboundExecutor;
import com.powergateway.socket.outbound.HttpOutboundRequest;
import com.powergateway.utils.FormatConverter;
import com.powergateway.utils.FormatType;
import io.netty.channel.Channel;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入站 Socket 编排器(v0.3.2 SOCK-5-C 核心 · Task 3)。
 *
 * <p><b>链路</b>(渠道 TCP XML → PG 入 → HTTP 出到联机 → 收 JSON 应答 → 转 XML → 从原 TCP 连接回写):</p>
 * <ol>
 *   <li>dom4j 解析 XML → 提 FunctionId(XPath 可配 · 默认 //FunctionId)</li>
 *   <li>走 v0.3.1 双层路由:{@link ChannelFunctionIdMapper} → PG 功能号 → {@link FunctionIdRouteService} → interfaceId</li>
 *   <li>加载 InterfaceConfig · verify type=INBOUND_SOCKET</li>
 *   <li>XML → Map(bizBody + bizHeader)</li>
 *   <li>{@link JsonSkeletonRenderer#wrapRequest} 按骨架构造 · 追加 {@code _originalFunctionId}</li>
 *   <li>{@link HttpOutboundExecutor#exec} 发到联机 · 附 X-Trace-Id + X-Original-Function-Id header</li>
 *   <li>联机 JSON 应答 → {@link JsonSkeletonRenderer#unwrapResponse} 提业务体</li>
 *   <li>Map → XML(简化响应结构 · &lt;response&gt; 根 + 各字段)</li>
 *   <li>从原 Channel 回写</li>
 * </ol>
 *
 * <p>各阶段日志带 traceId(v0.3.1 TraceIdFilter + AOP MDC 已就绪 · orchestrator 只需 log 时打印)。</p>
 */
@Service
public class InboundSocketOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InboundSocketOrchestrator.class);

    @Autowired private ChannelFunctionIdMapper channelMapper;
    @Autowired private FunctionIdRouteService routeService;
    @Autowired private InterfaceConfigService interfaceConfigService;
    @Autowired private FormatConverter formatConverter;
    @Autowired private HttpOutboundExecutor httpOutbound;
    @Autowired private ObjectMapper objectMapper;

    /**
     * 处理入站请求 → 编排完整链路 → 从 Channel 回写应答 XML。
     *
     * @param rawXml    渠道发来的 XML
     * @param channel   Netty Channel(用于回写)
     * @param charset   编解码字符集
     */
    public void handle(String rawXml, Channel channel, Charset charset) {
        String traceId = SocketInboundContext.currentTraceId();
        try {
            // 1. 提 FunctionId
            String channelFnId = extractFunctionId(rawXml);
            if (channelFnId == null || channelFnId.isEmpty()) {
                writeError(channel, charset, "功能号缺失(报文里未找到 FunctionId)", traceId);
                return;
            }

            // 2. 双层路由 · 复用 v0.3.1
            String pgFnId = channelMapper.map(channelFnId);
            Long interfaceId = routeService.lookup(pgFnId);
            if (interfaceId == null) {
                writeError(channel, charset, "未找到匹配接口 · PG 功能号:" + pgFnId, traceId);
                return;
            }

            // 3. 加载 config · verify type
            InterfaceConfig config = interfaceConfigService.getById(interfaceId);
            if (config == null || !"INBOUND_SOCKET".equals(config.getType())) {
                writeError(channel, charset, "接口 " + interfaceId + " 类型不是 INBOUND_SOCKET", traceId);
                return;
            }
            if (!"published".equals(config.getStatus())) {
                writeError(channel, charset, "接口未发布", traceId);
                return;
            }

            AuditContextHolder.set(new AuditContext()
                    .setInterfaceId(interfaceId)
                    .setOpType("INBOUND_SOCKET_EXEC")
                    .setTargetDb("inbound-socket")
                    .setTraceId(traceId));

            log.info("SOCK-5-C · orchestrator start · traceId={} · channelFnId={} · pgFnId={} · interfaceId={}",
                    traceId, channelFnId, pgFnId, interfaceId);

            // 4. XML → Map(整体 · 供 wrap 或 unwrap 提字段)
            Map<String, Object> xmlMap = formatConverter.parseToMap(rawXml, FormatType.XML);

            // 5. 反解 config_json.jsonSkeleton + outbound
            Map<String, Object> configMap = parseConfigJson(config.getConfigJson());
            JsonSkeletonConfig skeleton = JsonSkeletonConfig.fromMap(mapField(configMap, "jsonSkeleton"));
            HttpOutboundRequest outboundReq = HttpOutboundRequest.fromMap(mapField(configMap, "outbound"));

            // 从 xmlMap 提业务 body + head(bank 场景走 Body/request/bizBody · host 场景 xmlMap 就是扁平)
            Map<String, Object> bizBody = extractBizBody(xmlMap);
            Map<String, Object> bizHeader = extractBizHeader(xmlMap);

            Object requestJson = JsonSkeletonRenderer.wrapRequest(bizBody, bizHeader, skeleton);
            // 6. 追加 _originalFunctionId 透传下游联机(用户 Q1 明确)
            injectOriginalFunctionId(requestJson, channelFnId);

            // 7. HTTP 出站
            String respJsonStr = httpOutbound.exec(outboundReq, requestJson, traceId, channelFnId);

            // 8. JSON 应答解析 + unwrap
            Object respJson = objectMapper.readValue(respJsonStr, Object.class);
            Object unwrapped = JsonSkeletonRenderer.unwrapResponse(respJson, skeleton);

            // 9. Map → XML(简化 · <Response> 根 + 扁平字段)
            String respXml = buildResponseXml(unwrapped);

            // 10. 回写渠道
            channel.writeAndFlush(respXml.getBytes(charset));
            log.info("SOCK-5-C · orchestrator done · traceId={} · resp bytes={}", traceId, respXml.length());
        } catch (BusinessException e) {
            log.warn("SOCK-5-C · orchestrator 业务异常 · traceId={} · {}", traceId, e.getMessage());
            writeError(channel, charset, e.getMessage(), traceId);
        } catch (Exception e) {
            log.error("SOCK-5-C · orchestrator 系统异常 · traceId={}", traceId, e);
            writeError(channel, charset, "系统异常:" + e.getMessage(), traceId);
        } finally {
            channel.close();
        }
    }

    /**
     * 用 dom4j XPath 提取 FunctionId · 默认 //FunctionId(bank/host 通用)。
     * 若报文含多个(bank 场景 sysHeader.serviceCd + bizHeader.FunctionId)· 取第一个匹配。
     */
    public static String extractFunctionId(String xml) {
        if (xml == null || xml.trim().isEmpty()) return null;
        try {
            Document doc = DocumentHelper.parseText(xml.trim());
            return findElementText(doc.getRootElement(), "FunctionId");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 递归找嵌套元素里第一个匹配 name 的 text · 避免引入 jaxen 依赖(dom4j.selectNodes 依赖)。
     */
    @SuppressWarnings("unchecked")
    private static String findElementText(Element el, String name) {
        if (el == null) return null;
        if (name.equals(el.getName())) {
            String t = el.getTextTrim();
            return t == null ? "" : t;
        }
        for (Object child : el.elements()) {
            String v = findElementText((Element) child, name);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * 提业务 body:优先 //bizBody(bank 场景) · fallback xmlMap 自身(host 扁平)。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractBizBody(Map<String, Object> xmlMap) {
        Object nested = findByKey(xmlMap, "bizBody");
        if (nested instanceof Map) return (Map<String, Object>) nested;
        return xmlMap;
    }

    /**
     * 提 bizHeader:优先 //bizHeader · fallback null。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractBizHeader(Map<String, Object> xmlMap) {
        Object nested = findByKey(xmlMap, "bizHeader");
        if (nested instanceof Map) return (Map<String, Object>) nested;
        return null;
    }

    /** 递归找嵌套 map 里第一个匹配 key 的值 */
    @SuppressWarnings("unchecked")
    private static Object findByKey(Map<String, Object> map, String key) {
        if (map == null) return null;
        if (map.containsKey(key)) return map.get(key);
        for (Object v : map.values()) {
            if (v instanceof Map) {
                Object found = findByKey((Map<String, Object>) v, key);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 追加 _originalFunctionId 到 requestJson · bank/host 场景都支持 */
    @SuppressWarnings("unchecked")
    public static void injectOriginalFunctionId(Object requestJson, String channelFnId) {
        if (requestJson instanceof Map) {
            ((Map<String, Object>) requestJson).put("_originalFunctionId", channelFnId);
        } else if (requestJson instanceof List) {
            List<Object> list = (List<Object>) requestJson;
            if (!list.isEmpty() && list.get(0) instanceof Map) {
                ((Map<String, Object>) list.get(0)).put("_originalFunctionId", channelFnId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJson(String json) {
        if (json == null || json.isEmpty()) {
            throw new BusinessException("INBOUND_SOCKET 接口 config_json 为空");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException("INBOUND_SOCKET config_json 解析失败:" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapField(Map<String, Object> src, String key) {
        Object v = src.get(key);
        return v instanceof Map ? (Map<String, Object>) v : null;
    }

    /**
     * 简化应答 XML 构造:{@code <Response>} 根 + unwrapped 扁平字段。
     * 复杂 Transaction 根结构留后续 minor 补齐(用户可配 responseTemplate)。
     */
    @SuppressWarnings("unchecked")
    public static String buildResponseXml(Object unwrapped) {
        Document doc = DocumentHelper.createDocument();
        Element root = doc.addElement("Response");
        if (unwrapped instanceof Map) {
            appendMap(root, (Map<String, Object>) unwrapped);
        } else if (unwrapped instanceof List) {
            // list 场景 · 每条 <item>
            List<Object> list = (List<Object>) unwrapped;
            for (Object item : list) {
                Element itemEl = root.addElement("item");
                if (item instanceof Map) appendMap(itemEl, (Map<String, Object>) item);
                else itemEl.setText(item == null ? "" : item.toString());
            }
        } else {
            root.setText(unwrapped == null ? "" : unwrapped.toString());
        }
        return doc.asXML();
    }

    @SuppressWarnings("unchecked")
    private static void appendMap(Element parent, Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String tag = sanitize(e.getKey());
            Object v = e.getValue();
            Element el = parent.addElement(tag);
            if (v instanceof Map) {
                appendMap(el, (Map<String, Object>) v);
            } else if (v instanceof List) {
                for (Object item : (List<Object>) v) {
                    Element itemEl = el.addElement("item");
                    if (item instanceof Map) appendMap(itemEl, (Map<String, Object>) item);
                    else itemEl.setText(item == null ? "" : item.toString());
                }
            } else {
                el.setText(v == null ? "" : v.toString());
            }
        }
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (s.isEmpty()) return "field";
        if (Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    private void writeError(Channel channel, Charset charset, String message, String traceId) {
        String errXml = "<Response><Error><Message>" + escapeXml(message)
                + "</Message><TraceId>" + (traceId == null ? "" : traceId) + "</TraceId></Error></Response>";
        try {
            channel.writeAndFlush(errXml.getBytes(charset));
        } catch (Exception ignore) {
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
