package com.linkforge.shortlink.application;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.persistence.PageResult;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface ShortLinkService {

    LinkDto create(long tenantId, long createdBy, CreateLinkRequest req);

    PageResult<LinkDto> search(long tenantId, ShortLinkSearchQuery query, PageQuery pageQuery);

    LinkDto detail(long tenantId, long linkId);

    LinkDto archive(long tenantId, long linkId);

    LinkDto restore(long tenantId, long linkId);

    void delete(long tenantId, long linkId);

    LinkDto update(long tenantId, long linkId, UpdateLinkRequest req);

    List<TagDto> listTags(long tenantId);

    TagDto createTag(long tenantId, String name);

    ImportResult importCsv(long tenantId, long createdBy, InputStream inputStream);

    void exportCsv(long tenantId, PageQuery pageQuery, OutputStream os);

    record CreateLinkRequest(
            String originalUrl,
            String note,
            LocalDateTime expiresAt,
            Boolean enabled,
            String customCode,
            Set<String> tags,
            Integer redirectStatusCode,
            Boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            List<String> queryForwardAllowlist
    ) {
    }

    record UpdateLinkRequest(
            String originalUrl,
            String note,
            LocalDateTime expiresAt,
            Boolean clearExpiresAt,
            Boolean enabled,
            Set<String> tags,
            Integer redirectStatusCode,
            Boolean clearRedirectStatusCode,
            Boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            Boolean clearQueryForwardMode,
            List<String> queryForwardAllowlist
    ) {
    }

    record LinkDto(
            long id,
            long tenantId,
            String code,
            String shortUrl,
            String originalUrl,
            String note,
            boolean enabled,
            LocalDateTime expiresAt,
            LocalDateTime archivedAt,
            Integer redirectStatusCode,
            boolean previewEnabled,
            String unavailableLandingUrl,
            String queryForwardMode,
            List<String> queryForwardAllowlist,
            List<String> tags,
            LocalDateTime createdAt
    ) {
    }

    record TagDto(long id, String name) {
    }

    record ImportResult(int success, int failed, List<String> errors) {
    }
}

