package com.linkforge.governance.application.port;

import com.linkforge.governance.domain.AuditLog;

import java.util.List;

/**
 * 治理审计日志的追加写端口。
 *
 * <p>实现不得覆盖既有审计事实，并应参与 Governance 应用服务发起的事务：审批提交或批准流程回滚时，
 * 对应审计记录也必须回滚。所有查询必须以租户为隔离边界。</p>
 */
public interface AuditLogRepository {

    /** 追加一条审计事实；ID 由应用层预先分配。 */
    void insert(AuditLog auditLog);

    /** 返回租户内审计记录，约定按创建时间、ID 倒序排列。 */
    List<AuditLog> listByTenantId(long tenantId);
}
