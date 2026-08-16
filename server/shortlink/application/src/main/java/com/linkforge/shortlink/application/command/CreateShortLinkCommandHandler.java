package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.platform.ApplicationScopePort;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.id.SnowflakeIdGenerator;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.foundation.util.Base62;
import com.linkforge.shortlink.application.CreatedBy;
import com.linkforge.shortlink.application.CreateLinkRequest;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.ApplicationLinkQuotaReservationPort;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
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

/**
 * 创建短链聚合并协调应用作用域、额度、标签、集成事件和跳转缓存失效。
 *
 * <p>应用短链必须同时提供 {@code applicationId} 与 {@code domainId}，并在写入前通过应用/域名授权及
 * 月度发链额度检查；未绑定应用和域名的历史作用域仍按仓储规则创建。聚合、标签、集成事件发布端口和
 * 缓存失效 outbox 在同一事务中执行。事务提交后会立即尝试清理跳转缓存；快路径失败不会回滚业务事务，
 * 后续由已持久化的 outbox 重试。</p>
 *
 * <p>创建命令没有幂等键，自动码的重复请求会创建新短链。自定义码先做友好冲突检查，数据库唯一约束
 * 仍是并发竞争的最终防线，并将冲突稳定映射为 {@code CODE_ALREADY_EXISTS}。</p>
 */
@Component
public class CreateShortLinkCommandHandler {

    private final SnowflakeIdGenerator idGenerator;
    private final ShortLinkRepository shortLinkRepository;
    private final ApplicationLinkQuotaReservationPort applicationLinkQuotaReservationPort;
    private final SetLinkTagsCommandHandler setLinkTagsHandler;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkEventPublisher eventPublisher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;
    private final ApplicationScopePort applicationScopePort;

    public CreateShortLinkCommandHandler(
            SnowflakeIdGenerator idGenerator,
            ShortLinkRepository shortLinkRepository,
            ApplicationLinkQuotaReservationPort applicationLinkQuotaReservationPort,
            SetLinkTagsCommandHandler setLinkTagsHandler,
            LinkTagRepository linkTagRepository,
            ShortLinkEventPublisher eventPublisher,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            ShortLinkDtoMapper dtoMapper,
            PostCommitHookPort postCommitHookPort,
            Clock clock,
            ApplicationScopePort applicationScopePort
    ) {
        this.idGenerator = idGenerator;
        this.shortLinkRepository = shortLinkRepository;
        this.applicationLinkQuotaReservationPort = applicationLinkQuotaReservationPort;
        this.setLinkTagsHandler = setLinkTagsHandler;
        this.linkTagRepository = linkTagRepository;
        this.eventPublisher = eventPublisher;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.dtoMapper = dtoMapper;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
        this.applicationScopePort = applicationScopePort;
    }

    /**
     * 在一个事务中创建短链及其附属标签，并登记事件与缓存失效任务。
     *
     * @param tenantId 短链所属租户
     * @param createdBy 创建主体；必须具有正 ID 和明确主体类型
     * @param req 创建参数；应用 ID 与域名 ID 必须同时提供或同时省略
     * @return 已持久化短链及标签的当前视图
     * @throws BusinessException 主体、作用域、额度、短码或聚合不变量校验失败时抛出
     */
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
                boolean reserved = applicationLinkQuotaReservationPort.tryReserveMonthlyLink(
                        tenantId,
                        applicationId,
                        monthStart,
                        fromInclusiveUtc,
                        toExclusiveUtc,
                        monthlyLinkLimit
                );
                if (!reserved) {
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
        eventPublisher.created(link, clock.instant());
        RedirectCacheInvalidations.enqueueAndRunAfterCommit(
                redirectCacheInvalidationOutbox,
                postCommitHookPort,
                redirectCacheSync,
                persisted.tenantId(),
                persisted.domainId(),
                persisted.code().value()
        );

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
