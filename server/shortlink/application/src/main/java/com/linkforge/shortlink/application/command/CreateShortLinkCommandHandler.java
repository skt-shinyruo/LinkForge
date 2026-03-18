package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.foundation.tx.AfterCommit;
import com.linkforge.foundation.util.Base62;
import com.linkforge.shortlink.application.ShortLinkService.CreatedBy;
import com.linkforge.shortlink.application.ShortLinkService.CreateLinkRequest;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.QueryForwardAllowlist;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class CreateShortLinkCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final ShortLinkRepository shortLinkRepository;
    private final SetLinkTagsCommandHandler setLinkTagsHandler;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkEventPublisher eventPublisher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final ShortLinkDtoMapper dtoMapper;
    private final TenantGuard tenantGuard;
    private final Clock clock;

    public CreateShortLinkCommandHandler(
            SnowflakeIdGenerator idGenerator,
            ShortLinkRepository shortLinkRepository,
            SetLinkTagsCommandHandler setLinkTagsHandler,
            LinkTagRepository linkTagRepository,
            ShortLinkEventPublisher eventPublisher,
            RedirectCacheSyncPort redirectCacheSync,
            ShortLinkDtoMapper dtoMapper,
            TenantGuard tenantGuard,
            Clock clock
    ) {
        this.idGenerator = idGenerator;
        this.shortLinkRepository = shortLinkRepository;
        this.setLinkTagsHandler = setLinkTagsHandler;
        this.linkTagRepository = linkTagRepository;
        this.eventPublisher = eventPublisher;
        this.redirectCacheSync = redirectCacheSync;
        this.dtoMapper = dtoMapper;
        this.tenantGuard = tenantGuard;
        this.clock = clock;
    }

    @Transactional
    public LinkDto handle(long tenantId, CreatedBy createdBy, CreateLinkRequest req) {
        tenantGuard.requireCurrentTenant(tenantId);
        if (createdBy == null || createdBy.id() <= 0 || createdBy.type() == null) {
            throw new BusinessException(com.linkforge.contract.api.ErrorCode.UNAUTHORIZED, "createdBy 无效");
        }
        if (req == null) {
            throw new BusinessException(com.linkforge.contract.api.ErrorCode.BAD_REQUEST, "CreateLinkRequest 不能为空");
        }

        String customCodeRaw = normalizeNullable(req.customCode());
        boolean custom = customCodeRaw != null;

        long id = idGenerator.nextId();
        String codeRaw = custom ? customCodeRaw : Base62.encode(id);

        if (custom) {
            ShortCode code = parseCode(codeRaw);
            if (shortLinkRepository.findByCode(code.value()).isPresent()) {
                throw new BusinessException(ShortLinkErrorCode.CODE_ALREADY_EXISTS);
            }
        }

        ShortLink link;
        try {
            LocalDateTime expiresAtUtc = req.expiresAt() == null
                    ? null
                    : req.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime();
            link = ShortLink.create(
                    id,
                    tenantId,
                    parseCode(codeRaw),
                    HttpUrl.of(req.originalUrl()),
                    req.note(),
                    req.enabled(),
                    expiresAtUtc,
                    req.redirectStatusCode(),
                    req.previewEnabled(),
                    parseOptionalHttpUrl(req.unavailableLandingUrl()),
                    QueryForwardMode.parseNullable(req.queryForwardMode()),
                    QueryForwardAllowlist.fromRaw(req.queryForwardAllowlist()),
                    createdBy.type(),
                    createdBy.id()
            );
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        try {
            shortLinkRepository.insert(link);
        } catch (DataIntegrityViolationException ex) {
            if (custom) {
                throw new BusinessException(ShortLinkErrorCode.CODE_ALREADY_EXISTS);
            }
            throw ex;
        }

        ShortLink persisted = shortLinkRepository.findByTenantIdAndId(tenantId, id).orElse(link);

        setLinkTagsHandler.handle(tenantId, id, req.tags());
        eventPublisher.created(persisted, clock.instant());
        AfterCommit.run(() -> redirectCacheSync.evict(persisted.code().value()));

        List<String> tags = linkTagRepository.findTagNamesByLinkId(id);
        return dtoMapper.toDto(persisted, tags);
    }

    private static ShortCode parseCode(String raw) {
        try {
            return ShortCode.of(raw);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
    }

    private static HttpUrl parseOptionalHttpUrl(String raw) {
        String s = normalizeNullable(raw);
        if (s == null) {
            return null;
        }
        try {
            return HttpUrl.of(s);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
