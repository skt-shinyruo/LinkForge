package com.linkforge.shortlink.interfaces.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "app.scheduling.enabled=false",
                "app.shortlink.write-enabled=false"
        }
)
@AutoConfigureMockMvc
class ShortLinkWriteGuardIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
        r.add("app.analytics.events.sample-rate", () -> "1");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void create_shouldReturn503_whenShortLinkWriteDisabled() throws Exception {
        String token = registerTenantAdminAndGetToken();

        JsonNode body = objectMapper.createObjectNode()
                .put("originalUrl", "https://example.com/maintenance")
                .put("note", "maintenance");

        JsonNode response = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/links")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(body)))
                        .andExpect(status().isServiceUnavailable())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray()
        );

        assertThat(response.get("code").asInt()).isEqualTo(50300);
        assertThat(response.get("message").asText()).isEqualTo("维护中");
        assertThat(response.get("requestId").asText()).isNotBlank();
    }

    private String registerTenantAdminAndGetToken() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        JsonNode registerBody = objectMapper.createObjectNode()
                .put("tenantName", "tenant-" + suffix)
                .put("email", "admin-" + suffix + "@example.com")
                .put("password", "password123");

        JsonNode response = objectMapper.readTree(
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(registerBody)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray()
        );

        assertThat(response.get("code").asInt()).isEqualTo(0);
        assertThat(response.get("data").get("token").asText()).isNotBlank();
        return response.get("data").get("token").asText();
    }
}
