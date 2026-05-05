package com.linkforge.contract.governance;

public record ApplicationQuotaIncreaseApprovalPayload(
        String type,
        int version,
        Long monthlyLinkLimit,
        Long monthlyClickLimit
) {

    public static ApplicationQuotaIncreaseApprovalPayload v1(Long monthlyLinkLimit, Long monthlyClickLimit) {
        return new ApplicationQuotaIncreaseApprovalPayload(
                ApprovalPayloadTypes.APPLICATION_QUOTA_INCREASE,
                ApprovalPayloadTypes.VERSION_1,
                monthlyLinkLimit,
                monthlyClickLimit
        );
    }
}
