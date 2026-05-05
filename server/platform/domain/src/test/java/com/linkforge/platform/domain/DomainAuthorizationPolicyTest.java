package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainAuthorizationPolicyTest {

    private final DomainAuthorizationPolicy policy = new DomainAuthorizationPolicy();

    @Test
    void dedicatedDomain_shouldAllowOnlyBoundApplication() {
        Domain domain = domain(DomainScope.APPLICATION_DEDICATED, 2001L);

        assertThatCode(() -> policy.requireApplicationCanUseDomain(2001L, domain, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireApplicationCanUseDomain(2002L, domain, true))
                .isInstanceOf(DomainAuthorizationException.class)
                .extracting("reason")
                .isEqualTo(DomainAuthorizationException.Reason.DEDICATED_DOMAIN_MISMATCH);
    }

    @Test
    void sharedDomain_shouldRequireExplicitApplicationAuthorization() {
        Domain domain = domain(DomainScope.TENANT_SHARED, null);

        assertThatCode(() -> policy.requireApplicationCanUseDomain(2001L, domain, true))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireApplicationCanUseDomain(2001L, domain, false))
                .isInstanceOf(DomainAuthorizationException.class)
                .extracting("reason")
                .isEqualTo(DomainAuthorizationException.Reason.SHARED_DOMAIN_NOT_AUTHORIZED);
    }

    @Test
    void inactiveDomain_shouldBeRejected() {
        Domain domain = domain(DomainScope.TENANT_SHARED, null, DomainStatus.DISABLED);

        assertThatThrownBy(() -> policy.requireApplicationCanUseDomain(2001L, domain, true))
                .isInstanceOf(DomainAuthorizationException.class)
                .extracting("reason")
                .isEqualTo(DomainAuthorizationException.Reason.DOMAIN_NOT_ACTIVE);
    }

    private static Domain domain(DomainScope scope, Long applicationId) {
        return domain(scope, applicationId, DomainStatus.ACTIVE);
    }

    private static Domain domain(DomainScope scope, Long applicationId, DomainStatus status) {
        return new Domain(
                3001L,
                1L,
                applicationId,
                "go.example.com",
                scope,
                status,
                TargetTrustClass.FIRST_PARTY,
                LocalDateTime.parse("2026-04-28T10:00:00"),
                LocalDateTime.parse("2026-04-28T10:00:00")
        );
    }
}
