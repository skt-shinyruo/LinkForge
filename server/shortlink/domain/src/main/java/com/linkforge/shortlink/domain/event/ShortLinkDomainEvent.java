package com.linkforge.shortlink.domain.event;

public interface ShortLinkDomainEvent {

    long linkId();

    long tenantId();

    Long domainId();

    String code();
}
