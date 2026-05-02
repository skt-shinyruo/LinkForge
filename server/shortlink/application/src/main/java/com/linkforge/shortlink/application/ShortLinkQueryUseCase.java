package com.linkforge.shortlink.application;

import com.linkforge.foundation.context.ApiKeyActor;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;

public interface ShortLinkQueryUseCase {

    PageResult<LinkDto> browseForUser(UserActor actor, BrowseLinksRequest request);

    PageResult<LinkDto> browseForApiKey(ApiKeyActor actor, BrowseLinksRequest request);

    PageResult<LinkDto> search(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery);

    LinkDto detail(long tenantId, long linkId);
}
