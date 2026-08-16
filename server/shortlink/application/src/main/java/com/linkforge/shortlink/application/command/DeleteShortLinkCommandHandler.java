package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 物理删除已经归档的短链，并同步删除标签关联、发布删除事件和失效跳转缓存。
 *
 * <p>聚合规则强制“先归档、后删除”。标签关联删除、带版本条件的短链删除、领域事件和缓存失效 outbox
 * 位于同一事务，任一步骤失败都会整体回滚。提交后缓存清理作为 best-effort 快路径执行，失败由 outbox
 * 重试。该处理器仅以租户作为数据隔离边界，用户删除权限由上游校验。</p>
 */
@Component
public class DeleteShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final LinkTagRepository linkTagRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public DeleteShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            LinkTagRepository linkTagRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            PostCommitHookPort postCommitHookPort,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.linkTagRepository = linkTagRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
    }

    /**
     * 删除指定的已归档短链。
     *
     * <p>删除使用聚合版本进行条件写；并发修改或删除会返回 {@code LINK_STALE_WRITE}。
     * 本命令不是“删除不存在即成功”的幂等接口，重复调用将在仓储读取阶段返回 {@code LINK_NOT_FOUND}。</p>
     *
     * @param tenantId 短链所属租户
     * @param linkId 待删除短链
     * @throws BusinessException 短链不存在、尚未归档或发生乐观锁冲突时抛出
     */
    @Transactional
    public void handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        Instant occurredAtUtc = clock.instant();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(occurredAtUtc, ZoneOffset.UTC);
        try {
            link.delete(nowUtc);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }

        linkTagRepository.deleteAllByLinkId(linkId);
        if (!shortLinkRepository.delete(link)) {
            throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
        }
        domainEventDispatcher.publish(link, occurredAtUtc);
        RedirectCacheInvalidations.enqueueAndRunAfterCommit(
                redirectCacheInvalidationOutbox,
                postCommitHookPort,
                redirectCacheSync,
                link.tenantId(),
                link.domainId(),
                link.code().value()
        );
    }
}
