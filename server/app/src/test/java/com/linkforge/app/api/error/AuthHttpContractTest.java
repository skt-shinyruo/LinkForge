package com.linkforge.app.api.error;

import com.linkforge.accounts.application.AuthService;
import com.linkforge.accounts.interfaces.web.AuthController;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.foundation.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthHttpContractTest {

    private AuthService authService;
    private SecurityProperties securityProperties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        securityProperties = new SecurityProperties();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, securityProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_shouldMapBeanValidationFailureToStableBadRequestEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("密码长度需为 8-64"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(authService);
    }

    @Test
    void register_shouldMapDisabledRegistrationToStableForbiddenEnvelope() throws Exception {
        securityProperties.setRegistrationEnabled(false);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantName":"tenant-a",
                                  "email":"owner@example.com",
                                  "password":"password123"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.message").value("自助注册未开启"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verifyNoInteractions(authService);
    }
}
