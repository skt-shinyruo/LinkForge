package com.linkforge;

import com.linkforge.LinkForgeApplication;
import com.linkforge.contract.redirect.LinkMetaQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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

import java.time.Duration;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RedirectNegativeCacheIntegrationTest {

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
        r.add("app.redirect.not-found-cache-ttl-seconds", () -> "60");
        r.add("app.edge.risk-control.enabled", () -> "false");

        // 预览页内部参数默认不透传（逗号分隔 List 绑定）
        r.add("app.redirect.query-forward-reserved-params", () -> "__lf_confirm,__lf_preview");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redis;

    @SpyBean
    LinkMetaQueryPort linkMetaQueryPort;

    @BeforeEach
    void setUp() {
        // 避免测试间缓存 key 干扰
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void not_found_should_be_negative_cached_to_reduce_db_lookups() throws Exception {
        String code = "missing" + Long.toUnsignedString(System.nanoTime());

        mockMvc.perform(get("/r/" + code).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/r/" + code).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());

        verify(linkMetaQueryPort, times(1)).findActiveByCode(code);
    }
}
