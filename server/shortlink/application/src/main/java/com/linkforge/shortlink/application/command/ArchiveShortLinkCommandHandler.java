package com.linkforge.shortlink.application.command;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.shortlink.ShortLinkErrorCode;
import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.shortlink.application.LinkDto;
import com.linkforge.shortlink.application.mapper.ShortLinkDtoMapper;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.support.RedirectCacheInvalidations;
import com.linkforge.shortlink.application.support.ShortLinkDomainExceptions;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 将短链转换为归档状态。
 *
 * <p>归档状态变更、集成事件和缓存失效 outbox 位于同一事务，仓储更新使用聚合版本做乐观锁。
 * 提交后会立即尝试清理跳转缓存，失败时由 outbox 后台重试。该处理器只按租户隔离数据，
 * 不接收用户主体，调用方必须在进入命令前完成操作权限校验。</p>
 */
@Component
public class ArchiveShortLinkCommandHandler {

    private final ShortLinkRepository shortLinkRepository;
    private final ShortLinkEventPublisher eventPublisher;
    private final LinkTagRepository linkTagRepository;
    private final RedirectCacheSyncPort redirectCacheSync;
    private final RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox;
    private final ShortLinkDtoMapper dtoMapper;
    private final PostCommitHookPort postCommitHookPort;
    private final Clock clock;

    public ArchiveShortLinkCommandHandler(
            ShortLinkRepository shortLinkRepository,
            ShortLinkEventPublisher eventPublisher,
            LinkTagRepository linkTagRepository,
            RedirectCacheSyncPort redirectCacheSync,
            RedirectCacheInvalidationOutboxPort redirectCacheInvalidationOutbox,
            ShortLinkDtoMapper dtoMapper,
            PostCommitHookPort postCommitHookPort,
            Clock clock
    ) {
        this.shortLinkRepository = shortLinkRepository;
        this.eventPublisher = eventPublisher;
        this.linkTagRepository = linkTagRepository;
        this.redirectCacheSync = redirectCacheSync;
        this.redirectCacheInvalidationOutbox = redirectCacheInvalidationOutbox;
        this.dtoMapper = dtoMapper;
        this.postCommitHookPort = postCommitHookPort;
        this.clock = clock;
    }

    /**
     * 归档指定短链。
     *
     * <p>已经归档的短链会直接返回当前视图，不重复写库、发事件或清缓存；首次状态转换若遇到并发版本变化，
     * 则整个事务失败并返回 {@code LINK_STALE_WRITE}。</p>
     *
     * @param tenantId 短链所属租户
     * @param linkId 待归档短链
     * @return 归档后的短链及其标签；重复归档返回现有状态
     * @throws BusinessException 短链不存在、状态不允许或发生乐观锁冲突时抛出
     */
    @Transactional
    public LinkDto handle(long tenantId, long linkId) {
        ShortLink link = shortLinkRepository.findByTenantIdAndId(tenantId, linkId)
                .orElseThrow(() -> new BusinessException(ShortLinkErrorCode.LINK_NOT_FOUND));

        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        boolean archived;
        try {
            archived = link.archive(nowUtc);
        } catch (ShortLinkDomainException ex) {
            throw ShortLinkDomainExceptions.translate(ex);
        }
        if (archived) {
            if (!shortLinkRepository.update(link)) {
                throw new BusinessException(ShortLinkErrorCode.LINK_STALE_WRITE);
            }
            eventPublisher.archived(link, nowUtc.toInstant(ZoneOffset.UTC));
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
