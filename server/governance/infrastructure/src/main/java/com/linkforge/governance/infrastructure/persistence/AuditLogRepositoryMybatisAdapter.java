package com.linkforge.governance.infrastructure.persistence;

import com.linkforge.governance.application.AuditLogSummaryResult;
import com.linkforge.governance.application.port.AuditLogRepository;
import com.linkforge.governance.domain.AuditLog;
import com.linkforge.governance.infrastructure.persistence.entity.AuditLogEntity;
import com.linkforge.governance.infrastructure.persistence.mapper.AuditLogMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志的追加式 MyBatis 仓储。
 *
 * <p>写入使用调用方生成的日志 ID，不执行 upsert、覆盖或静默去重；主键冲突和数据库故障会
 * 直接向上层传播。仓储不自行开启事务，因此 Governance 应用服务中的审批状态与审计记录可在
 * 同一数据库事务内提交或回滚。</p>
 */
@Component
public class AuditLogRepositoryMybatisAdapter implements AuditLogRepository {

    private final AuditLogMapper mapper;

    public AuditLogRepositoryMybatisAdapter(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 追加一条包含操作者、请求关联和前后快照的审计记录。
     *
     * <p>该方法不吞掉失败：若审计无法落库，事务型调用链应整体失败，避免产生没有审计证据的
     * 审批状态变更。</p>
     */
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
    public List<AuditLogSummaryResult> listSummaries(
            long tenantId,
            String actionType,
            String resourceType,
            LocalDateTime cursorCreatedAt,
            Long cursorId,
            int limit
    ) {
        return mapper.listSummaries(
                        tenantId,
                        actionType,
                        resourceType,
                        cursorCreatedAt,
                        cursorId,
                        limit
                ).stream()
                .map(entity -> new AuditLogSummaryResult(
                        entity.getId(),
                        entity.getTenantId(),
                        entity.getActorUserId(),
                        entity.getActorEmail(),
                        entity.getActionType(),
                        entity.getResourceType(),
                        entity.getResourceId(),
                        entity.getRequestId(),
                        entity.getCreatedAt()
                ))
                .toList();
    }
}
