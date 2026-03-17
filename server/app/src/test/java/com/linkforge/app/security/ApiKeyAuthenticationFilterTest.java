package com.linkforge.app.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.accounts.application.AccountStatusService;
import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.contract.accounts.AccountsErrorCode;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.openapi.OpenApiErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void openApiKeyDisabled_shouldReturnForbidden() throws Exception {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        AccountStatusService accountStatusService = mock(AccountStatusService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
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
    void openApi_withoutApiKey_shouldReturnUnauthorized() throws Exception {
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        AccountStatusService accountStatusService = mock(AccountStatusService.class);
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
        ApiKeyService apiKeyService = mock(ApiKeyService.class);
        AccountStatusService accountStatusService = mock(AccountStatusService.class);
        ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(
                apiKeyService,
                accountStatusService,
                writer
        );

        when(apiKeyService.authenticate("lfk_123_secret"))
                .thenReturn(new ApiKeyService.ApiKeyAuthResult(1L, 123L));
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
}
