package com.linkforge.contract.platform;

public record ApplicationQuotaView(
        long applicationId,
        long monthlyLinkLimit,
        long monthlyClickLimit
) {
}
