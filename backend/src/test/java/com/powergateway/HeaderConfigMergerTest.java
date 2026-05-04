package com.powergateway;

import com.powergateway.model.dto.HeaderConfig;
import com.powergateway.utils.HeaderConfigMerger;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class HeaderConfigMergerTest {

    @Test
    void merge_bothNull_returnsEmptyConfig() {
        HeaderConfig result = HeaderConfigMerger.merge(null, null);
        assertNotNull(result);
        assertNull(result.getContentType());
        assertNull(result.getCharset());
        assertNull(result.getRequestHeaders());
        assertNull(result.getResponseHeaders());
    }

    @Test
    void merge_channelOnly_returnsChannelValues() {
        HeaderConfig channel = new HeaderConfig();
        channel.setContentType("application/json");
        channel.setCharset("GBK");
        Map<String, String> chHeaders = new HashMap<>();
        chHeaders.put("X-Channel", "ch1");
        channel.setRequestHeaders(chHeaders);

        HeaderConfig result = HeaderConfigMerger.merge(channel, null);

        assertEquals("application/json", result.getContentType());
        assertEquals("GBK", result.getCharset());
        assertEquals("ch1", result.getRequestHeaders().get("X-Channel"));
    }

    @Test
    void merge_routeOverridesContentType() {
        HeaderConfig channel = new HeaderConfig();
        channel.setContentType("application/json");
        channel.setCharset("GBK");

        HeaderConfig route = new HeaderConfig();
        route.setContentType("application/xml");
        // charset not set in route → channel value wins

        HeaderConfig result = HeaderConfigMerger.merge(channel, route);

        assertEquals("application/xml", result.getContentType()); // route wins
        assertEquals("GBK", result.getCharset());                 // channel fallback
    }

    @Test
    void merge_requestHeaders_routeKeyOverridesChannelKey() {
        HeaderConfig channel = new HeaderConfig();
        Map<String, String> channelHeaders = new HashMap<>();
        channelHeaders.put("X-Common", "from-channel");
        channelHeaders.put("X-Channel-Only", "yes");
        channel.setRequestHeaders(channelHeaders);

        HeaderConfig route = new HeaderConfig();
        Map<String, String> routeHeaders = new HashMap<>();
        routeHeaders.put("X-Common", "from-route");
        routeHeaders.put("X-Route-Only", "yes");
        route.setRequestHeaders(routeHeaders);

        HeaderConfig result = HeaderConfigMerger.merge(channel, route);

        assertEquals("from-route", result.getRequestHeaders().get("X-Common"));     // route wins
        assertEquals("yes", result.getRequestHeaders().get("X-Channel-Only"));      // channel retained
        assertEquals("yes", result.getRequestHeaders().get("X-Route-Only"));        // route added
    }

    @Test
    void merge_responseHeaders_mergedCorrectly() {
        HeaderConfig channel = new HeaderConfig();
        Map<String, String> chRespHeaders = new HashMap<>();
        chRespHeaders.put("Content-Type", "application/json");
        channel.setResponseHeaders(chRespHeaders);

        HeaderConfig route = new HeaderConfig();
        Map<String, String> rtRespHeaders = new HashMap<>();
        rtRespHeaders.put("X-Trace-Id", "abc");
        route.setResponseHeaders(rtRespHeaders);

        HeaderConfig result = HeaderConfigMerger.merge(channel, route);

        assertEquals("application/json", result.getResponseHeaders().get("Content-Type"));
        assertEquals("abc", result.getResponseHeaders().get("X-Trace-Id"));
    }

    @Test
    void merge_routeNullCharset_channelCharsetUsed() {
        HeaderConfig channel = new HeaderConfig();
        channel.setCharset("ISO-8859-1");

        HeaderConfig route = new HeaderConfig();
        // charset is null

        assertEquals("ISO-8859-1", HeaderConfigMerger.merge(channel, route).getCharset());
    }

    @Test
    void merge_routeEmptyHeaders_channelHeadersRetained() {
        HeaderConfig channel = new HeaderConfig();
        Map<String, String> keepHeaders = new HashMap<>();
        keepHeaders.put("X-Keep", "me");
        channel.setRequestHeaders(keepHeaders);

        HeaderConfig route = new HeaderConfig();
        // requestHeaders is null

        assertEquals("me", HeaderConfigMerger.merge(channel, route).getRequestHeaders().get("X-Keep"));
    }
}
