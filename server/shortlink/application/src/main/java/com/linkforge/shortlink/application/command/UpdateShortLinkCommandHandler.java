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
import com.linkforge.shortlink.application.support.LinkTagSetNormalizer;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.PatchValue;
import com.linkforge.shortlink.domain.QueryForwardAllowlist;
import com.linkforge.shortlink.domain.QueryForwardMode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkChangeSet;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import com.linkforge.shortlink.domain.ShortLinkPatch;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
        NormalizedUpdate update = normalizeRequest(req);
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));
        ShortLinkUserAccess.requireCanAccess(actor, link);

        ShortLinkChangeSet changes;
        try {
            changes = link.planPatch(update.patch());
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        boolean appAwareLink = link.applicationId() != null && link.domainId() != null;
        boolean requiresDestinationApproval = appAwareLink
                && changes.changed(ShortLinkChangeSet.Field.ORIGINAL_URL);

        List<String> existingTags = null;
        boolean tagsChanged = false;
        if (requiresDestinationApproval || !update.tags().isUnchanged()) {
            existingTags = linkTagRepository.findTagNamesByLinkId(linkId);
            tagsChanged = !update.tags().isUnchanged()
                    && !Set.copyOf(existingTags).equals(update.tags().value());
        }
        if (requiresDestinationApproval) {
            if (actor == null || actor.userId() <= 0 || actor.email() == null || actor.email().isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "actor 无效");
            }
            if (actor.tenantId() != tenantId) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "actor 租户不匹配");
            }
            if (changes.hasChangesOtherThan(ShortLinkChangeSet.Field.ORIGINAL_URL) || tagsChanged) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "请先单独提交目标地址变更，再保存其他修改");
            }
            String requestedOriginalUrl = update.patch().originalUrl().value().value();
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

        if (!changes.hasChanges() && !tagsChanged) {
            if (existingTags == null) {
                existingTags = linkTagRepository.findTagNamesByLinkId(linkId);
            }
            return dtoMapper.toDto(link, existingTags);
        }

        LocalDateTime updatedAtUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            link.applyUpdate(update.patch(), tagsChanged, updatedAtUtc);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        if (!shortLinkRepository.update(link)) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }

        if (tagsChanged) {
            setLinkTagsHandler.handle(tenantId, linkId, update.tags().value());
        }

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

    private static NormalizedUpdate normalizeRequest(UpdateLinkRequest req) {
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

        PatchValue<ShortLinkLifecycleState> lifecycleState = PatchValue.unchanged();
        if (req.lifecycleState() != null) {
            try {
                lifecycleState = PatchValue.set(ShortLinkLifecycleState.parseNullable(req.lifecycleState()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "lifecycleState 不合法");
            }
        }

        try {
            PatchValue<QueryForwardMode> queryForwardMode = PatchValue.unchanged();
            if (Boolean.TRUE.equals(req.clearQueryForwardMode())) {
                queryForwardMode = PatchValue.clear();
            } else if (req.queryForwardMode() != null) {
                QueryForwardMode parsed = QueryForwardMode.parseNullable(req.queryForwardMode());
                queryForwardMode = parsed == null ? PatchValue.clear() : PatchValue.set(parsed);
            }

            PatchValue<HttpUrl> unavailableLandingUrl = PatchValue.unchanged();
            if (req.unavailableLandingUrl() != null) {
                String normalized = normalizeNullable(req.unavailableLandingUrl());
                unavailableLandingUrl = normalized == null
                        ? PatchValue.clear()
                        : PatchValue.set(HttpUrl.of(normalized));
            }

            ShortLinkPatch patch = new ShortLinkPatch(
                    req.originalUrl() == null ? PatchValue.unchanged() : PatchValue.set(HttpUrl.of(req.originalUrl())),
                    req.note() == null ? PatchValue.unchanged() : PatchValue.set(req.note()),
                    req.enabled() == null ? PatchValue.unchanged() : PatchValue.set(req.enabled()),
                    Boolean.TRUE.equals(req.clearExpiresAt())
                            ? PatchValue.clear()
                            : req.expiresAt() == null
                            ? PatchValue.unchanged()
                            : PatchValue.set(req.expiresAt().atOffset(ZoneOffset.UTC).toLocalDateTime()),
                    Boolean.TRUE.equals(req.clearRedirectStatusCode())
                            ? PatchValue.clear()
                            : req.redirectStatusCode() == null
                            ? PatchValue.unchanged()
                            : PatchValue.set(req.redirectStatusCode()),
                    req.previewEnabled() == null ? PatchValue.unchanged() : PatchValue.set(req.previewEnabled()),
                    unavailableLandingUrl,
                    queryForwardMode,
                    req.queryForwardAllowlist() == null
                            ? PatchValue.unchanged()
                            : PatchValue.set(QueryForwardAllowlist.fromRaw(req.queryForwardAllowlist())),
                    lifecycleState
            );
            PatchValue<Set<String>> tags = req.tags() == null
                    ? PatchValue.unchanged()
                    : PatchValue.set(LinkTagSetNormalizer.normalize(req.tags()));
            return new NormalizedUpdate(patch, tags);
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

    private record NormalizedUpdate(ShortLinkPatch patch, PatchValue<Set<String>> tags) {
    }
}
