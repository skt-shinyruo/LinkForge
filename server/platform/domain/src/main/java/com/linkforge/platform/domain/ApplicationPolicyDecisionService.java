package com.linkforge.platform.domain;

public class ApplicationPolicyDecisionService {

    public boolean requiresGovernanceForDestinationChange(ApplicationPolicy policy) {
        if (policy == null) {
            return true;
        }
        TargetTrustClass trustClass = policy.targetTrustClass();
        return trustClass == TargetTrustClass.THIRD_PARTY;
    }
}
