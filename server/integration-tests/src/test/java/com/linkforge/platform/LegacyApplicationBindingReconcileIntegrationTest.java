package com.linkforge.platform;

import com.linkforge.LinkForgeApplication;
import com.linkforge.TestTenantFixtures;
import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = LinkForgeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class LegacyApplicationBindingReconcileIntegrationTest extends PlatformPersistenceIntegrationTestSupport {

    private static final long TENANT_ID = 81031L;
    private static final long OTHER_TENANT_ID = 81032L;

    @Autowired
    LegacyApplicationProvisioningPort legacyBindings;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, TENANT_ID);
        TestTenantFixtures.ensureTenantExists(jdbcTemplate, OTHER_TENANT_ID);
        cleanTenant(TENANT_ID);
        cleanTenant(OTHER_TENANT_ID);
    }

    @Test
    void completeBinding_shouldBeReusedAndCurrentConfigurationReconciled() {
        LegacyApplicationBindingView first = legacyBindings.ensureLegacyDefaultBinding(TENANT_ID);
        jdbcTemplate.update(
                "UPDATE application_policies SET default_domain_scope = 'TENANT_SHARED', default_redirect_status_code = 301, preview_enabled = 1 WHERE application_id = ?",
                first.applicationId()
        );
        jdbcTemplate.update(
                "UPDATE application_quotas SET monthly_link_limit = 1, monthly_click_limit = 2 WHERE application_id = ?",
                first.applicationId()
        );

        LegacyApplicationBindingView second = legacyBindings.ensureLegacyDefaultBinding(TENANT_ID);

        assertThat(second).isEqualTo(first);
        assertCurrentConfiguration(first.applicationId());
        assertThat(count("applications", "tenant_id = " + TENANT_ID + " AND application_key = 'legacy-default'"))
                .isEqualTo(1);
        assertThat(count("domains", "tenant_id = " + TENANT_ID + " AND hostname = '" + hostname() + "'"))
                .isEqualTo(1);
    }

    @Test
    void partialApplication_shouldCreateMissingDomainPolicyAndQuota() {
        insertApplication(101L, TENANT_ID, "ACTIVE", "legacy-default");

        LegacyApplicationBindingView binding = legacyBindings.ensureLegacyDefaultBinding(TENANT_ID);

        assertThat(binding.applicationId()).isEqualTo(101L);
        Map<String, Object> domain = jdbcTemplate.queryForMap(
                "SELECT tenant_id, application_id, scope, status, trust_class FROM domains WHERE id = ?",
                binding.domainId()
        );
        assertThat(domain)
                .containsEntry("tenant_id", TENANT_ID)
                .containsEntry("application_id", 101L)
                .containsEntry("scope", "APPLICATION_DEDICATED")
                .containsEntry("status", "ACTIVE")
                .containsEntry("trust_class", "FIRST_PARTY");
        assertCurrentConfiguration(101L);
    }

    @Test
    void disabledApplicationOrDomain_shouldBeRejectedWithoutSilentReenable() {
        insertApplication(101L, TENANT_ID, "DISABLED", "legacy-default");
        assertThatThrownBy(() -> legacyBindings.ensureLegacyDefaultBinding(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用未启用");
        assertThat(count("domains", "tenant_id = " + TENANT_ID)).isZero();

        cleanTenant(TENANT_ID);
        insertApplication(101L, TENANT_ID, "ACTIVE", "legacy-default");
        insertDomain(201L, TENANT_ID, 101L, hostname(), "APPLICATION_DEDICATED", "DISABLED", "FIRST_PARTY");
        assertThatThrownBy(() -> legacyBindings.ensureLegacyDefaultBinding(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("域名未启用");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM domains WHERE id = 201", String.class))
                .isEqualTo("DISABLED");
        assertThat(count("application_policies", "application_id = 101")).isZero();
    }

    @Test
    void crossTenantOrWrongApplicationDomain_shouldBeRejectedDeterministically() {
        insertApplication(101L, TENANT_ID, "ACTIVE", "legacy-default");
        insertApplication(301L, OTHER_TENANT_ID, "ACTIVE", "other");
        insertDomain(201L, OTHER_TENANT_ID, 301L, hostname(), "APPLICATION_DEDICATED", "ACTIVE", "FIRST_PARTY");

        assertThatThrownBy(() -> legacyBindings.ensureLegacyDefaultBinding(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他租户");
        assertThat(count("application_policies", "application_id = 101")).isZero();

        jdbcTemplate.update("DELETE FROM domains WHERE id = 201");
        insertApplication(102L, TENANT_ID, "ACTIVE", "other");
        insertDomain(202L, TENANT_ID, 102L, hostname(), "APPLICATION_DEDICATED", "ACTIVE", "FIRST_PARTY");
        assertThatThrownBy(() -> legacyBindings.ensureLegacyDefaultBinding(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("绑定错误");
        assertThat(count("application_policies", "application_id = 101")).isZero();
    }

    @Test
    void concurrentFirstCalls_shouldConvergeOnOneLogicalBinding() throws Exception {
        int callers = 8;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LegacyApplicationBindingView>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(30, TimeUnit.SECONDS);
                    return legacyBindings.ensureLegacyDefaultBinding(TENANT_ID);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<LegacyApplicationBindingView> results = new ArrayList<>();
            for (Future<LegacyApplicationBindingView> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(new HashSet<>(results)).hasSize(1);
            LegacyApplicationBindingView binding = results.get(0);
            assertThat(count("applications", "tenant_id = " + TENANT_ID + " AND application_key = 'legacy-default'"))
                    .isEqualTo(1);
            assertThat(count("domains", "tenant_id = " + TENANT_ID + " AND hostname = '" + hostname() + "'"))
                    .isEqualTo(1);
            assertThat(count("application_policies", "application_id = " + binding.applicationId())).isEqualTo(1);
            assertThat(count("application_quotas", "application_id = " + binding.applicationId())).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertCurrentConfiguration(long applicationId) {
        Map<String, Object> policy = jdbcTemplate.queryForMap(
                "SELECT default_domain_scope, default_redirect_status_code, preview_enabled FROM application_policies WHERE application_id = ?",
                applicationId
        );
        assertThat(policy)
                .containsEntry("default_domain_scope", "APPLICATION_DEDICATED")
                .containsEntry("default_redirect_status_code", 302);
        assertThat(((Number) policy.get("preview_enabled")).intValue()).isZero();
        Map<String, Object> quota = jdbcTemplate.queryForMap(
                "SELECT monthly_link_limit, monthly_click_limit FROM application_quotas WHERE application_id = ?",
                applicationId
        );
        assertThat(quota)
                .containsEntry("monthly_link_limit", 10_000)
                .containsEntry("monthly_click_limit", 1_000_000L);
    }

    private void cleanTenant(long tenantId) {
        jdbcTemplate.update("DELETE FROM application_domain_authorizations WHERE application_id IN (SELECT id FROM applications WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM application_policies WHERE application_id IN (SELECT id FROM applications WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM application_quotas WHERE application_id IN (SELECT id FROM applications WHERE tenant_id = ?)", tenantId);
        jdbcTemplate.update("DELETE FROM domains WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM applications WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM platform_legacy_binding_locks WHERE tenant_id = ?", tenantId);
    }

    private void insertApplication(long id, long tenantId, String status, String key) {
        jdbcTemplate.update(
                "INSERT INTO applications (id, tenant_id, application_key, display_name, status) VALUES (?, ?, ?, 'test', ?)",
                id,
                tenantId,
                key,
                status
        );
    }

    private void insertDomain(
            long id,
            long tenantId,
            Long applicationId,
            String hostname,
            String scope,
            String status,
            String trustClass
    ) {
        jdbcTemplate.update(
                "INSERT INTO domains (id, tenant_id, application_id, hostname, scope, status, trust_class) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                applicationId,
                hostname,
                scope,
                status,
                trustClass
        );
    }

    private int count(String table, String condition) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Integer.class);
        return value == null ? 0 : value;
    }

    private String hostname() {
        return "legacy-" + TENANT_ID + ".localhost";
    }
}
