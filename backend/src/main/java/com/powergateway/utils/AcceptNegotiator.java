package com.powergateway.utils;

import org.springframework.http.HttpHeaders;
import javax.servlet.http.HttpServletRequest;

/** FN-06 Accept/query/config 三级优先级协商响应格式。 */
public final class AcceptNegotiator {

    private AcceptNegotiator() {}

    public static FormatType negotiate(HttpServletRequest req,
                                       String queryParamFormat,
                                       String configDefault) {
        if (queryParamFormat != null && !queryParamFormat.isEmpty()) {
            return FormatType.parse(queryParamFormat);
        }
        String accept = req.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && !accept.isEmpty() && !"*/*".equals(accept.trim())) {
            if (accept.contains("application/xml") || accept.contains("text/xml")) return FormatType.XML;
            if (accept.contains("text/csv")) return FormatType.CSV;
            if (accept.contains("application/x-www-form-urlencoded")) return FormatType.FORM_DATA;
            if (accept.contains("application/json")) return FormatType.JSON;
        }
        if (configDefault != null && !configDefault.isEmpty()) {
            return FormatType.parse(configDefault);
        }
        return FormatType.JSON;
    }
}
