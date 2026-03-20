package com.linkforge.app.security;

import com.linkforge.accounts.application.ApiKeyService;
import com.linkforge.accounts.application.AccountStatusService;
import com.linkforge.accounts.infrastructure.security.JwtService;
import com.linkforge.app.api.error.ApiErrorResponseWriter;
import com.linkforge.foundation.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = {
        SecurityConfigCsrfTest.TestAuthController.class,
        SecurityConfigCsrfTest.TestOpenApiController.class,
        SecurityConfigCsrfTest.TestApiController.class
})
@TestPropertySource(properties = {
        "app.security.jwt.cookie-enabled=true",
        "app.security.jwt.cookie-name=lf_token"
})
@ContextConfiguration(classes = {
        SecurityConfigCsrfTest.TestApp.class,
        SecurityConfigCsrfTest.TestAuthController.class,
        SecurityConfigCsrfTest.TestOpenApiController.class,
        SecurityConfigCsrfTest.TestApiController.class
})
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        SecurityConfigCsrfTest.TestSecurityBeans.class
})
class SecurityConfigCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private JwtService jwtService;

    @Test
    void csrf_should_not_be_ignored_for_non_bearer_authorization_header() throws Exception {
        assertThat(securityProperties.getJwt().isCookieEnabled()).isTrue();

        // Baseline: cookie+CSRF mode should reject POST without CSRF token.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Regression: a non-Bearer Authorization header must NOT bypass CSRF.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "foo")
                        .content("{\"email\":\"a@b.com\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrf_should_only_be_ignored_for_openapi_when_api_key_header_present() throws Exception {
        assertThat(securityProperties.getJwt().isCookieEnabled()).isTrue();

        when(apiKeyService.authenticate(anyString()))
                .thenReturn(new ApiKeyService.ApiKeyAuthResult(1L, 1L, 123L));

        // Without X-API-Key: OpenAPI chain is stateless and does not use CSRF; auth should fail as 401.
        mockMvc.perform(post("/api/v1/open/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // With X-API-Key: OpenAPI clients should not be blocked by CSRF in cookie mode.
        mockMvc.perform(post("/api/v1/open/links")
                        .header("X-API-Key", "lfk_123_secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void openapi_shouldRejectBearerJwt_evenIfJwtIsValid() throws Exception {
        assertThat(securityProperties.getJwt().isCookieEnabled()).isTrue();

        // Even with a valid Bearer JWT, OpenAPI routes must NOT accept JWT auth (API key only).
        when(jwtService.parseToken("good"))
                .thenReturn(new com.linkforge.foundation.security.AuthPrincipal(
                        1L,
                        1L,
                        "user@example.com",
                        Set.of("USER")
                ));

        mockMvc.perform(post("/api/v1/open/links")
                        .header("Authorization", "Bearer good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        // Defense-in-depth: OpenAPI chain should not parse JWT at all.
        verify(jwtService, never()).parseToken(anyString());
    }

    @Test
    void apiRoutes_shouldRejectApiKeyHeader() throws Exception {
        assertThat(securityProperties.getJwt().isCookieEnabled()).isTrue();

        // /api/v1/** must NOT accept OpenAPI API key auth.
        mockMvc.perform(get("/api/v1/links/ping")
                        .header("X-API-Key", "lfk_123_secret"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestAuthController {
        @PostMapping("/api/v1/auth/login")
        void login() {
            // no-op
        }
    }

    @RestController
    static class TestOpenApiController {
        @PostMapping("/api/v1/open/links")
        ResponseEntity<?> create() {
            // no-op
            return ResponseEntity.ok().build();
        }
    }

    @RestController
    static class TestApiController {
        @GetMapping("/api/v1/links/ping")
        ResponseEntity<?> ping() {
            return ResponseEntity.ok().build();
        }
    }

    @SpringBootConfiguration
    static class TestApp {
    }

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties p = new SecurityProperties();
            p.getJwt().setCookieEnabled(true);
            p.getJwt().setCookieName("lf_token");
            return p;
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        ApiKeyService apiKeyService() {
            return mock(ApiKeyService.class);
        }

        @Bean
        AccountStatusService accountStatusService() {
            return mock(AccountStatusService.class);
        }
    }
}
