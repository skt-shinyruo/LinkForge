package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.analytics.application.AnalyticsQueryService;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyUpsertRow;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkDeleteRetentionIntegrationTest extends SharedIntegrationTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // 避免统计相关调度影响测试稳定性
    }

    @Autowired
    ShortLinkApplicationService shortLinkService;

    @Autowired
    ShortLinkQueryMapper shortLinkQueryMapper;

    @Autowired
    AnalyticsQueryService analyticsQueryService;

    @Autowired
    LinkStatsDailyMapper linkStatsDailyMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1L;

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
    void delete_shouldNotDeleteAnalyticsRows() {
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
        LinkDto created = shortLinkService.create(TENANT_ID, CreatedBy.user(USER_ID), req);
        long linkId = created.id();

        LocalDate day = LocalDate.of(2026, 1, 1);
        LinkStatsDailyUpsertRow daily = new LinkStatsDailyUpsertRow();
        daily.setLinkId(linkId);
        daily.setTenantId(TENANT_ID);
        daily.setDay(day);
        daily.setPv(10L);
        daily.setUv(5L);
        linkStatsDailyMapper.batchUpsert(List.of(daily));

        shortLinkService.archive(TENANT_ID, linkId);
        shortLinkService.delete(TENANT_ID, linkId);

        assertThat(shortLinkQueryMapper.findByTenantIdAndId(TENANT_ID, linkId)).isNull();

        assertThat(analyticsQueryService.linkDaily(TENANT_ID, linkId, day, day)).hasSize(1);
    }
}
