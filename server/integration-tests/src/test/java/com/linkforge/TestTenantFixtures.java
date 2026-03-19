package com.linkforge;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestTenantFixtures {

    private TestTenantFixtures() {
    }

    public static void ensureTenantExists(JdbcTemplate jdbcTemplate, long tenantId) {
        if (jdbcTemplate == null || tenantId <= 0) {
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO tenants (id, name, status)
                        VALUES (?, ?, 'ACTIVE')
                        ON DUPLICATE KEY UPDATE name = name
                        """,
                tenantId,
                "test-tenant-" + tenantId
        );
    }
}
