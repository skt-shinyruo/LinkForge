package com.linkforge.platform.domain;

public record MonthlyLinkLimit(long value) {

    public MonthlyLinkLimit {
        if (value < 0) {
            throw new IllegalArgumentException("monthlyLinkLimit must not be negative");
        }
    }

    public static MonthlyLinkLimit of(long value) {
        return new MonthlyLinkLimit(value);
    }

    public static MonthlyLinkLimit unlimited() {
        return new MonthlyLinkLimit(0);
    }
}
