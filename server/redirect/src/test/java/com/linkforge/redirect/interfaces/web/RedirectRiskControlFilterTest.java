package com.linkforge.redirect.interfaces.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.foundation.config.AnalyticsProperties;
import com.linkforge.foundation.config.EdgeProperties;
import com.linkforge.foundation.web.VisitInfo;
import com.linkforge.redirect.application.risk.RedirectRiskControl;
import com.linkforge.redirect.interfaces.web.error.RedirectErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectRiskControlFilterTest {

    @Test
    void should_filter_r_path_even_with_context_path() {
        RedirectRiskControlFilter filter = new RedirectRiskControlFilter(null, null, null, null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/edge");
        req.setRequestURI("/edge/r/abc123");

        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void should_truncate_and_clean_tracking_params_values() throws Exception {
        EdgeProperties edge = new EdgeProperties();
        RedirectClientIpResolver ipResolver = new RedirectClientIpResolver(edge);
        RedirectRiskControl riskControl = new RedirectRiskControl(edge, (k, ttl) -> 0L);
        RedirectErrorResponseWriter writer = new RedirectErrorResponseWriter(new ObjectMapper());
        AnalyticsProperties analytics = new AnalyticsProperties();
        analytics.getEvents().setMaxTrackingValueLength(16);

        RedirectRiskControlFilter filter = new RedirectRiskControlFilter(ipResolver, riskControl, writer, analytics);

        String raw = "abc\tdef\n\rghi" + "x".repeat(100);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/r/abc123");
        req.setRemoteAddr("127.0.0.1");
        req.setParameter("utm_source", raw);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (r, s) -> {
            VisitInfo v = (VisitInfo) req.getAttribute(RedirectRiskControlFilter.ATTR_VISIT_INFO);
            assertThat(v).isNotNull();
            assertThat(v.trackingParams()).containsKey("utm_source");
            assertThat(v.trackingParams().get("utm_source")).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t");
            assertThat(v.trackingParams().get("utm_source").length()).isLessThanOrEqualTo(16);
        };

        filter.doFilter(req, resp, chain);
    }
}
