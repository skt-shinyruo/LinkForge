package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.ShortLink;

import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository {

    Optional<ShortLink> findByTenantIdAndId(long tenantId, long linkId);

    Optional<ShortLink> findByCode(String code);

    void insert(ShortLink link);

    boolean update(ShortLink link);

    boolean deleteByTenantIdAndId(long tenantId, long linkId, long version);

    long countSearch(long tenantId, ShortLinkSearchQuery query);

    List<ShortLink> listSearch(long tenantId, ShortLinkSearchQuery query, long offset, int limit);
}
