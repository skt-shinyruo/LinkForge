package com.linkforge.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.foundation.security.JwtPrincipalVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void invalidCookieJwt_shouldNotShortCircuitPermitAllEndpoints() throws Exception {
        JwtPrincipalVerifier jwtService = mock(JwtPrincipalVerifier.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setCookieEnabled(true);
        securityProperties.getJwt().setCookieName("lf_token");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
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
        JwtPrincipalVerifier jwtService = mock(JwtPrincipalVerifier.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        SecurityProperties securityProperties = new SecurityProperties();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
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
        JwtPrincipalVerifier jwtService = mock(JwtPrincipalVerifier.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
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
        JwtPrincipalVerifier jwtService = mock(JwtPrincipalVerifier.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setCookieEnabled(true);
        securityProperties.getJwt().setCookieName("lf_token");

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
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

    @Test
    void disabledUser_shouldReturnForbidden() throws Exception {
        JwtPrincipalVerifier jwtService = mock(JwtPrincipalVerifier.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
                writer,
                securityProperties
        );

        when(jwtService.parseToken("good"))
                .thenReturn(new AuthPrincipal(1L, 1L, "user@example.com", Set.of("USER"), 0));
        doThrow(new BusinessException(AccountsErrorCode.USER_DISABLED))
                .when(accountStatusService)
                .requireActiveUserAndTenant(1L, 1L, 0);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isFalse();
        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).contains("\"code\":40302");
    }

    @Test
    void jwt_filter_should_parse_claims_once_and_delegate_user_state_validation_to_account_status_service() throws Exception {
        JwtPrincipalVerifier jwtService = mock(JwtPrincipalVerifier.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());
        SecurityProperties securityProperties = new SecurityProperties();

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                jwtService,
                accountStatusService,
                writer,
                securityProperties
        );

        when(jwtService.parseToken("good"))
                .thenReturn(new AuthPrincipal(1L, 1L, "user@example.com", Set.of("USER"), 7));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.addHeader("Authorization", "Bearer good");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> {
            chainCalled.set(true);
            ((HttpServletResponse) s).setStatus(204);
        };

        filter.doFilter(req, resp, chain);

        assertThat(chainCalled.get()).isTrue();
        verify(jwtService).parseToken("good");
        verify(accountStatusService).requireActiveUserAndTenant(1L, 1L, 7);
    }
}
