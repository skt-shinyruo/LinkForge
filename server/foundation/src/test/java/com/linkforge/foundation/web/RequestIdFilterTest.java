package com.linkforge.foundation.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test
    void should_accept_safe_request_id_and_reflect_it() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "abc-123_DEF.456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, resp) -> seen.set(RequestId.get());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo("abc-123_DEF.456");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo("abc-123_DEF.456");
        assertThat(RequestId.get()).isNull();
    }

    @Test
    void should_trim_and_accept_safe_request_id() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "  abc-123  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, resp) -> seen.set(RequestId.get());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo("abc-123");
    }

    @Test
    void should_generate_new_request_id_when_header_is_too_long() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "a".repeat(100));
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, resp) -> seen.set(RequestId.get());

        filter.doFilter(request, response, chain);

        String rid = seen.get();
        assertThat(rid).isNotBlank();
        assertThat(rid).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(rid);
    }

    @Test
    void should_generate_new_request_id_when_header_contains_invalid_chars() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "abc 123\nx");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, resp) -> seen.set(RequestId.get());

        filter.doFilter(request, response, chain);

        String rid = seen.get();
        assertThat(rid).isNotBlank();
        assertThat(rid).matches("[0-9a-f]{32}");
        assertThat(response.getHeader(RequestIdFilter.HEADER_REQUEST_ID)).isEqualTo(rid);
    }
}

