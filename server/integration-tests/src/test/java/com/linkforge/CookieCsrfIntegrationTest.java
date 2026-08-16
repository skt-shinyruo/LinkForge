package com.linkforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CookieCsrfIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("app.base-url", () -> "http://localhost");

        // 启用 Cookie 模式（并触发 CSRF）
        r.add("app.security.jwt.cookie-enabled", () -> "true");
        r.add("app.security.jwt.cookie-name", () -> "lf_token");
        r.add("app.security.jwt.cookie-same-site", () -> "Lax");
        r.add("app.security.jwt.cookie-secure", () -> "false");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void register_shouldRequireCsrfToken_whenCookieModeEnabled() throws Exception {
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "csrf-tenant")
                .put("email", "csrf-" + System.nanoTime() + "@example.com")
                .put("password", "password123");

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void register_shouldSucceed_withDoubleSubmitCookieCsrf() throws Exception {
        // 1) 先获取 CSRF cookie
        var csrfResp = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        Cookie csrfCookie = csrfResp.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();

        // 2) 带 cookie + header 执行写请求
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "csrf-tenant-" + System.nanoTime())
                .put("email", "csrf-ok-" + System.nanoTime() + "@example.com")
                .put("password", "password123");

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(registerBody))
                )
                .andExpect(status().isOk());
    }
}
