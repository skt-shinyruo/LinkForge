package com.linkforge.shortlink.application;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.CreatedByType;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface ShortLinkService {

    LinkDto create(long tenantId, CreatedBy createdBy, CreateLinkRequest req);

    PageResult<LinkDto> search(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery);

    LinkDto detail(long tenantId, long linkId);

    LinkDto archive(long tenantId, long linkId);

    LinkDto restore(long tenantId, long linkId);

    void delete(long tenantId, long linkId);

    LinkDto update(long tenantId, long linkId, UpdateLinkRequest req, UserActor actor, LocalDateTime requestedAt);

    List<TagDto> listTags(long tenantId);

    TagDto createTag(long tenantId, String name);

    ImportResult importCsv(long tenantId, CreatedBy createdBy, InputStream inputStream);

    void exportCsv(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery, OutputStream os);

    record CreatedBy(long id, CreatedByType type) {
        public static CreatedBy user(long userId) {
            return new CreatedBy(userId, CreatedByType.USER);
        }

        public static CreatedBy apiKey(long apiKeyId) {
            return new CreatedBy(apiKeyId, CreatedByType.API_KEY);
        }
    }

    record CreateLinkRequest(
            String originalUrl,
            String note,
            Instant expiresAt,
            Boolean enabled,
            String customCode,
            Set<String> tags,
            Integer redirectStatusCode,
            Boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            List<String> queryForwardAllowlist,
            Long applicationId,
            Long domainId,
            String lifecycleState
    ) {
    }

    record UpdateLinkRequest(
            String originalUrl,
            String note,
            Instant expiresAt,
            Boolean clearExpiresAt,
            Boolean enabled,
            Set<String> tags,
            Integer redirectStatusCode,
            Boolean clearRedirectStatusCode,
            Boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            Boolean clearQueryForwardMode,
            List<String> queryForwardAllowlist,
            String lifecycleState
    ) {
    }

    record LinkDto(
            long id,
            long tenantId,
            Long applicationId,
            Long domainId,
            String lifecycleState,
            String code,
            String shortUrl,
            String originalUrl,
            String note,
            boolean enabled,
            Instant expiresAt,
            Instant archivedAt,
            Integer redirectStatusCode,
            boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            List<String> queryForwardAllowlist,
            List<String> tags,
            Instant createdAt
    ) {
    }

    record TagDto(long id, String name) {
    }

    record ImportResult(int success, int failed, List<String> errors) {
    }
}
