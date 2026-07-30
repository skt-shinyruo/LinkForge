package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalRequester;
import com.linkforge.contract.governance.ApprovalSubmissionPort;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.context.UserActor;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.ShortLinkUserAccess;
import com.linkforge.shortlink.application.UpdateLinkRequest;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.QueryForwardAllowlist;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 更新短链可变属性，并处理应用短链目标地址变更的审批分支。
 *
 * <p>处理器先按租户读取聚合，再通过 {@link ShortLinkUserAccess} 校验用户是否能访问该短链。
 * 应用短链的目标地址发生变化时，只允许单独提交该变化：处理器创建审批单并返回待审批视图，
 * 不直接修改短链、标签，也不发布短链事件或清理缓存。未触发审批时，聚合更新和标签替换位于同一事务，
 * 仓储以版本号执行乐观锁；过期写会令整个事务回滚。</p>
 *
 * <p>成功直写后会在事务内发布领域事件并登记缓存失效 outbox，提交后再执行一次 best-effort 缓存清理；
 * 即时清理失败由 outbox 重试承担可靠性。</p>
 */
@Component
public class UpdateShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final SetLinkTagsCommandHandler setLinkTagsHandler;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final LinkTagRepository linkTagRepository;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;
    private final ApprovalSubmissionPort approvalSubmissionPort;

    public UpdateShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            SetLinkTagsCommandHandler setLinkTagsHandler,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            LinkTagRepository linkTagRepository,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            ShortLinkDtoMapper dtoMapper,
            PostCommitHookPort postCommitHookPort,
            Clock clock,
            ApprovalSubmissionPort approvalSubmissionPort
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.setLinkTagsHandler = setLinkTagsHandler;
        this.domainEventDispatcher = domainEventDispatcher;
        this.linkTagRepository = linkTagRepository;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.dtoMapper = dtoMapper;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
        this.approvalSubmissionPort = approvalSubmissionPort;
    }

    /**
     * 更新短链，或在需要治理审批时仅提交目标地址变更申请。
     *
     * <p>所有 {@code clearXxx=true} 均表示显式清空；同一次请求若还提供对应新值会被拒绝，
     * 包括 {@code clearExpiresAt + expiresAt}、{@code clearRedirectStatusCode + redirectStatusCode}
     * 和 {@code clearQueryForwardMode + queryForwardMode}。重复直写需携带从当前聚合读取的版本，
     * 并发修改失败会返回 {@code LINK_STALE_WRITE}，调用方应重新读取后决定是否重试。</p>
     *
     * @param tenantId 当前租户，必须与用户和短链归属一致
     * @param linkId 待更新短链
     * @param req 部分更新参数，不能为 {@code null}
     * @param actor 当前用户；用于短链访问控制和审批申请人审计
     * @param requestedAt 审批申请时间；仅审批分支使用
     * @return 更新后的短链，或原短链附带待审批信息的视图
     * @throws BusinessException 权限、不变量、清空字段冲突或乐观锁校验失败时抛出
     */
    @Transactional
    public LinkDto handle(long tenantId, long linkId, UpdateLinkRequest req, UserActor actor, LocalDateTime requestedAt) {
        if (req == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "UpdateLinkRequest 不能为空");
        }
        if (Boolean.TRUE.equals(req.clearExpiresAt()) && req.expiresAt() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "clearExpiresAt=true 时不允许同时传 expiresAt");
        }
        if (Boolean.TRUE.equals(req.clearRedirectStatusCode()) && req.redirectStatusCode() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "clearRedirectStatusCode=true 时不允许同时传 redirectStatusCode");
        }
        if (Boolean.TRUE.equals(req.clearQueryForwardMode()) && req.queryForwardMode() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "clearQueryForwardMode=true 时不允许同时传 queryForwardMode");
        }
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));
        ShortLinkUserAccess.requireCanAccess(actor, link);

        try {
            link.requireNotArchivedForUpdate();
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        boolean appAwareLink = link.applicationId() != null && link.domainId() != null;
        boolean requiresDestinationApproval = req.originalUrl() != null
                && appAwareLink
                && !link.originalUrl().value().equals(req.originalUrl());

        List<String> existingTags = null;
        if (requiresDestinationApproval) {
            if (actor == null || actor.userId() <= 0 || actor.email() == null || actor.email().isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "actor 无效");
            }
            if (actor.tenantId() != tenantId) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "actor 租户不匹配");
            }
            String requestedOriginalUrl = normalizeOriginalUrlForApproval(req.originalUrl());
            existingTags = linkTagRepository.findTagNamesByLinkId(linkId);
            if (hasOtherEffectiveChangesForApproval(link, req, existingTags)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请先单独提交目标地址变更，再保存其他修改");
            }
            var approval = approvalSubmissionPort.requestLinkDestinationChangeApproval(
                    tenantId,
                    new ApprovalSubmissionPort.LinkDestinationChangeApprovalRequest(
                            linkId,
                            link.applicationId(),
                            link.originalUrl().value(),
                            requestedOriginalUrl,
                            new ApprovalRequester(actor.tenantId(), actor.userId(), actor.email()),
                            requestedAt
                    )
            );
            return dtoMapper.toDto(link, existingTags)
                    .withPendingApproval(approval == null ? null : approval.id(), requestedOriginalUrl);
        }

        if (req.lifecycleState() != null) {
            try {
                link.setLifecycleState(ShortLinkLifecycleState.parseNullable(req.lifecycleState()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "lifecycleState 不合法");
            }
        }

        if (req.originalUrl() != null) {
            try {
                link.changeOriginalUrl(HttpUrl.of(req.originalUrl()));
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }
        if (req.note() != null) {
            try {
                link.changeNote(req.note());
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }
        if (req.enabled() != null) {
            link.setEnabled(req.enabled());
        }
        if (Boolean.TRUE.equals(req.clearExpiresAt())) {
            link.clearExpiresAtUtc();
        } else if (req.expiresAt() != null) {
            LocalDateTime expiresAtUtc = req.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime();
            link.setExpiresAtUtc(expiresAtUtc);
        }

        if (Boolean.TRUE.equals(req.clearRedirectStatusCode())) {
            link.clearRedirectStatusCode();
        } else if (req.redirectStatusCode() != null) {
            try {
                link.setRedirectStatusCode(req.redirectStatusCode());
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }

        if (req.previewEnabled() != null) {
            link.setPreviewEnabled(req.previewEnabled());
        }
        if (req.unavailableLandingUrl() != null) {
            // 该字段没有独立 clear 标志，显式空字符串表示清空。
            String normalized = normalizeNullable(req.unavailableLandingUrl());
            if (normalized == null) {
                link.clearUnavailableLandingUrl();
            } else {
                try {
                    link.setUnavailableLandingUrl(HttpUrl.of(normalized));
                } catch (ShortLinkDomainException ex) {
                    throw ShortLinkDomainExceptions.translate(ex);
                }
            }
        }

        if (Boolean.TRUE.equals(req.clearQueryForwardMode())) {
            link.clearQueryForwardMode();
        } else if (req.queryForwardMode() != null) {
            try {
                link.setQueryForwardMode(QueryForwardMode.parseNullable(req.queryForwardMode()));
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }

        if (req.queryForwardAllowlist() != null) {
            try {
                link.setQueryForwardAllowlist(QueryForwardAllowlist.fromRaw(req.queryForwardAllowlist()));
            } catch (ShortLinkDomainException ex) {
                throw ShortLinkDomainExceptions.translate(ex);
            }
        }

        if (!shortLinkRepository.update(link)) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
        link.incrementVersion();

        if (req.tags() != null) {
            setLinkTagsHandler.handle(tenantId, linkId, req.tags());
        }

        LocalDateTime updatedAtUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        link.markUpdated(updatedAtUtc);
        domainEventDispatcher.publish(link, updatedAtUtc.toInstant(ZoneOffset.UTC));
        RedirectCacheInvalidations.enqueueAndRunAfterCommit(
                redirectCacheInvalidationOutbox,
                postCommitHookPort,
                redirectCacheSync,
                link.tenantId(),
                link.domainId(),
                link.code().value()
        );

        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }

    private static boolean hasOtherEffectiveChangesForApproval(ShortLink link, UpdateLinkRequest req, List<String> existingTags) {
        try {
            return hasOtherEffectiveChanges(link, req, existingTags);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "lifecycleState 不合法");
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
    }

    private static String normalizeOriginalUrlForApproval(String originalUrl) {
        try {
            return HttpUrl.of(originalUrl).value();
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
    }

    private static boolean hasOtherEffectiveChanges(ShortLink link, UpdateLinkRequest req, List<String> existingTags) {
        return lifecycleStateChanged(link, req)
                || noteChanged(link, req)
                || enabledChanged(link, req)
                || expiresAtChanged(link, req)
                || redirectStatusCodeChanged(link, req)
                || previewEnabledChanged(link, req)
                || unavailableLandingUrlChanged(link, req)
                || queryForwardModeChanged(link, req)
                || queryForwardAllowlistChanged(link, req)
                || tagsChanged(req, existingTags);
    }

    private static boolean lifecycleStateChanged(ShortLink link, UpdateLinkRequest req) {
        if (req.lifecycleState() == null) {
            return false;
        }
        return ShortLinkLifecycleState.parseNullable(req.lifecycleState()) != link.lifecycleState();
    }

    private static boolean noteChanged(ShortLink link, UpdateLinkRequest req) {
        return req.note() != null && !Objects.equals(link.note(), req.note());
    }

    private static boolean enabledChanged(ShortLink link, UpdateLinkRequest req) {
        return req.enabled() != null && req.enabled() != link.enabled();
    }

    private static boolean expiresAtChanged(ShortLink link, UpdateLinkRequest req) {
        if (Boolean.TRUE.equals(req.clearExpiresAt())) {
            return link.expiresAtUtc() != null;
        }
        if (req.expiresAt() == null) {
            return false;
        }
        return !Objects.equals(link.expiresAtUtc(), req.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    private static boolean redirectStatusCodeChanged(ShortLink link, UpdateLinkRequest req) {
        if (Boolean.TRUE.equals(req.clearRedirectStatusCode())) {
            return link.redirectStatusCode() != null;
        }
        return req.redirectStatusCode() != null && !Objects.equals(link.redirectStatusCode(), req.redirectStatusCode());
    }

    private static boolean previewEnabledChanged(ShortLink link, UpdateLinkRequest req) {
        return req.previewEnabled() != null && req.previewEnabled() != link.previewEnabled();
    }

    private static boolean unavailableLandingUrlChanged(ShortLink link, UpdateLinkRequest req) {
        if (req.unavailableLandingUrl() == null) {
            return false;
        }
        String current = link.unavailableLandingUrl() == null ? null : link.unavailableLandingUrl().value();
        return !Objects.equals(current, normalizeNullable(req.unavailableLandingUrl()));
    }

    private static boolean queryForwardModeChanged(ShortLink link, UpdateLinkRequest req) {
        if (Boolean.TRUE.equals(req.clearQueryForwardMode())) {
            return link.queryForwardMode() != null;
        }
        if (req.queryForwardMode() == null) {
            return false;
        }
        return QueryForwardMode.parseNullable(req.queryForwardMode()) != link.queryForwardMode();
    }

    private static boolean queryForwardAllowlistChanged(ShortLink link, UpdateLinkRequest req) {
        if (req.queryForwardAllowlist() == null) {
            return false;
        }
        Set<String> current = Set.copyOf(link.queryForwardAllowlist().values());
        Set<String> requested = Set.copyOf(QueryForwardAllowlist.fromRaw(req.queryForwardAllowlist()).values());
        return !current.equals(requested);
    }

    private static boolean tagsChanged(UpdateLinkRequest req, List<String> existingTags) {
        if (req.tags() == null) {
            return false;
        }
        Set<String> current = existingTags == null ? Set.of() : Set.copyOf(existingTags);
        return !current.equals(req.tags());
    }

    private static String normalizeNullable(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
