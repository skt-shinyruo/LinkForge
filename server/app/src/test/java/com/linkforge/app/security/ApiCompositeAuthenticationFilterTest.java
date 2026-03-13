package com.linkforge.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiCompositeAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openApiKeyDisabled_shouldReturnForbidden() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        ApiCompositeAuthenticationFilter filter = new ApiCompositeAuthenticationFilter(
                jwtService,
                apiKeyService,
                writer,
                securityProperties
        );

        when(apiKeyService.authenticate("lfk_123_secret"))
                .thenThrow(new ApiKeyService.ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).contains("\"code\":40310");
    }

    @Test
    void openApiRoute_withContextPath_shouldStillRequireApiKey() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        ApiCompositeAuthenticationFilter filter = new ApiCompositeAuthenticationFilter(
                jwtService,
                apiKeyService,
                writer,
                securityProperties
        );

        when(apiKeyService.authenticate("lfk_123_secret"))
                .thenThrow(new ApiKeyService.ApiKeyAuthException(OpenApiErrorCode.API_KEY_DISABLED));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x/api/v1/open/ping");
        req.setContextPath("/x");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentAsString()).contains("\"code\":40310");
    }

    @Test
    void invalidCookieJwt_shouldNotShortCircuitPermitAllEndpoints() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setCookieEnabled(true);
        securityProperties.getJwt().setCookieName("lf_token");

        ApiCompositeAuthenticationFilter filter = new ApiCompositeAuthenticationFilter(
                jwtService,
                apiKeyService,
                writer,
                securityProperties
        );

        when(jwtService.parseToken("bad")).thenThrow(new RuntimeException("bad token"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setCookies(new Cookie("lf_token", "bad"));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> {
            chainCalled.set(true);
            ((HttpServletResponse) s).setStatus(204);
        };

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isTrue();
        assertThat(resp.getStatus()).isEqualTo(204);
        assertThat(resp.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(v -> v != null && v.startsWith("lf_token=") && v.contains("Max-Age=0"));
    }

    @Test
    void invalidBearerJwt_shouldReturnUnauthorized() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        ApiCompositeAuthenticationFilter filter = new ApiCompositeAuthenticationFilter(
                jwtService,
                apiKeyService,
                writer,
                securityProperties
        );

        when(jwtService.parseToken("bad")).thenThrow(new RuntimeException("bad token"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).startsWith("application/json");
    }

    @Test
    void oversizedBearerJwt_shouldReturnUnauthorized_withoutParsing() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        ApiCompositeAuthenticationFilter filter = new ApiCompositeAuthenticationFilter(
                jwtService,
                apiKeyService,
                writer,
                securityProperties
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.addHeader("Authorization", "Bearer " + "a".repeat(5000));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        verify(jwtService, never()).parseToken(anyString());
    }

    @Test
    void oversizedCookieJwt_shouldClearCookie_andContinueChain_withoutParsing() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setCookieEnabled(true);
        securityProperties.getJwt().setCookieName("lf_token");

        ApiCompositeAuthenticationFilter filter = new ApiCompositeAuthenticationFilter(
                jwtService,
                apiKeyService,
                writer,
                securityProperties
        );

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setCookies(new Cookie("lf_token", "a".repeat(5000)));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> {
            chainCalled.set(true);
            ((HttpServletResponse) s).setStatus(204);
        };

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isTrue();
        assertThat(resp.getStatus()).isEqualTo(204);
        assertThat(resp.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(v -> v != null && v.startsWith("lf_token=") && v.contains("Max-Age=0"));
        verify(jwtService, never()).parseToken(anyString());
    }
}
