package com.linkforge.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.foundation.security.AccountStatusVerifier;
import com.linkforge.foundation.security.ApiKeyAuthenticationDetails;
import com.linkforge.foundation.security.ApiKeyAuthenticationException;
import com.linkforge.foundation.security.ApiKeyAuthenticationFailure;
import com.linkforge.foundation.security.ApiKeyAuthenticationResult;
import com.linkforge.foundation.security.ApiKeyAuthenticator;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openApiKeyDisabled_shouldReturnForbidden() throws Exception {
        ApiKeyAuthenticator apiKeyService = mock(ApiKeyAuthenticator.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        when(apiKeyService.authenticateApiKey("lfk_123_secret"))
                .thenThrow(new ApiKeyAuthenticationException(ApiKeyAuthenticationFailure.DISABLED));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).contains("\"code\":40310");
    }

    @Test
    void openApi_withoutApiKey_shouldReturnUnauthorized() throws Exception {
        ApiKeyAuthenticator apiKeyService = mock(ApiKeyAuthenticator.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).contains("\"code\":40110");
    }

    @Test
    void openApi_withTenantDisabled_shouldReturnForbidden() throws Exception {
        ApiKeyAuthenticator apiKeyService = mock(ApiKeyAuthenticator.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        when(apiKeyService.authenticateApiKey("lfk_123_secret"))
                .thenReturn(new ApiKeyAuthenticationResult(1L, 1L, 123L));
        doThrow(new BusinessException(AccountsErrorCode.TENANT_DISABLED))
                .when(accountStatusService)
                .requireActiveTenant(1L);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(403);
        assertThat(resp.getContentType()).startsWith("application/json");
        assertThat(resp.getContentAsString()).contains("\"code\":40301");
    }

    @Test
    void openApi_withValidApiKey_shouldStoreApiKeyMetadataInAuthenticationDetails() throws Exception {
        ApiKeyAuthenticator apiKeyService = mock(ApiKeyAuthenticator.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        when(apiKeyService.authenticateApiKey("lfk_123_secret"))
                .thenReturn(new ApiKeyAuthenticationResult(1L, 2001L, 123L));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOfSatisfying(AuthPrincipal.class, principal ->
                        assertThat(principal.getTenantId()).isEqualTo(1L));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isEqualTo(new ApiKeyAuthenticationDetails(123L, 2001L));
    }

    @Test
    void openApi_withNonApiKeyAuthenticationInContext_shouldStillValidateApiKey() throws Exception {
        ApiKeyAuthenticator apiKeyService = mock(ApiKeyAuthenticator.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        AuthPrincipal existingPrincipal = new AuthPrincipal(99L, 77L, "user@example.com", Set.of("USER"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                existingPrincipal,
                "N/A",
                Set.of()
        ));
        when(apiKeyService.authenticateApiKey("lfk_123_secret"))
                .thenReturn(new ApiKeyAuthenticationResult(1L, 2001L, 123L));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isEqualTo(new ApiKeyAuthenticationDetails(123L, 2001L));
        verify(apiKeyService).authenticateApiKey("lfk_123_secret");
    }

    @Test
    void openApi_withCurrentApiKeyAuthenticationInContext_shouldStillValidateApiKey() throws Exception {
        ApiKeyAuthenticator apiKeyService = mock(ApiKeyAuthenticator.class);
        AccountStatusVerifier accountStatusService = mock(AccountStatusVerifier.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        AuthPrincipal principal = new AuthPrincipal(0L, 1L, null, Set.of("OPENAPI"));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                Set.of()
        );
        authentication.setDetails(new ApiKeyAuthenticationDetails(123L, 2001L));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(apiKeyService.authenticateApiKey("lfk_123_secret"))
                .thenReturn(new ApiKeyAuthenticationResult(1L, 2001L, 123L));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/open/ping");
        req.addHeader("X-API-Key", "lfk_123_secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        assertThat(resp.getStatus()).isEqualTo(200);
        verify(apiKeyService).authenticateApiKey("lfk_123_secret");
    }
}
