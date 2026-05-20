package com.linkforge.shortlink.application.approval;

import com.linkforge.foundation.tx.PostCommitHookPort;
import com.linkforge.contract.governance.ApprovalExecutionRequest;
import com.linkforge.contract.governance.ApprovalPayloadCodec;
import com.linkforge.contract.governance.LinkDestinationChangeApprovalPayload;
import com.linkforge.contract.governance.SensitiveOperation;
import com.linkforge.shortlink.application.eventing.ShortLinkDomainEventDispatcher;
import com.linkforge.shortlink.application.port.RedirectCacheInvalidationOutboxPort;
import com.linkforge.shortlink.application.port.RedirectCacheSyncPort;
import com.linkforge.shortlink.application.port.ShortLinkEventPublisher;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkDestinationChangeApprovalExecutorTest {

    @Test
    void constructor_shouldDependOnDomainEventDispatcherInsteadOfEventPublisher() {
        Constructor<?> constructor = LinkDestinationChangeApprovalExecutor.class.getDeclaredConstructors()[0];

        assertThat(constructor.getParameterTypes())
                .contains(ShortLinkDomainEventDispatcher.class);
        assertThat(constructor.getParameterTypes())
                .doesNotContain(ShortLinkEventPublisher.class);
    }

    @Test
    void execute_shouldRejectLegacyTextPayload() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        LinkDestinationChangeApprovalExecutor executor = new LinkDestinationChangeApprovalExecutor(
                shortLinkRepository,
                domainEventDispatcher,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
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
        ApprovalExecutionRequest request = new ApprovalExecutionRequest(
                501L,
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                "linkId=101\noriginalUrl=https://example.com/old",
                "linkId=101\noriginalUrl=https://example.com/new"
        );
        LocalDateTime executedAt = LocalDateTime.parse("2026-04-01T01:02:03");

        assertThatThrownBy(() -> executor.execute(request, executedAt))
                .hasMessageContaining("beforeSnapshot 审批 payload 不合法");

        assertThat(executor.supports(SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE)).isTrue();
        assertThat(link.originalUrl().value()).isEqualTo("https://example.com/old");
        verify(shortLinkRepository, never()).update(link);
        verify(domainEventDispatcher, never()).publish(any(), any());
    }

    @Test
    void execute_shouldApplyApprovedDestinationChangeFromVersionedStructuredPayload() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        ShortLinkDomainEventDispatcher domainEventDispatcher = mock(ShortLinkDomainEventDispatcher.class);
        RedirectCacheSyncPort redirectCacheSync = mock(RedirectCacheSyncPort.class);
        PostCommitHookPort postCommitHookPort = mock(PostCommitHookPort.class);
        LinkDestinationChangeApprovalExecutor executor = new LinkDestinationChangeApprovalExecutor(
                shortLinkRepository,
                domainEventDispatcher,
                redirectCacheSync,
                mock(RedirectCacheInvalidationOutboxPort.class),
                postCommitHookPort
        );
        ShortLink link = ShortLink.create(
                102L,
                1L,
                2001L,
                3001L,
                ShortCode.of("governed2"),
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
        ApprovalExecutionRequest request = new ApprovalExecutionRequest(
                502L,
                1L,
                SensitiveOperation.PUBLIC_LINK_DESTINATION_CHANGE,
                2001L,
                linkDestinationPayload(102L, "https://example.com/old"),
                linkDestinationPayload(102L, "https://example.com/new")
        );
        LocalDateTime executedAt = LocalDateTime.parse("2026-04-01T01:02:03");
        when(shortLinkRepository.findByTenantIdAndId(1L, 102L)).thenReturn(Optional.of(link));
        when(shortLinkRepository.update(link)).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(postCommitHookPort).run(any(Runnable.class));

        executor.execute(request, executedAt);

        assertThat(link.originalUrl().value()).isEqualTo("https://example.com/new");
        verify(shortLinkRepository).update(link);
        verify(domainEventDispatcher).publish(link, executedAt.toInstant(java.time.ZoneOffset.UTC));
        verify(redirectCacheSync).evict(1L, 3001L, "governed2");
    }

    private static String linkDestinationPayload(long linkId, String originalUrl) {
        return ApprovalPayloadCodec.write(LinkDestinationChangeApprovalPayload.v1(linkId, originalUrl));
    }
}
