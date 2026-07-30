package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.domain.ShortLink;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 将已归档短链恢复为可用状态。
 *
 * <p>恢复写入、领域事件和缓存失效 outbox 在同一事务内完成，并以版本号防止覆盖并发修改。
 * 事务提交后执行一次 best-effort 缓存清理，失败由 outbox 重试。该处理器只执行租户级仓储隔离，
 * 用户是否有恢复权限必须由上游入口保证。</p>
 */
@Component
public class RestoreShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkDomainEventDispatcher domainEventDispatcher;
    private final LinkTagRepository linkTagRepository;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public RestoreShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            ShortLinkDomainEventDispatcher domainEventDispatcher,
            LinkTagRepository linkTagRepository,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            ShortLinkDtoMapper dtoMapper,
            PostCommitHookPort postCommitHookPort,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.domainEventDispatcher = domainEventDispatcher;
        this.linkTagRepository = linkTagRepository;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.dtoMapper = dtoMapper;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
    }

    /**
     * 恢复指定短链。
     *
     * <p>未归档短链的重复恢复不产生写入、事件或缓存任务。实际发生状态转换时，仓储版本不匹配会返回
     * {@code LINK_STALE_WRITE}，调用方需要重新读取最新状态。</p>
     *
     * @param tenantId 短链所属租户
     * @param linkId 待恢复短链
     * @return 恢复后的短链及其标签；无需恢复时返回现有状态
     * @throws BusinessException 短链不存在或发生乐观锁冲突时抛出
     */
    @Transactional
    public LinkDto handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        boolean restored = link.restore();
        if (restored) {
            Instant occurredAtUtc = clock.instant();
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            link.incrementVersion();
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

        List<String> tags = linkTagRepository.findTagNamesByLinkId(linkId);
        return dtoMapper.toDto(link, tags);
    }
}
