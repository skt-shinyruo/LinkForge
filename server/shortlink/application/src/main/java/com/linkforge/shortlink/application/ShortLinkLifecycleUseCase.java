package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.UserActor;

import java.time.LocalDateTime;

public interface ShortLinkLifecycleUseCase {

    LinkDto archive(long tenantId, long linkId);

    LinkDto restore(long tenantId, long linkId);

    void delete(long tenantId, long linkId);

    LinkDto update(long tenantId, long linkId, UpdateLinkRequest req, UserActor actor, LocalDateTime requestedAt);
}
