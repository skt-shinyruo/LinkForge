package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.AuthResult;
import com.linkforge.accounts.application.AuthService;
import com.linkforge.contract.api.ApiResponse;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import com.linkforge.foundation.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void register_shouldRejectWhenSelfRegistrationDisabled() {
        AuthService authService = mock(AuthService.class);
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setRegistrationEnabled(false);
        AuthController controller = new AuthController(authService, securityProperties);

        assertThatThrownBy(() -> controller.register(
                new AuthController.RegisterRequest("tenant", "owner@example.com", "password123"),
                mock(HttpServletResponse.class)
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verifyNoInteractions(authService);
    }

    @Test
    void login_shouldExposeTokenInBearerModeWithoutWritingCookie() {
        AuthService authService = mock(AuthService.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthPrincipal principal = principal();
        when(authService.login("owner@example.com", "password123"))
                .thenReturn(new AuthResult("jwt-token", principal));
        AuthController controller = new AuthController(authService, new SecurityProperties());

        ApiResponse<AuthController.AuthResponse> actual = controller.login(
                new AuthController.LoginRequest("owner@example.com", "password123"),
                response
        );

        assertThat(actual.getData().token()).isEqualTo("jwt-token");
        assertThat(actual.getData().user())
                .extracting(AuthController.UserDto::id, AuthController.UserDto::tenantId,
                        AuthController.UserDto::email, AuthController.UserDto::roles)
                .containsExactly(7L, 11L, "owner@example.com", Set.of("TENANT_ADMIN"));
        verify(response, never()).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    void login_shouldWriteHttpOnlyCookieAndHideTokenInCookieMode() {
        AuthService authService = mock(AuthService.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setCookieEnabled(true);
        properties.getJwt().setCookieName(" ");
        properties.getJwt().setCookieSameSite(" ");
        properties.getJwt().setCookieSecure(true);
        properties.getJwt().setTtlSeconds(120);
        when(authService.login("owner@example.com", "password123"))
                .thenReturn(new AuthResult("jwt-token", principal()));
        AuthController controller = new AuthController(authService, properties);

        ApiResponse<AuthController.AuthResponse> actual = controller.login(
                new AuthController.LoginRequest("owner@example.com", "password123"),
                response
        );

        assertThat(actual.getData().token()).isNull();
        ArgumentCaptor<String> cookie = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), cookie.capture());
        assertThat(cookie.getValue())
                .contains("lf_token=jwt-token")
                .contains("Path=/")
                .contains("Max-Age=120")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Test
    void logout_shouldRevokeAuthenticatedUserAndExpireCookie() {
        AuthService authService = mock(AuthService.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setCookieEnabled(true);
        AuthController controller = new AuthController(authService, properties);

        ApiResponse<Void> actual = controller.logout(principal(), response);

        assertThat(actual.getCode()).isZero();
        verify(authService).logout(7L);
        ArgumentCaptor<String> cookie = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), cookie.capture());
        assertThat(cookie.getValue()).contains("lf_token=").contains("Max-Age=0");
    }

    @Test
    void csrf_shouldKeepAnonymousBearerModeResponseStable() {
        AuthController controller = new AuthController(mock(AuthService.class), new SecurityProperties());

        assertThat(controller.csrf(null).getData()).isNull();

        CsrfToken token = mock(CsrfToken.class);
        when(token.getHeaderName()).thenReturn("X-XSRF-TOKEN");
        when(token.getToken()).thenReturn("csrf-value");
        assertThat(controller.csrf(token).getData())
                .isEqualTo(new AuthController.CsrfResponse("X-XSRF-TOKEN", "csrf-value"));
    }

    private static AuthPrincipal principal() {
        return new AuthPrincipal(7L, 11L, "owner@example.com", Set.of("TENANT_ADMIN"), 3);
    }
}
