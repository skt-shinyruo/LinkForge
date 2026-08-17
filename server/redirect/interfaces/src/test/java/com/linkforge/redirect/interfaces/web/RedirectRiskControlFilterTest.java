package com.linkforge.redirect.interfaces.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        RedirectRiskControlFilter filter = new RedirectRiskControlFilter(null, null, null);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/edge");
        req.setRequestURI("/edge/r/abc123");

        assertThat(filter.shouldNotFilter(req)).isFalse();
    }

    @Test
    void should_bound_user_agent_before_passing_context() throws Exception {
        EdgeProperties edge = new EdgeProperties();
        RedirectClientIpResolver ipResolver = new RedirectClientIpResolver(edge);
        RedirectRiskControl riskControl = new RedirectRiskControl(edge, (k, ttl) -> 0L);
        RedirectErrorResponseWriter writer = new RedirectErrorResponseWriter(new ObjectMapper());
        RedirectRiskControlFilter filter = new RedirectRiskControlFilter(ipResolver, riskControl, writer);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/r/abc123");
        req.setRemoteAddr("127.0.0.1");
        req.addHeader("User-Agent", "x".repeat(600));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        FilterChain chain = (r, s) -> {
            VisitInfo v = (VisitInfo) req.getAttribute(RedirectRiskControlFilter.ATTR_VISIT_INFO);
            assertThat(v).isNotNull();
            assertThat(v.userAgent()).hasSize(512);
        };

        filter.doFilter(req, resp, chain);
    }
}
