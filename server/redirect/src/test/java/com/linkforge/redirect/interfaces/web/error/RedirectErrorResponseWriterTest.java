package com.linkforge.redirect.interfaces.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectErrorResponseWriterTest {

    @Test
    void write_should_set_no_store_cache_headers() throws Exception {
        RedirectErrorResponseWriter writer = new RedirectErrorResponseWriter(new ObjectMapper());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.ACCEPT, "application/json");

        MockHttpServletResponse resp = new MockHttpServletResponse();

        writer.write(req, resp, 429, RedirectErrorCode.TOO_MANY_REQUESTS, "too many");

        assertThat(resp.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }
}

