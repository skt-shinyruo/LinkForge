package com.linkforge.shortlink.domain;

import java.util.Objects;

public class DestinationChangePolicy {

    public Decision decide(
            boolean applicationAwareLink,
            ShortLinkLifecycleState lifecycleState,
            String currentOriginalUrl,
            String requestedOriginalUrl
    ) {
        if (requestedOriginalUrl == null || Objects.equals(currentOriginalUrl, requestedOriginalUrl)) {
            return Decision.DIRECT;
        }
        if (applicationAwareLink && lifecycleState == ShortLinkLifecycleState.ACTIVE) {
            return Decision.REQUIRES_APPROVAL;
        }
        return Decision.DIRECT;
    }

    public enum Decision {
        DIRECT,
        REQUIRES_APPROVAL
    }
}
