package com.linkforge.platform.interfaces.web;

import com.linkforge.platform.application.ApplicationProvisioningService;

final class PlatformHttpMapper {

    private PlatformHttpMapper() {
    }

    static ApplicationHttpResponse toApplicationResponse(ApplicationProvisioningService.ApplicationDto dto) {
        return new ApplicationHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationKey(),
                dto.displayName()
        );
    }

    static DomainHttpResponse toDomainResponse(ApplicationProvisioningService.DomainDto dto) {
        return new DomainHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationId(),
                dto.hostname(),
                dto.scope() == null ? null : dto.scope().name()
        );
    }
}
