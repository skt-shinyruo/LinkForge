package com.linkforge.shortlink.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.redirect.LinkCachePort;
import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.foundation.config.AppProperties;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.tx.AfterCommit;
import com.linkforge.foundation.security.TenantGuard;
import com.linkforge.foundation.util.Base62;
import com.linkforge.shortlink.infrastructure.outbox.LinkCacheOutboxRepository;
import com.linkforge.shortlink.infrastructure.persistence.entity.LinkTagEntity;
import com.linkforge.shortlink.infrastructure.persistence.entity.LinkTagId;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.entity.TagEntity;
import com.linkforge.shortlink.infrastructure.persistence.repo.LinkTagRepository;
import com.linkforge.shortlink.infrastructure.persistence.repo.ShortLinkRepository;
import com.linkforge.shortlink.infrastructure.persistence.repo.TagRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShortLinkService {

    private final SnowflakeIdGenerator idGenerator;
    private final ShortLinkRepository shortLinkRepository;
    private final TagRepository tagRepository;
    private final LinkTagRepository linkTagRepository;
    private final LinkCachePort linkCache;
    private final LinkCacheOutboxRepository linkCacheOutboxRepository;
    private final AppProperties appProperties;
    private final UrlValidator urlValidator;
    private final TenantGuard tenantGuard;
    private final JdbcTemplate jdbcTemplate;

    public ShortLinkService(
            SnowflakeIdGenerator idGenerator,
            ShortLinkRepository shortLinkRepository,
            TagRepository tagRepository,
            LinkTagRepository linkTagRepository,
            LinkCachePort linkCache,
            LinkCacheOutboxRepository linkCacheOutboxRepository,
            AppProperties appProperties,
            UrlValidator urlValidator,
            TenantGuard tenantGuard,
            JdbcTemplate jdbcTemplate
    ) {
        this.idGenerator = idGenerator;
        this.shortLinkRepository = shortLinkRepository;
        this.tagRepository = tagRepository;
        this.linkTagRepository = linkTagRepository;
        this.linkCache = linkCache;
        this.linkCacheOutboxRepository = linkCacheOutboxRepository;
        this.appProperties = appProperties;
        this.urlValidator = urlValidator;
        this.tenantGuard = tenantGuard;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public LinkDto create(long tenantId, long createdBy, CreateLinkRequest req) {
        tenantGuard.requireCurrentTenant(tenantId);
        urlValidator.validateHttpUrl(req.originalUrl());

        String customCode = normalize(req.customCode());
        String code;
        long id = idGenerator.nextId();
        if (customCode != null) {
            validateCode(customCode);
            ensureCodeAvailable(customCode);
            code = customCode;
        } else {
            code = Base62.encode(id);
        }

        ShortLinkEntity e = new ShortLinkEntity();
        e.setId(id);
        e.setTenantId(tenantId);
        e.setCode(code);
        e.setOriginalUrl(req.originalUrl());
        e.setNote(req.note());
        e.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
        e.setExpiresAt(req.expiresAt());
        e.setRedirectStatusCode(validateRedirectStatus(req.redirectStatusCode()));
        e.setPreviewEnabled(req.previewEnabled() == null ? Boolean.FALSE : req.previewEnabled());
        e.setUnavailableLandingUrl(validateOptionalHttpUrl(normalize(req.unavailableLandingUrl()), "unavailableLandingUrl"));
        e.setQueryForwardMode(normalizeQueryForwardMode(req.queryForwardMode()));
        e.setQueryForwardAllowlist(normalizeAllowlist(req.queryForwardAllowlist()));
        e.setCreatedBy(createdBy);
        shortLinkRepository.save(e);
        setTags(tenantId, e.getId(), req.tags());
        linkCacheOutboxRepository.enqueueRefresh(e.getCode());
        LinkMeta meta = toMeta(e);
        AfterCommit.run(() -> linkCache.tryPut(meta));
        return toDto(tenantId, e, loadTagsByLinkId(e.getId()));
    }

    public Page<LinkDto> search(long tenantId, boolean archived, Boolean enabled, String keyword, String tag, Pageable pageable) {
        tenantGuard.requireCurrentTenant(tenantId);
        Page<ShortLinkEntity> page = shortLinkRepository.search(tenantId, archived, enabled, normalize(keyword), normalize(tag), pageable);
        Map<Long, List<String>> tags = loadTagsByLinkIds(page.getContent().stream().map(ShortLinkEntity::getId).toList());
        return page.map(e -> toDto(tenantId, e, tags.getOrDefault(e.getId(), List.of())));
    }

    public LinkDto detail(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLinkEntity e = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_NOT_FOUND));
        return toDto(tenantId, e, loadTagsByLinkId(linkId));
    }

    @Transactional
    public LinkDto archive(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLinkEntity e = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_NOT_FOUND));

        if (e.getArchivedAt() == null) {
            e.setArchivedAt(LocalDateTime.now());
            shortLinkRepository.save(e);
        }

        String code = e.getCode();
        linkCacheOutboxRepository.enqueueRefresh(code);
        AfterCommit.run(() -> linkCache.tryEvict(code));
        return toDto(tenantId, e, loadTagsByLinkId(linkId));
    }

    @Transactional
    public LinkDto restore(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLinkEntity e = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_NOT_FOUND));

        if (e.getArchivedAt() != null) {
            e.setArchivedAt(null);
            shortLinkRepository.save(e);
        }

        String code = e.getCode();
        linkCacheOutboxRepository.enqueueRefresh(code);
        LinkMeta meta = toMeta(e);
        AfterCommit.run(() -> {
            linkCache.tryEvict(code);
            linkCache.tryPut(meta);
        });
        return toDto(tenantId, e, loadTagsByLinkId(linkId));
    }

    @Transactional
    public void delete(long tenantId, long linkId) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLinkEntity e = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_NOT_FOUND));

        if (e.getArchivedAt() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "删除前请先归档（可避免误删）");
        }

        String code = e.getCode();
        linkCacheOutboxRepository.enqueueRefresh(code);
        AfterCommit.run(() -> linkCache.tryEvict(code));

        // 清理关联（标签）
        linkTagRepository.deleteAllByIdLinkId(linkId);

        // 清理统计/明细（避免产生大量孤儿数据）
        // 注意：短链删除是低频治理动作，可接受同步清理
        jdbcTemplate.update("DELETE FROM link_stats_daily WHERE tenant_id = ? AND link_id = ?", tenantId, linkId);
        jdbcTemplate.update("DELETE FROM link_stats_dim_daily WHERE tenant_id = ? AND link_id = ?", tenantId, linkId);
        jdbcTemplate.update("DELETE FROM link_visit_events WHERE tenant_id = ? AND link_id = ?", tenantId, linkId);

        shortLinkRepository.delete(e);
    }

    @Transactional
    public LinkDto update(long tenantId, long linkId, UpdateLinkRequest req) {
        tenantGuard.requireCurrentTenant(tenantId);
        ShortLinkEntity e = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LINK_NOT_FOUND));

        if (e.getArchivedAt() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "短链已归档，请先恢复后再编辑");
        }

        boolean needEvict = false;

        if (req.originalUrl() != null) {
            urlValidator.validateHttpUrl(req.originalUrl());
            if (!req.originalUrl().equals(e.getOriginalUrl())) {
                e.setOriginalUrl(req.originalUrl());
                needEvict = true;
            }
        }
        if (req.note() != null) {
            e.setNote(req.note());
        }
        if (req.enabled() != null && !req.enabled().equals(e.getEnabled())) {
            e.setEnabled(req.enabled());
            needEvict = true;
        }
        if (req.expiresAt() != null || (req.expiresAt() == null && req.clearExpiresAt() != null && req.clearExpiresAt())) {
            if (req.clearExpiresAt() != null && req.clearExpiresAt()) {
                e.setExpiresAt(null);
            } else {
                e.setExpiresAt(req.expiresAt());
            }
            needEvict = true;
        }

        if (req.clearRedirectStatusCode() != null && req.clearRedirectStatusCode()) {
            if (req.redirectStatusCode() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "clearRedirectStatusCode=true 时不允许同时传 redirectStatusCode");
            }
            if (e.getRedirectStatusCode() != null) {
                e.setRedirectStatusCode(null);
                needEvict = true;
            }
        } else if (req.redirectStatusCode() != null) {
            Integer v = validateRedirectStatus(req.redirectStatusCode());
            if ((v == null && e.getRedirectStatusCode() != null) || (v != null && !v.equals(e.getRedirectStatusCode()))) {
                e.setRedirectStatusCode(v);
                needEvict = true;
            }
        }
        if (req.previewEnabled() != null && !req.previewEnabled().equals(e.getPreviewEnabled())) {
            e.setPreviewEnabled(req.previewEnabled());
            needEvict = true;
        }
        if (req.unavailableLandingUrl() != null) {
            // 约定：显式传空字符串可清空
            String url = normalize(req.unavailableLandingUrl());
            String v = validateOptionalHttpUrl(url, "unavailableLandingUrl");
            if ((v == null && e.getUnavailableLandingUrl() != null) || (v != null && !v.equals(e.getUnavailableLandingUrl()))) {
                e.setUnavailableLandingUrl(v);
                needEvict = true;
            }
        }
        if (req.clearQueryForwardMode() != null && req.clearQueryForwardMode()) {
            if (req.queryForwardMode() != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "clearQueryForwardMode=true 时不允许同时传 queryForwardMode");
            }
            if (e.getQueryForwardMode() != null) {
                e.setQueryForwardMode(null);
                needEvict = true;
            }
        } else if (req.queryForwardMode() != null) {
            String v = normalizeQueryForwardMode(req.queryForwardMode());
            if ((v == null && e.getQueryForwardMode() != null) || (v != null && !v.equals(e.getQueryForwardMode()))) {
                e.setQueryForwardMode(v);
                needEvict = true;
            }
        }
        if (req.queryForwardAllowlist() != null) {
            String v = normalizeAllowlist(req.queryForwardAllowlist());
            if ((v == null && e.getQueryForwardAllowlist() != null) || (v != null && !v.equals(e.getQueryForwardAllowlist()))) {
                e.setQueryForwardAllowlist(v);
                needEvict = true;
            }
        }

        shortLinkRepository.save(e);

        if (req.tags() != null) {
            setTags(tenantId, linkId, req.tags());
        }

        String code = e.getCode();
        linkCacheOutboxRepository.enqueueRefresh(code);
        LinkMeta meta = toMeta(e);
        boolean evict = needEvict;
        AfterCommit.run(() -> {
            if (evict) {
                linkCache.tryEvict(code);
            }
            linkCache.tryPut(meta);
        });
        return toDto(tenantId, e, loadTagsByLinkId(linkId));
    }

    public List<TagDto> listTags(long tenantId) {
        tenantGuard.requireCurrentTenant(tenantId);
        return tagRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(t -> new TagDto(t.getId(), t.getName()))
                .toList();
    }

    @Transactional
    public TagDto createTag(long tenantId, String name) {
        tenantGuard.requireCurrentTenant(tenantId);
        String n = normalize(name);
        if (n == null || n.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        if (n.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名过长");
        }
        TagEntity existing = tagRepository.findByTenantIdAndName(tenantId, n).orElse(null);
        if (existing != null) {
            return new TagDto(existing.getId(), existing.getName());
        }
        long id = idGenerator.nextId();
        TagEntity t = new TagEntity();
        t.setId(id);
        t.setTenantId(tenantId);
        t.setName(n);
        tagRepository.save(t);
        return new TagDto(t.getId(), t.getName());
    }

    @Transactional
    public ImportResult importCsv(long tenantId, long createdBy, java.io.InputStream inputStream) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (inputStream == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 输入流不能为空");
        }
        List<String> errors = new ArrayList<>();
        int success = 0;
        int failed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord r : parser) {
                try {
                    String originalUrl = r.get("originalUrl");
                    String code = safeGet(r, "code");
                    String expiresAt = safeGet(r, "expiresAt");
                    String note = safeGet(r, "note");
                    String tags = safeGet(r, "tags");

                    LocalDateTime exp = parseDateTime(expiresAt);
                    Set<String> tagSet = splitTags(tags);
                    CreateLinkRequest req = new CreateLinkRequest(
                            originalUrl,
                            note,
                            exp,
                            null,
                            code,
                            tagSet,
                            null,
                            null,
                            null,
                            null,
                            null
                    );
                    create(tenantId, createdBy, req);
                    success++;
                } catch (Exception e) {
                    failed++;
                    errors.add("line " + r.getRecordNumber() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 解析失败");
        }

        return new ImportResult(success, failed, errors);
    }

    public void exportCsv(long tenantId, Pageable pageable, java.io.OutputStream os) {
        tenantGuard.requireCurrentTenant(tenantId);
        exportCsv(tenantId, pageable, new OutputStreamWriter(os, StandardCharsets.UTF_8));
    }

    public void exportCsv(long tenantId, Pageable pageable, Writer writer) {
        tenantGuard.requireCurrentTenant(tenantId);
        // MVP：导出按分页拉取；如需全量导出可改为游标/分片
        Page<ShortLinkEntity> page = shortLinkRepository.search(tenantId, false, null, null, null, pageable);
        Map<Long, List<String>> tags = loadTagsByLinkIds(page.getContent().stream().map(ShortLinkEntity::getId).toList());

        try (CSVPrinter printer = new CSVPrinter(
                writer,
                CSVFormat.DEFAULT.builder()
                        .setHeader("id", "code", "originalUrl", "note", "enabled", "expiresAt", "tags")
                        .build()
        )) {
            for (ShortLinkEntity e : page.getContent()) {
                printer.printRecord(
                        e.getId(),
                        e.getCode(),
                        e.getOriginalUrl(),
                        e.getNote(),
                        e.getEnabled(),
                        e.getExpiresAt(),
                        String.join(",", tags.getOrDefault(e.getId(), List.of()))
                );
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "导出失败");
        }
    }

    private void setTags(long tenantId, long linkId, Set<String> tags) {
        linkTagRepository.deleteAllByIdLinkId(linkId);
        if (tags == null || tags.isEmpty()) {
            return;
        }

        Set<String> normalized = tags.stream()
                .map(ShortLinkService::normalize)
                .filter(s -> s != null && !s.isBlank())
                .limit(20)
                .collect(Collectors.toCollection(HashSet::new));

        Map<String, TagEntity> existing = new HashMap<>();
        for (String tag : normalized) {
            tagRepository.findByTenantIdAndName(tenantId, tag).ifPresent(t -> existing.put(tag, t));
        }

        for (String name : normalized) {
            TagEntity t = existing.get(name);
            if (t == null) {
                long id = idGenerator.nextId();
                t = new TagEntity();
                t.setId(id);
                t.setTenantId(tenantId);
                t.setName(name);
                tagRepository.save(t);
            }
            linkTagRepository.save(new LinkTagEntity(new LinkTagId(linkId, t.getId())));
        }
    }

    private List<String> loadTagsByLinkId(long linkId) {
        return linkTagRepository.findAllByLinkIdFetchTag(linkId).stream()
                .map(lt -> lt.getTag().getName())
                .toList();
    }

    private Map<Long, List<String>> loadTagsByLinkIds(Collection<Long> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> map = new HashMap<>();
        for (LinkTagEntity lt : linkTagRepository.findAllByLinkIdsFetchTag(linkIds)) {
            map.computeIfAbsent(lt.getId().getLinkId(), k -> new ArrayList<>()).add(lt.getTag().getName());
        }
        return map;
    }

    private static LinkMeta toMeta(ShortLinkEntity e) {
        return new LinkMeta(
                e.getId(),
                e.getTenantId(),
                e.getCode(),
                e.getOriginalUrl(),
                Boolean.TRUE.equals(e.getEnabled()),
                e.getExpiresAt(),
                e.getRedirectStatusCode(),
                Boolean.TRUE.equals(e.getPreviewEnabled()),
                e.getUnavailableLandingUrl(),
                e.getQueryForwardMode(),
                e.getQueryForwardAllowlist()
        );
    }

    private LinkDto toDto(long tenantId, ShortLinkEntity e, List<String> tags) {
        return new LinkDto(
                e.getId(),
                tenantId,
                e.getCode(),
                buildShortUrl(e.getCode()),
                e.getOriginalUrl(),
                e.getNote(),
                Boolean.TRUE.equals(e.getEnabled()),
                e.getExpiresAt(),
                e.getArchivedAt(),
                e.getRedirectStatusCode(),
                Boolean.TRUE.equals(e.getPreviewEnabled()),
                e.getUnavailableLandingUrl(),
                e.getQueryForwardMode(),
                splitAllowlist(e.getQueryForwardAllowlist()),
                tags,
                e.getCreatedAt()
        );
    }

    private String buildShortUrl(String code) {
        String base = appProperties.getBaseUrl();
        if (base == null) {
            base = "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/r/" + code;
    }

    private void ensureCodeAvailable(String code) {
        if (shortLinkRepository.findByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.CODE_ALREADY_EXISTS);
        }
    }

    private static void validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "短码不能为空");
        }
        if (code.length() < 6 || code.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "短码长度需为 6-32");
        }
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z');
            if (!ok) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "短码仅允许 [0-9A-Za-z]");
            }
        }
    }

    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Integer validateRedirectStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status != 301 && status != 302) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "redirectStatusCode 仅支持 301/302");
        }
        return status;
    }

    private String normalizeQueryForwardMode(String raw) {
        String v = normalize(raw);
        if (v == null) {
            return null;
        }
        String t = v.toUpperCase();
        if (!("OFF".equals(t) || "ALLOWLIST".equals(t) || "ALL".equals(t))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "queryForwardMode 仅支持 OFF/ALLOWLIST/ALL");
        }
        return t;
    }

    private String normalizeAllowlist(List<String> list) {
        if (list == null) {
            return null;
        }
        List<String> out = list.stream()
                .map(ShortLinkService::normalize)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .limit(50)
                .toList();
        if (out.isEmpty()) {
            return null;
        }
        for (String p : out) {
            if (!isValidParamPattern(p)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "queryForwardAllowlist 包含不合法项: " + p);
            }
        }
        String joined = String.join(",", out);
        if (joined.length() > 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "queryForwardAllowlist 过长");
        }
        return joined;
    }

    private static List<String> splitAllowlist(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return List.of();
        }
        String[] parts = s.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = normalize(p);
            if (t != null) {
                out.add(t);
            }
        }
        return out;
    }

    private String validateOptionalHttpUrl(String url, String fieldName) {
        if (url == null) {
            return null;
        }
        // 复用 API 侧 URL 校验器（http/https + 基础合法性）
        try {
            urlValidator.validateHttpUrl(url);
            return url;
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " URL 不合法");
        }
    }

    private static boolean isValidParamPattern(String p) {
        if (p == null || p.isBlank()) {
            return false;
        }
        String t = p.trim();
        if ("*".equals(t)) {
            return false;
        }
        // 允许 utm_* 这类前缀通配；仅允许最后一位为 '*'
        boolean star = t.endsWith("*");
        String base = star ? t.substring(0, t.length() - 1) : t;
        if (base.isBlank()) {
            return false;
        }
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || ch == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static String safeGet(CSVRecord r, String key) {
        try {
            return r.isMapped(key) ? r.get(key) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            // 兼容仅日期
            try {
                return LocalDate.parse(s).atStartOfDay();
            } catch (Exception ignored) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "expiresAt 格式错误（建议 ISO-8601）");
            }
        }
    }

    private static Set<String> splitTags(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return Set.of();
        }
        String[] parts = s.split(",");
        Set<String> out = new HashSet<>();
        for (String p : parts) {
            String n = normalize(p);
            if (n != null && !n.isBlank()) {
                out.add(n);
            }
        }
        return out;
    }

    public record CreateLinkRequest(
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

    public record UpdateLinkRequest(
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

    public record LinkDto(
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

    public record TagDto(long id, String name) {
    }

    public record ImportResult(int success, int failed, List<String> errors) {
    }
}
