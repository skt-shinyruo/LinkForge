package com.linkforge.governance.domain;

public class ApprovalMatrixPolicy {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final long TENANT_ADMIN_MONTHLY_LINK_LIMIT_CEILING = 100_000L;

    public boolean mayApprove(ApprovalActor actor, ApprovalRequest request) {
        if (actor == null || request == null) {
            return false;
        }
        boolean platformAdmin = actor.hasRole(PLATFORM_ADMIN);
        boolean tenantAdmin = actor.hasRole(TENANT_ADMIN);
        if (!platformAdmin && !tenantAdmin) {
            return false;
        }
        if (request.operationType() == SensitiveOperationType.EXTERNAL_DOMAIN_BINDING) {
            return platformAdmin;
        }
        if (request.operationType() == SensitiveOperationType.APPLICATION_QUOTA_INCREASE) {
            long requestedMonthlyLinkLimit = ApprovalSnapshot.of(request.afterSnapshot())
                    .monthlyLinkLimit()
                    .orElse(0L);
            return requestedMonthlyLinkLimit <= TENANT_ADMIN_MONTHLY_LINK_LIMIT_CEILING || platformAdmin;
        }
        return true;
    }
}
