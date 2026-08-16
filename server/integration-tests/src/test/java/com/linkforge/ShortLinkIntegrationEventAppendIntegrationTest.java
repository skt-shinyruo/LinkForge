package com.linkforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.LinkForgeApplication;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.shortlink.application.*;
import com.linkforge.testsupport.SharedIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkIntegrationEventAppendIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 避免调度任务影响测试稳定性
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
    }

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

    @Autowired
    ShortLinkApplicationService shortLinkService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpAuth() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        AuthPrincipal principal = new AuthPrincipal(USER_ID, TENANT_ID, "admin@example.com", Set.of("tenant_admin"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", List.of())
        );
    }

    @AfterEach
    void tearDownAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_should_append_integration_event() throws Exception {
        CreateLinkRequest req = new CreateLinkRequest(
                "https://example.com",
                "note",
                null,
                null,
                null,
                Set.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        LinkDto dto = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req);

        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM integration_events
                        WHERE producer = ?
                          AND event_type = ?
                          AND tenant_id = ?
                          AND aggregate_type = 'shortlink'
                          AND aggregate_id = ?
                        """,
                Integer.class,
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                TENANT_ID,
                dto.id()
        );

        assertThat(count).isEqualTo(1);

        String payloadJson = jdbcTemplate.queryForObject(
                """
                        SELECT payload_json
                        FROM integration_events
                        WHERE producer = ?
                          AND event_type = ?
                          AND tenant_id = ?
                          AND aggregate_type = 'shortlink'
                          AND aggregate_id = ?
                        """,
                String.class,
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                TENANT_ID,
                dto.id()
        );
        JsonNode payload = new ObjectMapper().readTree(payloadJson);

        assertThat(payload.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "eventId", "occurredAtUtc", "tenantId", "linkId", "code", "snapshot"
        );
        assertThat(payload.path("eventId").asText()).isNotBlank();
        assertThat(payload.path("occurredAtUtc").isTextual()).isTrue();
        assertThat(payload.path("tenantId").asLong()).isEqualTo(TENANT_ID);
        assertThat(payload.path("linkId").asLong()).isEqualTo(dto.id());
        assertThat(payload.path("snapshot").path("linkId").asLong()).isEqualTo(dto.id());
        assertThat(payload.path("snapshot").path("originalUrl").asText()).isEqualTo("https://example.com");
    }
}
