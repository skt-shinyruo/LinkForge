package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.AuthService;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void login_shouldNotReturnTokenInBody_whenCookieModeEnabled() {
        AuthService authService = mock(AuthService.class);

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setCookieEnabled(true);
        securityProperties.getJwt().setCookieName("lf_token");
        securityProperties.getJwt().setCookieSameSite("Lax");
        securityProperties.getJwt().setCookieSecure(false);
        securityProperties.getJwt().setTtlSeconds(3600);

        AuthController controller = new AuthController(authService, securityProperties);

        AuthPrincipal principal = new AuthPrincipal(1L, 2L, "u@example.com", Set.of("USER"));
        when(authService.login("u@example.com", "password123"))
                .thenReturn(new AuthService.AuthResult("jwt-token", principal));

        MockHttpServletResponse response = new MockHttpServletResponse();
        var out = controller.login(new AuthController.LoginRequest("u@example.com", "password123"), response);

        assertThat(out).isNotNull();
        assertThat(out.getData()).isNotNull();
        assertThat(out.getData().token()).isNull();

        assertThat(response.getHeader("Set-Cookie"))
                .contains("lf_token=")
                .contains("jwt-token")
                .contains("HttpOnly");
    }

    @Test
    void register_shouldNotReturnTokenInBody_whenCookieModeEnabled() {
        AuthService authService = mock(AuthService.class);

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getJwt().setCookieEnabled(true);
        securityProperties.getJwt().setCookieName("lf_token");
        securityProperties.getJwt().setCookieSameSite("Lax");
        securityProperties.getJwt().setCookieSecure(false);
        securityProperties.getJwt().setTtlSeconds(3600);

        AuthController controller = new AuthController(authService, securityProperties);

        AuthPrincipal principal = new AuthPrincipal(1L, 2L, "u@example.com", Set.of("TENANT_ADMIN"));
        when(authService.register("t1", "u@example.com", "password123"))
                .thenReturn(new AuthService.AuthResult("jwt-token", principal));

        MockHttpServletResponse response = new MockHttpServletResponse();
        var out = controller.register(
                new AuthController.RegisterRequest("t1", "u@example.com", "password123"),
                response
        );

        assertThat(out).isNotNull();
        assertThat(out.getData()).isNotNull();
        assertThat(out.getData().token()).isNull();

        assertThat(response.getHeader("Set-Cookie"))
                .contains("lf_token=")
                .contains("jwt-token")
                .contains("HttpOnly");
    }
}
