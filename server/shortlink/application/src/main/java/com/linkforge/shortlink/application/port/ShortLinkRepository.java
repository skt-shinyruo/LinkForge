package com.linkforge.shortlink.application.port;

import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.ShortLink;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShortLinkRepository {

    Optional<ShortLink> findByTenantIdAndId(long tenantId, long linkId);

    Optional<ShortLink> findUnscopedByCode(String code);

    Optional<ShortLink> findByDomainIdAndCode(long domainId, String code);

    long countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
            long tenantId,
            long applicationId,
            LocalDateTime fromInclusiveUtc,
            LocalDateTime toExclusiveUtc
    );

    void insert(ShortLink link);

    boolean update(ShortLink link);

    boolean deleteByTenantIdAndId(long tenantId, long linkId, long version);

    long countSearch(long tenantId, ShortLinkSearchQuery query);

    List<ShortLink> listSearch(long tenantId, ShortLinkSearchQuery query, long offset, int limit);
}
