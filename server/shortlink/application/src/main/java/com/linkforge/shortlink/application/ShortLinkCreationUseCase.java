package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;

public interface ShortLinkCreationUseCase {

    LinkDto createForUser(UserActor actor, ScopedCreateLinkRequest request);

    LinkDto createForApiKey(ApiKeyActor actor, ScopedCreateLinkRequest request);

    LinkDto create(long tenantId, CreatedBy createdBy, CreateLinkRequest req);
}
