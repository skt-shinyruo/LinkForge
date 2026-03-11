package com.linkforge.shortlink.infrastructure.persistence.mapper;

public class LinkCacheOutboxStatsRow {

    private Long pendingTotal;
    private Long pendingReady;
    private Long pendingLagSeconds;

    public Long getPendingTotal() {
        return pendingTotal;
    }

    public void setPendingTotal(Long pendingTotal) {
        this.pendingTotal = pendingTotal;
    }

    public Long getPendingReady() {
        return pendingReady;
    }

    public void setPendingReady(Long pendingReady) {
        this.pendingReady = pendingReady;
    }

    public Long getPendingLagSeconds() {
        return pendingLagSeconds;
    }

    public void setPendingLagSeconds(Long pendingLagSeconds) {
        this.pendingLagSeconds = pendingLagSeconds;
    }
}

