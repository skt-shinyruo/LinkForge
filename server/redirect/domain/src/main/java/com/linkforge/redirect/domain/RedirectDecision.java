package com.linkforge.redirect.domain;

public record RedirectDecision(
        Kind kind,
        UnavailableReason reason,
        String blockReason
) {

    public RedirectDecision {
        if (kind == null) {
            throw new IllegalArgumentException("kind must be provided");
        }
        if (kind != Kind.UNAVAILABLE) {
            reason = null;
        }
        if (kind != Kind.BLOCKED) {
            blockReason = null;
        }
    }

    public static RedirectDecision redirect() {
        return new RedirectDecision(Kind.REDIRECT, null, null);
    }

    public static RedirectDecision preview() {
        return new RedirectDecision(Kind.PREVIEW, null, null);
    }

    public static RedirectDecision notFound() {
        return new RedirectDecision(Kind.NOT_FOUND, null, null);
    }

    public static RedirectDecision unavailable(UnavailableReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must be provided");
        }
        return new RedirectDecision(Kind.UNAVAILABLE, reason, null);
    }

    public static RedirectDecision blocked(String blockReason) {
        return new RedirectDecision(Kind.BLOCKED, null, blockReason);
    }

    public enum Kind {
        REDIRECT,
        PREVIEW,
        NOT_FOUND,
        UNAVAILABLE,
        BLOCKED
    }

    public enum UnavailableReason {
        DISABLED,
        EXPIRED,
        QUOTA_EXCEEDED
    }
}
