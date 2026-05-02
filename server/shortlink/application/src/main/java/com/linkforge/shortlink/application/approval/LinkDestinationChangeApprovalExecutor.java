package com.linkforge.shortlink.application.approval;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalExecutionPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalPayloads;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Component
public class LinkDestinationChangeApprovalExecutor implements ApprovalExecutionPort {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final PostCommitHookPort postCommitHookPort;

    public LinkDestinationChangeApprovalExecutor(
            ShortLinkRepository shortLinkRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            RedirectCacheSyncPort redirectCacheSync,
            PostCommitHookPort postCommitHookPort
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.redirectCacheSync = redirectCacheSync;
        this.postCommitHookPort = postCommitHookPort;
    }

    @Override
    public boolean supports(SensitiveOperation operation) {
        return operation == SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE;
    }

    @Override
    public void execute(ApprovalExecutionRequest request, LocalDateTime executedAt) {
        LinkDestinationSnapshot before = LinkDestinationSnapshot.parse(request.beforeSnapshot(), "beforeSnapshot");
        LinkDestinationSnapshot after = LinkDestinationSnapshot.parse(request.afterSnapshot(), "afterSnapshot");
        if (before.linkId() != after.linkId()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批快照 linkId 不一致");
        }

        ShortLink link = shortLinkRepository.findByTenantIdAndId(request.tenantId(), after.linkId())
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));
        validateApprovalStillMatchesLink(request, before, link);

        try {
            link.changeOriginalUrl(HttpUrl.of(after.originalUrl()));
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
        if (!shortLinkRepository.update(link)) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
        link.incrementVersion();

        link.markUpdated(executedAt);
        domainEventDispatcher.publish(link, executedAt.toInstant(ZoneOffset.UTC));
        postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
    }

    private static void validateApprovalStillMatchesLink(
            ApprovalExecutionRequest request,
            LinkDestinationSnapshot before,
            ShortLink link
    ) {
        if (!Objects.equals(request.targetApplicationId(), link.applicationId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批目标应用与短链不匹配");
        }
        if (link.domainId() == null || link.lifecycleState() != ShortLinkLifecycleState.ACTIVE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批目标短链状态已变化，请重新提交审批");
        }
        try {
            link.requireNotArchivedForUpdate();
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
        if (!Objects.equals(link.originalUrl().value(), before.originalUrl())) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE, "短链目标地址已变化，请重新提交审批");
        }
    }

    private record LinkDestinationSnapshot(long linkId, String originalUrl) {

        private static LinkDestinationSnapshot parse(String snapshot, String fieldName) {
            if (snapshot == null || snapshot.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 缺少审批快照");
            }
            ApprovalPayloads.LinkDestinationChangePayload payload;
            try {
                payload = ApprovalPayloadCodec.read(snapshot, ApprovalPayloads.LinkDestinationChangePayload.class);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 审批 payload 不合法");
            }
            if (!ApprovalPayloads.LINK_DESTINATION_CHANGE.equals(payload.type())
                    || payload.version() != ApprovalPayloads.VERSION_1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 审批 payload 版本不支持");
            }
            if (payload.linkId() <= 0 || payload.originalUrl() == null || payload.originalUrl().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 缺少短链目标地址变更信息");
            }
            return new LinkDestinationSnapshot(payload.linkId(), payload.originalUrl());
        }
    }
}
