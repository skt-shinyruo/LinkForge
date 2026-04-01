package com.linkforge.contract.platform;

public interface LegacyApplicationProvisioningPort {

    LegacyApplicationBindingView ensureLegacyDefaultBinding(
            long tenantId,
            String applicationKey,
            String applicationName,
            String hostname,
            long monthlyLinkLimit,
            long monthlyClickLimit
    );
}
