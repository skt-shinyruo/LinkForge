package com.linkforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CookieCsrfIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);

        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        // 测试环境固定密钥，避免启动失败
        r.add("app.security.jwt.secret", () -> "test-secret-please-change-but-long-enough-32-bytes");
        r.add("app.analytics.salt", () -> "test-analytics-salt");
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
