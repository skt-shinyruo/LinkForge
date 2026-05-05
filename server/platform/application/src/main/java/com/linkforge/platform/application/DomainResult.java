package com.linkforge.platform.application;

import com.linkforge.platform.domain.DomainScope;

public record DomainResult(long id, long tenantId, Long applicationId, String hostname, DomainScope scope) {
}
