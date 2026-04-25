package com.linkforge.redirect.interfaces.web;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.redirect.application.error.RedirectErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class RedirectAvailabilityPolicy {

    private final Clock clock;

    public RedirectAvailabilityPolicy(Clock clock) {
        this.clock = clock;
    }

    public UnavailableReason unavailableReason(LinkMeta meta) {
        if (meta == null) {
            return UnavailableReason.NOT_FOUND;
        }
        if (!meta.enabled()) {
            return UnavailableReason.DISABLED;
        }
        if (!meta.activeLifecycle()) {
            return UnavailableReason.DISABLED;
        }
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (meta.expiresAt() != null && !meta.expiresAt().isAfter(nowUtc)) {
            return UnavailableReason.EXPIRED;
        }
        return null;
    }

    public enum UnavailableReason {
        NOT_FOUND,
        DISABLED,
        EXPIRED;

        public RedirectErrorCode toErrorCode() {
            return switch (this) {
                case NOT_FOUND -> RedirectErrorCode.LINK_NOT_FOUND;
                case DISABLED -> RedirectErrorCode.LINK_DISABLED;
                case EXPIRED -> RedirectErrorCode.LINK_EXPIRED;
            };
        }
    }
}
