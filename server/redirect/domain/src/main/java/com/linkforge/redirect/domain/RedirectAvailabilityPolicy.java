package com.linkforge.redirect.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class RedirectAvailabilityPolicy {

    public RedirectDecision evaluate(
            boolean enabled,
            boolean activeLifecycle,
            LocalDateTime expiresAtUtc,
            LocalDateTime nowUtc,
            boolean quotaExceeded
    ) {
        Objects.requireNonNull(nowUtc, "nowUtc must be provided");
        if (!enabled || !activeLifecycle) {
            return RedirectDecision.unavailable(RedirectDecision.UnavailableReason.DISABLED);
        }
        if (expiresAtUtc != null && !expiresAtUtc.isAfter(nowUtc)) {
            return RedirectDecision.unavailable(RedirectDecision.UnavailableReason.EXPIRED);
        }
        if (quotaExceeded) {
            return RedirectDecision.unavailable(RedirectDecision.UnavailableReason.QUOTA_EXCEEDED);
        }
        return RedirectDecision.redirect();
    }
}
