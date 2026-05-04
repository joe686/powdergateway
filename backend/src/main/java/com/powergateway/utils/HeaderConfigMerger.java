package com.powergateway.utils;

import com.powergateway.model.dto.HeaderConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 两级报文头配置合并工具（CHG-002）。
 * 优先级：port_route 配置 > channel_config 配置。
 */
public class HeaderConfigMerger {

    private HeaderConfigMerger() {}

    /**
     * 合并渠道级别（channel）和端口路由级别（route）的报文头配置。
     * <ul>
     *   <li>contentType / charset：route 非 null 时覆盖，否则取 channel 值</li>
     *   <li>requestHeaders / responseHeaders：先取 channel 的 map，再逐键 putAll route 的 map</li>
     * </ul>
     *
     * @param channel 渠道级别配置，可为 null
     * @param route   端口路由级别配置，可为 null
     * @return 合并后的 HeaderConfig，永远不为 null
     */
    public static HeaderConfig merge(HeaderConfig channel, HeaderConfig route) {
        HeaderConfig result = new HeaderConfig();

        // contentType：route 优先
        result.setContentType(
                route != null && route.getContentType() != null
                        ? route.getContentType()
                        : (channel != null ? channel.getContentType() : null)
        );

        // charset：route 优先
        result.setCharset(
                route != null && route.getCharset() != null
                        ? route.getCharset()
                        : (channel != null ? channel.getCharset() : null)
        );

        // requestHeaders：合并，route 键值覆盖 channel 同名键
        result.setRequestHeaders(mergeMaps(
                channel != null ? channel.getRequestHeaders() : null,
                route != null ? route.getRequestHeaders() : null
        ));

        // responseHeaders：合并，route 键值覆盖 channel 同名键
        result.setResponseHeaders(mergeMaps(
                channel != null ? channel.getResponseHeaders() : null,
                route != null ? route.getResponseHeaders() : null
        ));

        return result;
    }

    private static Map<String, String> mergeMaps(Map<String, String> base, Map<String, String> override) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) merged.putAll(base);
        if (override != null) merged.putAll(override);
        return merged.isEmpty() ? null : merged;
    }
}
