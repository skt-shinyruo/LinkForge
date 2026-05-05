package com.linkforge.platform.interfaces.web;

import com.linkforge.platform.application.ApplicationResult;
import com.linkforge.platform.application.DomainResult;

final class PlatformHttpMapper {

    private PlatformHttpMapper() {
    }

    static ApplicationHttpResponse toApplicationResponse(ApplicationResult dto) {
        return new ApplicationHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationKey(),
                dto.displayName()
        );
    }

    static DomainHttpResponse toDomainResponse(DomainResult dto) {
        return new DomainHttpResponse(
                dto.id(),
                dto.tenantId(),
                dto.applicationId(),
                dto.hostname(),
                dto.scope() == null ? null : dto.scope().name()
        );
    }
}
