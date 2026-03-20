package com.linkforge.governance.application.port;

import com.linkforge.governance.domain.AuditLog;

import java.util.List;

public interface AuditLogRepository {

    void insert(AuditLog auditLog);

    List<AuditLog> listByTenantId(long tenantId);
}
