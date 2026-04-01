package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegacyShortLinkBackfillService {

    private final LegacyApplicationProvisioningPort legacyApplicationProvisioningPort;
    private final ShortLinkOwnershipBackfillRepository backfillRepository;

    public LegacyShortLinkBackfillService(
            LegacyApplicationProvisioningPort legacyApplicationProvisioningPort,
            ShortLinkOwnershipBackfillRepository backfillRepository
    ) {
        this.legacyApplicationProvisioningPort = legacyApplicationProvisioningPort;
        this.backfillRepository = backfillRepository;
    }

    @Transactional
    public BackfillResult backfillTenant(long tenantId) {
        LegacyApplicationBindingView binding = legacyApplicationProvisioningPort.ensureLegacyDefaultBinding(tenantId);
        int updated = backfillRepository.backfillTenant(tenantId, binding.applicationId(), binding.domainId());
        return new BackfillResult(tenantId, binding.applicationId(), binding.domainId(), updated);
    }

    public record BackfillResult(long tenantId, long applicationId, long domainId, int updatedCount) {
    }
}
