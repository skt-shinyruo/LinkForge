package com.linkforge.contract.platform;

public interface LegacyApplicationProvisioningPort {

    LegacyApplicationBindingView ensureLegacyDefaultBinding(long tenantId);
}
