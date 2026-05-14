package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.security.StandardRoles;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.ShortLink;

public final class ShortLinkUserAccess {

    private ShortLinkUserAccess() {
    }

    public static ShortLinkSearchQuery scopeBrowse(UserActor actor, ShortLinkSearchQuery query) {
        if (isTenantAdmin(actor)) {
            return query;
        }
        return new ShortLinkSearchQuery(
                query.archived(),
                query.enabled(),
                query.keyword(),
                query.tag(),
                query.applicationId(),
                actor.userId(),
                CreatedByType.USER,
                true
        );
    }

    public static void requireCanAccess(UserActor actor, ShortLink link) {
        if (actor == null || link == null || actor.tenantId() != link.tenantId()) {
            throw new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND);
        }
        if (isTenantAdmin(actor)) {
            return;
        }
        if (link.applicationId() == null
                && link.createdByType() == CreatedByType.USER
                && link.createdBy() == actor.userId()) {
            return;
        }
        throw new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND);
    }

    public static boolean isTenantAdmin(UserActor actor) {
        return actor != null
                && actor.roles() != null
                && actor.roles().contains(StandardRoles.TENANT_ADMIN);
    }
}
