package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.foundation.util.Base62;
import com.linkforge.shortlink.application.ShortLinkService.CreatedBy;
import com.linkforge.shortlink.application.ShortLinkService.CreateLinkRequest;
import com.linkforge.shortlink.application.ShortLinkService.LinkDto;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.QueryForwardAllowlist;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class CreateShortLinkCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final ShortLinkRepository shortLinkRepository;
    private final SetLinkTagsCommandHandler setLinkTagsHandler;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;

    public CreateShortLinkCommandHandler(
            SnowflakeIdGenerator idGenerator,
            ShortLinkRepository shortLinkRepository,
            SetLinkTagsCommandHandler setLinkTagsHandler,
            LinkTagRepository linkTagRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            RedirectCacheSyncPort redirectCacheSync,
            ShortLinkDtoMapper dtoMapper,
            PostCommitHookPort postCommitHookPort,
            Clock clock,
            ApplicationScopePort applicationScopePort
    ) {
        this.idGenerator = idGenerator;
        this.shortLinkRepository = shortLinkRepository;
        this.setLinkTagsHandler = setLinkTagsHandler;
        this.linkTagRepository = linkTagRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.redirectCacheSync = redirectCacheSync;
        this.dtoMapper = dtoMapper;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
        this.applicationScopePort = applicationScopePort;
    }

    @Transactional
    public LinkDto handle(long tenantId, CreatedBy createdBy, CreateLinkRequest req) {
        if (createdBy == null || createdBy.id() <= 0 || createdBy.type() == null) {
            throw new BusinessException(com.linkforge.contract.api.ErrorCode.UNAUTHORIZED, "createdBy 无效");
        }
        if (req == null) {
            throw new BusinessException(com.linkforge.contract.api.ErrorCode.BAD_REQUEST, "CreateLinkRequest 不能为空");
        }

        String customCodeRaw = normalizeNullable(req.customCode());
        boolean custom = customCodeRaw != null;
        Long applicationId = req.applicationId();
        Long domainId = req.domainId();
        ShortLinkLifecycleState lifecycleState = parseLifecycleState(req.lifecycleState());

        if ((applicationId == null) != (domainId == null)) {
            throw new BusinessException(com.linkforge.contract.api.ErrorCode.BAD_REQUEST, "applicationId 与 domainId 必须同时提供");
        }
        if (applicationId != null) {
            applicationScopePort.requireApplicationAndDomainAuthorized(tenantId, applicationId, domainId);
            applicationScopePort.findApplicationQuota(tenantId, applicationId).ifPresent(quota -> {
                long monthlyLinkLimit = quota.monthlyLinkLimit();
                if (monthlyLinkLimit <= 0) {
                    return;
                }
                LocalDate monthStart = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1);
                LocalDateTime fromInclusiveUtc = monthStart.atStartOfDay();
                LocalDateTime toExclusiveUtc = monthStart.plusMonths(1).atStartOfDay();
                long currentMonthCreated = shortLinkRepository.countCreatedByTenantIdAndApplicationIdAndCreatedAtRange(
                        tenantId,
                        applicationId,
                        fromInclusiveUtc,
                        toExclusiveUtc
                );
                if (currentMonthCreated >= monthlyLinkLimit) {
                    throw new BusinessException(com.linkforge.contract.api.ErrorCode.FORBIDDEN, "应用发链额度已用尽");
                }
            });
        }

        long id = idGenerator.nextId();
        String codeRaw = custom ? customCodeRaw : Base62.encode(id);

        if (custom) {
            ShortCode code = parseCode(codeRaw);
            boolean exists = domainId == null
                    ? shortLinkRepository.findUnscopedByCode(code.value()).isPresent()
                    : shortLinkRepository.findByDomainIdAndCode(domainId, code.value()).isPresent();
            if (exists) {
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
                    applicationId,
                    domainId,
                    parseCode(codeRaw),
                    lifecycleState,
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
        domainEventDispatcher.publish(link, clock.instant());
        postCommitHookPort.run(() -> redirectCacheSync.evict(persisted.tenantId(), persisted.domainId(), persisted.code().value()));

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

    private static ShortLinkLifecycleState parseLifecycleState(String raw) {
        try {
            return ShortLinkLifecycleState.parseNullable(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "lifecycleState 无效");
        }
    }
}
