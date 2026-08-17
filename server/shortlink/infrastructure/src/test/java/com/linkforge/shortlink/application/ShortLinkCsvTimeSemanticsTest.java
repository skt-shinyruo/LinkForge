package com.linkforge.shortlink.application;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.tx.RequiresNewTransactionPort;
import com.linkforge.shortlink.application.command.CreateShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.ImportShortLinksCsvCommandHandler;
import com.linkforge.shortlink.application.csv.ShortLinkCsvExport;
import com.linkforge.shortlink.application.csv.ShortLinkCsvImportRow;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.query.ExportShortLinksCsvQueryHandler;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import com.linkforge.shortlink.domain.ShortLinkLifecycleState;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkCsvTimeSemanticsTest {

    @Test
    void importCsv_should_accept_instant_offset_and_legacy_localdatetime_as_utc() {
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        when(createHandler.handle(anyLong(), any(), any())).thenReturn(null);

        RequiresNewTransactionPort requiresNewTransactionPort = Runnable::run;
        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(createHandler, requiresNewTransactionPort);

        long tenantId = 1L;
        long userId = 1L;
        CreatedBy createdBy = CreatedBy.user(userId);
        ImportResult result = handler.handle(
                tenantId,
                createdBy,
                List.of(
                        new ShortLinkCsvImportRow(1L, "https://example.com/1", null, "2026-03-10T12:00:00Z", null, null),
                        new ShortLinkCsvImportRow(2L, "https://example.com/2", null, "2026-03-10T04:00:00Z", null, null),
                        new ShortLinkCsvImportRow(3L, "https://example.com/3", null, "2026-03-10T12:00:00", null, null)
                )
        );

        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.success()).isEqualTo(3);
        assertThat(result.errors()).isEmpty();

        ArgumentCaptor<CreateLinkRequest> reqCaptor = ArgumentCaptor.forClass(CreateLinkRequest.class);
        verify(createHandler, times(3)).handle(eq(tenantId), eq(createdBy), reqCaptor.capture());

        List<CreateLinkRequest> reqs = reqCaptor.getAllValues();
        assertThat(reqs).hasSize(3);

        Object expiresAt1 = reqs.get(0).expiresAt();
        assertThat(expiresAt1).isInstanceOf(Instant.class);
        assertThat(expiresAt1).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));

        Object expiresAt2 = reqs.get(1).expiresAt();
        assertThat(expiresAt2).isInstanceOf(Instant.class);
        assertThat(expiresAt2).isEqualTo(Instant.parse("2026-03-10T04:00:00Z"));

        Object expiresAt3 = reqs.get(2).expiresAt();
        assertThat(expiresAt3).isInstanceOf(Instant.class);
        assertThat(expiresAt3).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
    }

    @Test
    void importCsv_shouldAggregateMalformedRowsWithoutAbortingBatch() {
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        when(createHandler.handle(anyLong(), any(), any())).thenReturn(null);

        RequiresNewTransactionPort requiresNewTransactionPort = Runnable::run;
        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(createHandler, requiresNewTransactionPort);

        ImportResult result = handler.handle(
                1L,
                CreatedBy.user(1L),
                List.of(
                        new ShortLinkCsvImportRow(1L, "https://example.com/1", "code-1", "2026-03-10T12:00:00Z", null, "marketing,spring"),
                        new ShortLinkCsvImportRow(2L, "https://example.com/2", "code-2", "not-a-date", null, null),
                        new ShortLinkCsvImportRow(3L, "https://example.com/3", "code-3", null, null, null)
                )
        );

        assertThat(result.success()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).containsExactly(
                "line 2: expiresAt 格式错误（需 ISO-8601 Instant，例如 2026-03-10T12:00:00Z 或 2026-03-10T12:00:00+08:00；或 legacy LocalDateTime 例如 2026-03-10T12:00:00，按 UTC 处理）"
        );

        verify(createHandler, times(2)).handle(anyLong(), any(), any());
    }

    @Test
    void importCsv_shouldInjectScopedApplicationAndDomainIntoEveryCreateRequest() {
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        when(createHandler.handle(anyLong(), any(), any())).thenReturn(null);

        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(createHandler, Runnable::run);

        ImportResult result = handler.handle(
                1L,
                CreatedBy.user(1L),
                List.of(new ShortLinkCsvImportRow(1L, "https://example.com/1", "code-1", null, null, null)),
                2001L,
                3001L
        );

        assertThat(result.failed()).isEqualTo(0);

        ArgumentCaptor<CreateLinkRequest> reqCaptor = ArgumentCaptor.forClass(CreateLinkRequest.class);
        verify(createHandler).handle(eq(1L), eq(CreatedBy.user(1L)), reqCaptor.capture());
        assertThat(reqCaptor.getValue().applicationId()).isEqualTo(2001L);
        assertThat(reqCaptor.getValue().domainId()).isEqualTo(3001L);
    }

    @Test
    void importCsv_shouldUseRowScopeWhenNoRequestScopeIsProvided() {
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        when(createHandler.handle(anyLong(), any(), any())).thenReturn(null);

        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(createHandler, Runnable::run);

        ImportResult result = handler.handle(
                1L,
                CreatedBy.user(1L),
                List.of(new ShortLinkCsvImportRow(
                        1L,
                        "2001",
                        "3001",
                        "go.example.test",
                        "https://example.com/1",
                        "code-1",
                        null,
                        null,
                        null
                ))
        );

        assertThat(result.failed()).isEqualTo(0);

        ArgumentCaptor<CreateLinkRequest> reqCaptor = ArgumentCaptor.forClass(CreateLinkRequest.class);
        verify(createHandler).handle(eq(1L), eq(CreatedBy.user(1L)), reqCaptor.capture());
        assertThat(reqCaptor.getValue().applicationId()).isEqualTo(2001L);
        assertThat(reqCaptor.getValue().domainId()).isEqualTo(3001L);
    }

    @Test
    void importCsv_shouldResolveDomainIdFromHostnameWhenRowDomainIdIsMissing() {
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        DomainHostnameLookupPort domainHostnameLookupPort = mock(DomainHostnameLookupPort.class);
        when(createHandler.handle(anyLong(), any(), any())).thenReturn(null);
        when(domainHostnameLookupPort.findDomainIdByHostname(1L, "go.example.test")).thenReturn(Optional.of(3001L));

        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(
                createHandler,
                Runnable::run,
                domainHostnameLookupPort
        );

        ImportResult result = handler.handle(
                1L,
                CreatedBy.user(1L),
                List.of(new ShortLinkCsvImportRow(
                        1L,
                        "2001",
                        null,
                        " Go.Example.Test ",
                        "https://example.com/1",
                        "code-1",
                        null,
                        null,
                        null
                ))
        );

        assertThat(result.failed()).isEqualTo(0);

        ArgumentCaptor<CreateLinkRequest> reqCaptor = ArgumentCaptor.forClass(CreateLinkRequest.class);
        verify(createHandler).handle(eq(1L), eq(CreatedBy.user(1L)), reqCaptor.capture());
        assertThat(reqCaptor.getValue().applicationId()).isEqualTo(2001L);
        assertThat(reqCaptor.getValue().domainId()).isEqualTo(3001L);
        verify(domainHostnameLookupPort).findDomainIdByHostname(1L, "go.example.test");
    }

    @Test
    void importCsv_shouldFailRowWhenHostnameDoesNotMatchDomain() {
        CreateShortLinkCommandHandler createHandler = mock(CreateShortLinkCommandHandler.class);
        DomainHostnameLookupPort domainHostnameLookupPort = mock(DomainHostnameLookupPort.class);
        when(domainHostnameLookupPort.findDomainIdByHostname(1L, "missing.example.test")).thenReturn(Optional.empty());

        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(
                createHandler,
                Runnable::run,
                domainHostnameLookupPort
        );

        ImportResult result = handler.handle(
                1L,
                CreatedBy.user(1L),
                List.of(new ShortLinkCsvImportRow(
                        7L,
                        "2001",
                        null,
                        "missing.example.test",
                        "https://example.com/1",
                        "code-1",
                        null,
                        null,
                        null
                ))
        );

        assertThat(result.success()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).containsExactly("line 7: hostname 对应域名不存在");
        verify(createHandler, times(0)).handle(anyLong(), any(), any());
    }

    @Test
    void exportCsv_should_output_expiresAt_as_utc_instant_and_null_when_blank() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);

        ExportShortLinksCsvQueryHandler handler = new ExportShortLinksCsvQueryHandler(
                shortLinkRepository,
                linkTagRepository
        );

        LocalDateTime expiresAtUtc = LocalDateTime.of(2026, 3, 10, 12, 0, 0);
        ShortLink withExpiresAt = ShortLink.rehydrate(
                1L,
                1L,
                null,
                null,
                ShortCode.of("abcdef"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/1"),
                null,
                true,
                expiresAtUtc,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                1L,
                0L,
                LocalDateTime.of(2026, 3, 1, 0, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0, 0)
        );
        ShortLink noExpiresAt = ShortLink.rehydrate(
                2L,
                1L,
                null,
                null,
                ShortCode.of("ghijkl"),
                ShortLinkLifecycleState.ACTIVE,
                HttpUrl.of("https://example.com/2"),
                null,
                true,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                1L,
                0L,
                LocalDateTime.of(2026, 3, 1, 0, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0, 0)
        );

        when(shortLinkRepository.listSearch(eq(1L), any(), anyLong(), anyInt()))
                .thenReturn(List.of(withExpiresAt, noExpiresAt));
        when(linkTagRepository.findTagNamesByLinkIds(any())).thenReturn(List.of());

        ShortLinkCsvExport export = handler.handle(1L, new ShortLinkSearchQuery(false, null, null, null, null), new PageQuery(0, 10));

        assertThat(export.rows()).hasSize(2);
        assertThat(export.rows().get(0).expiresAt()).isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
        assertThat(export.rows().get(1).expiresAt()).isNull();
    }

    @Test
    void exportCsv_shouldIncludeApplicationDomainAndHostnameForRoundTrip() {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        DomainHostnameLookupPort domainHostnameLookupPort = mock(DomainHostnameLookupPort.class);

        ExportShortLinksCsvQueryHandler handler = new ExportShortLinksCsvQueryHandler(
                shortLinkRepository,
                linkTagRepository,
                domainHostnameLookupPort
        );

        ShortLink link = ShortLink.rehydrate(
                1L,
                1L,
                2001L,
                3001L,
                ShortCode.of("abcdef"),
                null,
                HttpUrl.of("https://example.com/1"),
                null,
                true,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                1L,
                0L,
                LocalDateTime.of(2026, 3, 1, 0, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0, 0)
        );

        when(shortLinkRepository.listSearch(eq(1L), any(), anyLong(), anyInt())).thenReturn(List.of(link));
        when(linkTagRepository.findTagNamesByLinkIds(any())).thenReturn(List.of());
        when(domainHostnameLookupPort.findDomainHostname(1L, 3001L)).thenReturn(Optional.of("go.example.test"));

        ShortLinkCsvExport export = handler.handle(1L, new ShortLinkSearchQuery(false, null, null, null, 2001L), new PageQuery(0, 10));

        assertThat(export.rows()).hasSize(1);
        assertThat(export.rows().get(0).applicationId()).isEqualTo(2001L);
        assertThat(export.rows().get(0).domainId()).isEqualTo(3001L);
        assertThat(export.rows().get(0).hostname()).isEqualTo("go.example.test");
    }

}
