package com.linkforge.governance.domain;

import java.util.OptionalLong;

public record ApprovalSnapshot(String raw) {

    public ApprovalSnapshot {
        raw = raw == null ? "" : raw;
    }

    public static ApprovalSnapshot of(String raw) {
        return new ApprovalSnapshot(raw);
    }

    public OptionalLong monthlyLinkLimit() {
        String marker = "monthlyLinkLimit=";
        int start = raw.indexOf(marker);
        if (start < 0) {
            return OptionalLong.empty();
        }
        int valueStart = start + marker.length();
        int valueEnd = raw.indexOf(',', valueStart);
        String value = valueEnd < 0 ? raw.substring(valueStart) : raw.substring(valueStart, valueEnd);
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
