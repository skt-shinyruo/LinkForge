package com.linkforge.platform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPolicyDecisionServiceTest {

    private final ApplicationPolicyDecisionService service = new ApplicationPolicyDecisionService();

    @Test
    void requiresGovernanceForDestinationChange_shouldFollowTrustClass() {
        ApplicationPolicy firstPartyPolicy = new ApplicationPolicy(
                10L,
                DomainScope.TENANT_SHARED,
                302,
                false,
                TargetTrustClass.FIRST_PARTY,
                null,
                null
        );
        ApplicationPolicy externalPolicy = new ApplicationPolicy(
                10L,
                DomainScope.TENANT_SHARED,
                302,
                false,
                TargetTrustClass.THIRD_PARTY,
                null,
                null
        );

        assertThat(service.requiresGovernanceForDestinationChange(firstPartyPolicy)).isFalse();
        assertThat(service.requiresGovernanceForDestinationChange(externalPolicy)).isTrue();
    }
}
