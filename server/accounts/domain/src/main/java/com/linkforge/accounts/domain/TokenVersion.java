package com.linkforge.accounts.domain;

public record TokenVersion(int value) {

    public TokenVersion {
        if (value < 0) {
            throw new IllegalArgumentException("tokenVersion must be >= 0");
        }
    }

    public static TokenVersion initial() {
        return new TokenVersion(0);
    }

    public static TokenVersion of(Integer value) {
        return value == null ? initial() : new TokenVersion(value);
    }

    public TokenVersion incremented() {
        return new TokenVersion(value + 1);
    }
}
