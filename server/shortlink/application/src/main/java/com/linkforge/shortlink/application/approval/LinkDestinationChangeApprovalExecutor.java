package com.linkforge.shortlink.application.approval;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.governance.application.port.ApprovalExecutionPort;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.SensitiveOperationType;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
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
    private final ShortLinkEventPublisher eventPublisher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final PostCommitHookPort postCommitHookPort;

    public LinkDestinationChangeApprovalExecutor(
            ShortLinkRepository shortLinkRepository,
            ShortLinkEventPublisher eventPublisher,
            RedirectCacheSyncPort redirectCacheSync,
            PostCommitHookPort postCommitHookPort
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.eventPublisher = eventPublisher;
        this.redirectCacheSync = redirectCacheSync;
        this.postCommitHookPort = postCommitHookPort;
    }

    @Override
    public boolean supports(SensitiveOperationType operationType) {
        return operationType == SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE;
    }

    @Override
    public void execute(ApprovalRequest request, LocalDateTime executedAt) {
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

        eventPublisher.updated(link, executedAt.toInstant(ZoneOffset.UTC));
        postCommitHookPort.run(() -> redirectCacheSync.evict(link.tenantId(), link.domainId(), link.code().value()));
    }

    private static void validateApprovalStillMatchesLink(
            ApprovalRequest request,
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
            long linkId = 0L;
            String originalUrl = null;
            for (String line : snapshot.split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1);
                if ("linkId".equals(key)) {
                    linkId = parseLinkId(value, fieldName);
                } else if ("originalUrl".equals(key)) {
                    originalUrl = value;
                }
            }
            if (linkId <= 0 || originalUrl == null || originalUrl.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 缺少短链目标地址变更信息");
            }
            return new LinkDestinationSnapshot(linkId, originalUrl);
        }

        private static long parseLinkId(String value, String fieldName) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " linkId 不合法");
            }
        }
    }
}
