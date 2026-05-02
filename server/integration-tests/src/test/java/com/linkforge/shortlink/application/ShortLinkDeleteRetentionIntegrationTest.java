package com.linkforge.shortlink.application;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.analytics.infrastructure.persistence.AnalyticsQueryRepository;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDailyUpsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyMapper;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkStatsDimDailyUpsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventInsertRow;
import com.linkforge.analytics.infrastructure.persistence.mapper.LinkVisitEventMapper;
import com.linkforge.foundation.security.AuthPrincipal;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.scheduling.enabled=false"
)
class ShortLinkDeleteRetentionIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.6.2-alpine")
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)))
            .withStartupAttempts(3);

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

        // 避免统计相关调度影响测试稳定性
        r.add("app.analytics.dimensions.enabled", () -> "false");
        r.add("app.analytics.events.enabled", () -> "false");
    }

    @Autowired
    ShortLinkApplicationService shortLinkService;

    @Autowired
    ShortLinkQueryMapper shortLinkQueryMapper;

    @Autowired
    AnalyticsQueryRepository analyticsQueryRepository;

    @Autowired
    LinkStatsDailyMapper linkStatsDailyMapper;

    @Autowired
    LinkStatsDimDailyMapper linkStatsDimDailyMapper;

    @Autowired
    LinkVisitEventMapper linkVisitEventMapper;

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

        LinkStatsDimDailyUpsertRow dim = new LinkStatsDimDailyUpsertRow();
        dim.setTenantId(TENANT_ID);
        dim.setLinkId(linkId);
        dim.setDay(day);
        dim.setDimType("referer_domain");
        dim.setDimValue("example.com");
        dim.setPv(3L);
        dim.setUv(2L);
        linkStatsDimDailyMapper.batchUpsert(List.of(dim));

        LinkVisitEventInsertRow e = new LinkVisitEventInsertRow();
        e.setId(900_000_001L);
        e.setTenantId(TENANT_ID);
        e.setLinkId(linkId);
        e.setOccurredAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        e.setRequestId("req-1");
        linkVisitEventMapper.batchInsertIgnore(List.of(e));

        shortLinkService.archive(TENANT_ID, linkId);
        shortLinkService.delete(TENANT_ID, linkId);

        assertThat(shortLinkQueryMapper.findByTenantIdAndId(TENANT_ID, linkId)).isNull();

        assertThat(analyticsQueryRepository.linkDaily(TENANT_ID, linkId, day, day)).hasSize(1);
        assertThat(analyticsQueryRepository.linkDimRows(TENANT_ID, linkId, day, day, "referer_domain", 10)).hasSize(1);
        assertThat(analyticsQueryRepository.linkEvents(
                TENANT_ID,
                linkId,
                day.atStartOfDay(),
                day.plusDays(1).atStartOfDay(),
                10
        )).hasSize(1);
    }
}
