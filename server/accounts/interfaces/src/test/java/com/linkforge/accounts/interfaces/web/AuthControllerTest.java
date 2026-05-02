package com.linkforge.accounts.interfaces.web;

import com.linkforge.accounts.application.AuthService;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
