package com.powergateway;

import com.powergateway.utils.AcceptNegotiator;
import com.powergateway.utils.FormatType;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@DisplayName("FN-06 AcceptNegotiator 五种优先级")
class FN06AcceptNegotiatorTest {

    private MockHttpServletRequest req() { return new MockHttpServletRequest(); }

    @Test
    void query参数优先级最高() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "application/xml");
        assertEquals(FormatType.CSV,
            AcceptNegotiator.negotiate(r, "csv", "XML"));
    }

    @Test
    void accept头次之_xml() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "application/xml");
        assertEquals(FormatType.XML, AcceptNegotiator.negotiate(r, null, "CSV"));
    }

    @Test
    void accept头_textCsv() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "text/csv");
        assertEquals(FormatType.CSV, AcceptNegotiator.negotiate(r, null, null));
    }

    @Test
    void accept头_formUrlencoded() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "application/x-www-form-urlencoded");
        assertEquals(FormatType.FORM_DATA, AcceptNegotiator.negotiate(r, null, null));
    }

    @Test
    void 无query与accept_使用config默认() {
        assertEquals(FormatType.XML, AcceptNegotiator.negotiate(req(), null, "XML"));
    }

    @Test
    void 全部缺失_兜底JSON() {
        assertEquals(FormatType.JSON, AcceptNegotiator.negotiate(req(), null, null));
    }

    @Test
    void acceptStar_兜底JSON() {
        MockHttpServletRequest r = req();
        r.addHeader("Accept", "*/*");
        assertEquals(FormatType.JSON, AcceptNegotiator.negotiate(r, null, null));
    }
}
