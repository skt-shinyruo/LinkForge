package com.linkforge.shortlink.application.approval;

import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.governance.domain.ApprovalRequest;
import com.linkforge.governance.domain.ApprovalStatus;
import com.linkforge.governance.domain.SensitiveOperationType;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkDestinationChangeApprovalExecutorTest {

    @Test
    void execute_shouldApplyApprovedDestinationChangeAndEvictRedirectCache() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ShortLinkEventPublisher eventPublisher = mock(ShortLinkEventPublisher.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        LinkDestinationChangeApprovalExecutor executor = new LinkDestinationChangeApprovalExecutor(
                shortLinkRepository,
                eventPublisher,
                redirectCacheSync,
                postCommitHookPort
        );
        ShortLink link = ShortLink.create(
                101L,
                1L,
                2001L,
                3001L,
                ShortCode.of("governed"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/old"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                9L
        );
        ApprovalRequest request = new ApprovalRequest(
                501L,
                1L,
                SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                7L,
                "requester@example.com",
                ApprovalStatus.PENDING_APPROVAL,
                null,
                null,
                null,
                "linkId=101\noriginalUrl=https://example.com/old",
                "linkId=101\noriginalUrl=https://example.com/new",
                LocalDateTime.parse("2026-04-01T00:00:00"),
                null,
                null
        );
        LocalDateTime executedAt = LocalDateTime.parse("2026-04-01T01:02:03");
        when(shortLinkRepository.findByTenantIdAndId(1L, 101L)).thenReturn(Optional.of(link));
        when(shortLinkRepository.update(link)).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(postCommitHookPort).run(any(Runnable.class));

        executor.execute(request, executedAt);

        assertThat(executor.supports(SensitiveOperationType.PUBLIC_LINK_DESTINATION_CHANGE)).isTrue();
        assertThat(link.originalUrl().value()).isEqualTo("https://example.com/new");
        verify(shortLinkRepository).update(link);
        verify(eventPublisher).updated(link, Instant.parse("2026-04-01T01:02:03Z"));
        verify(redirectCacheSync).evict(1L, 3001L, "governed");
    }
}
