package com.linkforge.shortlink.application.approval;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import com.linkforge.contract.governance.ApprovalExecutionPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.ApprovalPayloadTypes;
import com.linkforge.contract.governance.LinkDestinationChangeApprovalPayload;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * 执行“公开短链目标地址变更”审批的 Shortlink 上下文适配器。
 *
 * <p>本类不是审批权限边界。Governance 上下文负责校验审批人、抢占待审批状态并在其事务中调用本执行器；
 * 本类只验证发布契约与当前短链是否仍匹配。{@code beforeSnapshot} 和 {@code afterSnapshot} 必须都是
 * {@code linkDestinationChange/v1} JSON payload，且携带相同的正数 {@code linkId}。旧文本格式、未知类型或
 * 未支持版本会稳定作为参数错误拒绝，不能降级解析。</p>
 *
 * <p>执行前会按请求租户读取短链，并检查目标应用与当前目标地址仍匹配审批快照。聚合命名行为负责已绑定域名、
 * {@code ACTIVE} 发布阶段和未归档守卫；持久化更新还带聚合版本条件，因而快照校验之后的并发写入也会以
 * {@code LINK_STALE_WRITE} 失败，而不会覆盖新数据。</p>
 *
 * <p>短链更新、集成事件 durable append 和缓存失效 outbox 登记参与调用方事务，任一步失败都向上传播。
 * 事务提交后还会尝试一次 best-effort 缓存驱逐；该快路径失败不会撤销业务提交，最终收敛由 outbox worker
 * 重试保证。重试同一审批执行通常会因 before 快照不再匹配而失败，不提供“重复执行仍返回成功”的幂等语义。</p>
 */
@Component
public class LinkDestinationChangeApprovalExecutor implements ApprovalExecutionPort {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final PostCommitHookPort postCommitHookPort;

    public LinkDestinationChangeApprovalExecutor(
            ShortLinkRepository shortLinkRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            PostCommitHookPort postCommitHookPort
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.postCommitHookPort = postCommitHookPort;
    }

    /**
     * 仅声明支持公开短链目标地址变更；执行器选择与唯一性校验由 Governance 上下文负责。
     */
    @Override
    public boolean supports(SensitiveOperation operation) {
        return operation == SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE;
    }

    /**
     * 校验版本化审批快照仍适用于当前聚合，并提交目标地址变更及其可靠异步副作用。
     *
     * @param request Governance 发布的执行请求；租户、应用和前后快照均会参与一致性校验
     * @param executedAt 审批执行时间，按 UTC 解释并写入更新事件
     * @throws BusinessException payload 非法、短链不存在、审批目标已变化或乐观锁冲突时抛出
     */
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

        boolean changed;
        try {
            changed = link.approveDestinationChange(HttpUrl.of(after.originalUrl()), executedAt);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
        if (!changed) {
            return;
        }
        if (!shortLinkRepository.update(link)) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }

        domainEventDispatcher.publish(link, executedAt.toInstant(ZoneOffset.UTC));
        RedirectCacheInvalidations.enqueueAndRunAfterCommit(
                redirectCacheInvalidationOutbox,
                postCommitHookPort,
                redirectCacheSync,
                link.tenantId(),
                link.domainId(),
                link.code().value()
        );
    }

    /**
     * 将审批时的业务前置条件与当前聚合重新比较，避免已批准请求作用于后来改变了 scope 或目标地址的短链。
     */
    private static void validateApprovalStillMatchesLink(
            ApprovalExecutionRequest request,
            LinkDestinationSnapshot before,
            ShortLink link
    ) {
        if (!Objects.equals(request.targetApplicationId(), link.applicationId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批目标应用与短链不匹配");
        }
        if (!Objects.equals(link.originalUrl().value(), before.originalUrl())) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE, "短链目标地址已变化，请重新提交审批");
        }
    }

    /** 经过类型、版本及必填字段校验后的最小目标地址快照。 */
    private record LinkDestinationSnapshot(long linkId, String originalUrl) {

        /**
         * 严格解析 v1 发布 payload；解析失败不尝试兼容历史自由文本，防止审批含义发生歧义。
         */
        private static LinkDestinationSnapshot parse(String snapshot, String fieldName) {
            if (snapshot == null || snapshot.isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 缺少审批快照");
            }
            LinkDestinationChangeApprovalPayload payload;
            try {
                payload = ApprovalPayloadCodec.read(snapshot, LinkDestinationChangeApprovalPayload.class);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 审批 payload 不合法");
            }
            if (!ApprovalPayloadTypes.LINK_DESTINATION_CHANGE.equals(payload.type())
                    || payload.version() != ApprovalPayloadTypes.VERSION_1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 审批 payload 版本不支持");
            }
            if (payload.linkId() <= 0 || payload.originalUrl() == null || payload.originalUrl().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + " 缺少短链目标地址变更信息");
            }
            return new LinkDestinationSnapshot(payload.linkId(), payload.originalUrl());
        }
    }
}
