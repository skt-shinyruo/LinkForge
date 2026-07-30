package com.linkforge.foundation.runtime.web;

import com.linkforge.foundation.web.RequestId;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void should_clear_mdc_after_filter_chain_completes() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, resp) -> seen.set(MDC.get("requestId"));

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo("trace-1");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void should_clear_request_state_when_downstream_filter_throws() {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_REQUEST_ID, "trace-2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, resp) -> {
            throw new IllegalStateException("downstream failed");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("downstream failed");
        assertThat(RequestId.get()).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }
}
