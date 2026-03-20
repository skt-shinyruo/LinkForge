package com.linkforge.shortlink.application;

import com.linkforge.foundation.persistence.PageQuery;
import com.linkforge.foundation.runtime.security.TenantGuard;
import com.linkforge.shortlink.application.command.CreateShortLinkCommandHandler;
import com.linkforge.shortlink.application.command.ImportShortLinksCsvCommandHandler;
import com.linkforge.shortlink.application.port.LinkTagRepository;
import com.linkforge.shortlink.application.port.ShortLinkRepository;
import com.linkforge.shortlink.application.query.ExportShortLinksCsvQueryHandler;
import com.linkforge.shortlink.application.query.ShortLinkSearchQuery;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

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

        TenantGuard tenantGuard = mock(TenantGuard.class);
        PlatformTransactionManager txManager = new NoOpTransactionManager();
        ImportShortLinksCsvCommandHandler handler = new ImportShortLinksCsvCommandHandler(createHandler, tenantGuard, txManager);

        String csv = """
                originalUrl,code,expiresAt,note,tags
                https://example.com/1,,2026-03-10T12:00:00Z,,
                https://example.com/2,,2026-03-10T12:00:00+08:00,,
                https://example.com/3,,2026-03-10T12:00:00,,
                """;

        long tenantId = 1L;
        long userId = 1L;
        ShortLinkService.CreatedBy createdBy = ShortLinkService.CreatedBy.user(userId);
        ShortLinkService.ImportResult result = handler.handle(
                tenantId,
                createdBy,
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.success()).isEqualTo(3);
        assertThat(result.errors()).isEmpty();

        ArgumentCaptor<ShortLinkService.CreateLinkRequest> reqCaptor = ArgumentCaptor.forClass(ShortLinkService.CreateLinkRequest.class);
        verify(createHandler, times(3)).handle(eq(tenantId), eq(createdBy), reqCaptor.capture());

        List<ShortLinkService.CreateLinkRequest> reqs = reqCaptor.getAllValues();
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
    void exportCsv_should_output_expiresAt_as_utc_instant_string_and_blank_when_null() throws Exception {
        ShortLinkRepository shortLinkRepository = mock(ShortLinkRepository.class);
        LinkTagRepository linkTagRepository = mock(LinkTagRepository.class);
        TenantGuard tenantGuard = mock(TenantGuard.class);

        ExportShortLinksCsvQueryHandler handler = new ExportShortLinksCsvQueryHandler(
                shortLinkRepository,
                linkTagRepository,
                tenantGuard
        );

        LocalDateTime expiresAtUtc = LocalDateTime.of(2026, 3, 10, 12, 0, 0);
        ShortLink withExpiresAt = ShortLink.rehydrate(
                1L,
                1L,
                ShortCode.of("abcdef"),
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
                ShortCode.of("ghijkl"),
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

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        handler.handle(1L, new ShortLinkSearchQuery(false, null, null, null, null), new PageQuery(0, 10), os);

        String out = os.toString(StandardCharsets.UTF_8);
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new StringReader(out))) {
            List<CSVRecord> records = parser.getRecords();
            assertThat(records).hasSize(2);

            assertThat(records.get(0).get("expiresAt")).isEqualTo("2026-03-10T12:00:00Z");
            assertThat(records.get(1).get("expiresAt")).isBlank();
        }
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
