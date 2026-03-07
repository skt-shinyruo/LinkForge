package com.linkforge.redirect.interfaces.web;

import com.linkforge.foundation.config.EdgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectClientIpResolverTest {

    @Test
    void should_ignore_forwarded_headers_when_remote_not_trusted() {
        EdgeProperties p = new EdgeProperties();
        p.setTrustedProxies(List.of("10.0.0.0/8"));

        RedirectClientIpResolver r = new RedirectClientIpResolver(p);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("1.1.1.1");
        req.addHeader("X-Real-IP", "9.9.9.9");
        req.addHeader("X-Forwarded-For", "9.9.9.9, 10.0.0.1");

        assertThat(r.resolveClientIp(req)).isEqualTo("1.1.1.1");
    }

    @Test
    void should_trust_x_real_ip_when_remote_trusted() {
        EdgeProperties p = new EdgeProperties();
        p.setTrustedProxies(List.of("10.0.0.0/8"));

        RedirectClientIpResolver r = new RedirectClientIpResolver(p);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.5");
        req.addHeader("X-Real-IP", "1.2.3.4");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.5");

        assertThat(r.resolveClientIp(req)).isEqualTo("1.2.3.4");
    }

    @Test
    void should_fallback_to_xff_chain_when_x_real_ip_missing() {
        EdgeProperties p = new EdgeProperties();
        p.setTrustedProxies(List.of("10.0.0.0/8"));

        RedirectClientIpResolver r = new RedirectClientIpResolver(p);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.9");

        assertThat(r.resolveClientIp(req)).isEqualTo("1.2.3.4");
    }
}
