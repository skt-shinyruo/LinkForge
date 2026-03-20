package com.linkforge.governance.infrastructure.persistence;

import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.infrastructure.persistence.entity.AuditLogEntity;
import com.linkforge.governance.infrastructure.persistence.mapper.AuditLogMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditLogRepositoryMybatisAdapter implements AuditLogRepository {

    private final AuditLogMapper mapper;

    public AuditLogRepositoryMybatisAdapter(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(AuditLog auditLog) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(auditLog.id());
        entity.setTenantId(auditLog.tenantId());
        entity.setActorUserId(auditLog.actorUserId());
        entity.setActorEmail(auditLog.actorEmail());
        entity.setActionType(auditLog.actionType());
        entity.setResourceType(auditLog.resourceType());
        entity.setResourceId(auditLog.resourceId());
        entity.setRequestId(auditLog.requestId());
        entity.setBeforeSnapshot(auditLog.beforeSnapshot());
        entity.setAfterSnapshot(auditLog.afterSnapshot());
        entity.setCreatedAt(auditLog.createdAt());
        mapper.insert(entity);
    }

    @Override
    public List<AuditLog> listByTenantId(long tenantId) {
        return mapper.listByTenantId(tenantId).stream()
                .map(entity -> new AuditLog(
                        entity.getId(),
                        entity.getTenantId(),
                        entity.getActorUserId(),
                        entity.getActorEmail(),
                        entity.getActionType(),
                        entity.getResourceType(),
                        entity.getResourceId(),
                        entity.getRequestId(),
                        entity.getBeforeSnapshot(),
                        entity.getAfterSnapshot(),
                        entity.getCreatedAt()
                ))
                .toList();
    }
}
