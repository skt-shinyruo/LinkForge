package com.linkforge.shortlink.domain;

public class ShortCodeAllocationPolicy {

    public CollisionDecision onCollision(boolean customCode) {
        return customCode ? CollisionDecision.FAIL : CollisionDecision.RETRY_OR_SURFACE_INFRASTRUCTURE_ERROR;
    }

    public enum CollisionDecision {
        FAIL,
        RETRY_OR_SURFACE_INFRASTRUCTURE_ERROR
    }
}
