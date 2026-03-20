package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkforge.contract.shortlink.ShortLinkEventTypes;
import com.linkforge.contract.shortlink.ShortLinkPublicSnapshot;
import com.linkforge.contract.shortlink.event.ShortLinkArchivedV1;
import com.linkforge.contract.shortlink.event.ShortLinkCreatedV1;
import com.linkforge.foundation.eventing.IntegrationEventStore;
import com.linkforge.redirect.infrastructure.projection.ShortLinkEventProjectorJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(
        classes = LinkForgeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.scheduling.enabled=false"
)
@AutoConfigureMockMvc
class ArchivedLinkRedirectIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("linkforge")
            .withUsername("linkforge")
            .withPassword("linkforge");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.4-alpine")
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

        // 避免启动期严格校验失败（以及减少 log 噪音）
        r.add("app.analytics.salt", () -> "test-analytics-salt");
        r.add("app.redirect.cache-ttl-seconds", () -> "60");
        r.add("app.redirect.default-status-code", () -> "302");
        r.add("app.edge.risk-control.enabled", () -> "false");

        // 预览页内部参数默认不透传（逗号分隔 List 绑定）
        r.add("app.redirect.query-forward-reserved-params", () -> "__lf_confirm,__lf_preview");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    IntegrationEventStore eventStore;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ShortLinkEventProjectorJob projectorJob;

    private String code;
    private long linkId;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();

        long suffix = System.nanoTime();
        linkId = (suffix & Long.MAX_VALUE) <= 0 ? 1L : (suffix & Long.MAX_VALUE);
        code = "archived" + Long.toUnsignedString(suffix);

        Instant t1 = Instant.now();
        ShortLinkPublicSnapshot createdSnapshot = new ShortLinkPublicSnapshot(
                1L,
                linkId,
                code,
                "localhost",
                "https://example.com",
                true,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                null,
                null,
                null
        );
        String createdEventId = "it-created-" + code;
        ShortLinkCreatedV1 created = new ShortLinkCreatedV1(createdEventId, t1, 1L, linkId, code, createdSnapshot);

        Instant t2 = t1.plusSeconds(1);
        ShortLinkPublicSnapshot archivedSnapshot = new ShortLinkPublicSnapshot(
                1L,
                linkId,
                code,
                "localhost",
                "https://example.com",
                true,
                null,
                null,
                false,
                null,
                null,
                List.of(),
                t2,
                null,
                null
        );
        String archivedEventId = "it-archived-" + code;
        ShortLinkArchivedV1 archived = new ShortLinkArchivedV1(archivedEventId, t2, 1L, linkId, code, archivedSnapshot);

        eventStore.append(
                createdEventId,
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_CREATED_V1,
                1L,
                "shortlink",
                linkId,
                t1,
                toJson(created)
        );
        eventStore.append(
                archivedEventId,
                "shortlink",
                ShortLinkEventTypes.SHORT_LINK_ARCHIVED_V1,
                1L,
                "shortlink",
                linkId,
                t2,
                toJson(archived)
        );

        projectorJob.drain();
    }

    @Test
    void should_return_404_html_when_link_archived() throws Exception {
        mockMvc.perform(get("/r/" + code).header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
