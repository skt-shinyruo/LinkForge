package com.linkforge.platform.domain;

public class QuotaPolicy {

    public void requireValid(ApplicationQuota quota) {
        if (quota == null) {
            throw new IllegalArgumentException("quota must be provided");
        }
        MonthlyLinkLimit.of(quota.monthlyLinkLimit());
        MonthlyLinkLimit.of(quota.monthlyClickLimit());
    }

    public boolean unlimited(long limit) {
        return limit == 0;
    }
}
