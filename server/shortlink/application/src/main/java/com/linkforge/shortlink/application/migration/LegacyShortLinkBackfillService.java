package com.linkforge.shortlink.application.migration;

import com.linkforge.contract.platform.LegacyApplicationBindingView;
import com.linkforge.contract.platform.LegacyApplicationProvisioningPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.port.ShortLinkOwnershipBackfillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

@Service
public class LegacyShortLinkBackfillService {

    public static final String LEGACY_DEFAULT_APPLICATION_KEY = "legacy-default";
    public static final String LEGACY_DEFAULT_APPLICATION_NAME = "Legacy Default";
    static final long DEFAULT_MONTHLY_LINK_LIMIT = 10_000L;
    static final long DEFAULT_MONTHLY_CLICK_LIMIT = 1_000_000L;

    private final LegacyApplicationProvisioningPort legacyApplicationProvisioningPort;
    private final ShortLinkOwnershipBackfillRepository backfillRepository;
    private final CoreProperties coreProperties;

    public LegacyShortLinkBackfillService(
            LegacyApplicationProvisioningPort legacyApplicationProvisioningPort,
            ShortLinkOwnershipBackfillRepository backfillRepository,
            CoreProperties coreProperties
    ) {
        this.legacyApplicationProvisioningPort = legacyApplicationProvisioningPort;
        this.backfillRepository = backfillRepository;
        this.coreProperties = coreProperties;
    }

    @Transactional
    public BackfillResult backfillTenant(long tenantId) {
        LegacyApplicationBindingView binding = legacyApplicationProvisioningPort.ensureLegacyDefaultBinding(
                tenantId,
                LEGACY_DEFAULT_APPLICATION_KEY,
                LEGACY_DEFAULT_APPLICATION_NAME,
                legacyHostname(tenantId),
                DEFAULT_MONTHLY_LINK_LIMIT,
                DEFAULT_MONTHLY_CLICK_LIMIT
        );
        int updated = backfillRepository.backfillTenant(tenantId, binding.applicationId(), binding.domainId());
        return new BackfillResult(tenantId, binding.applicationId(), binding.domainId(), updated);
    }

    private String legacyHostname(long tenantId) {
        String baseUrl = coreProperties == null ? null : coreProperties.getBaseUrl();
        String host = "legacy-host";
        if (baseUrl != null && !baseUrl.isBlank()) {
            try {
                URI uri = URI.create(baseUrl);
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    host = uri.getHost().toLowerCase();
                }
            } catch (Exception ignored) {
                // fall through to synthetic host
            }
        }
        return "legacy-" + tenantId + "." + host;
    }

    public record BackfillResult(long tenantId, long applicationId, long domainId, int updatedCount) {
    }
}
