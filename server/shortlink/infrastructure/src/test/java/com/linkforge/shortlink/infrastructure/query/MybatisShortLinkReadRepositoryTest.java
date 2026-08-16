package com.linkforge.shortlink.infrastructure.query;

import com.linkforge.contract.redirect.LinkMeta;
import com.linkforge.contract.shortlink.ShortLinkReadPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.infrastructure.persistence.entity.ShortLinkEntity;
import com.linkforge.shortlink.infrastructure.persistence.mapper.ShortLinkQueryMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MybatisShortLinkReadRepositoryTest {

    @Test
    void findRedirectMetaByHostAndCode_shouldNormalizeHostAndUseHostAwareLookup() {
        ShortLinkQueryMapper queryMapper = mock(ShortLinkQueryMapper.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        MybatisShortLinkReadRepository repository = new MybatisShortLinkReadRepository(queryMapper, coreProperties);

        ShortLinkEntity row = redirectRow();
        row.setHostname("Alpha.Example.Test");
        when(queryMapper.findActiveByHostnameAndCode("alpha.example.test", "AbC123"))
                .thenReturn(row);

        Optional<LinkMeta> actual =
                repository.findRedirectMetaByHostAndCode(" Alpha.Example.Test:8443 ", "  AbC123  ");

        assertThat(actual).hasValueSatisfying(meta -> {
            assertThat(meta.tenantId()).isEqualTo(22L);
            assertThat(meta.id()).isEqualTo(11L);
            assertThat(meta.code()).isEqualTo("AbC123");
            assertThat(meta.hostname()).isEqualTo("alpha.example.test");
            assertThat(meta.originalUrl()).isEqualTo("https://example.com/live");
            assertThat(meta.enabled()).isTrue();
            assertThat(meta.expiresAt()).isEqualTo(LocalDateTime.parse("2026-03-18T10:15:30"));
            assertThat(meta.redirectStatusCode()).isEqualTo(302);
            assertThat(meta.previewEnabled()).isTrue();
            assertThat(meta.unavailableLandingUrl()).isEqualTo("https://example.com/unavailable");
            assertThat(meta.queryForwardMode()).isEqualTo("ALLOWLIST");
            assertThat(meta.queryForwardAllowlist()).isEqualTo("utm_source,utm_medium");
            assertThat(meta.applicationId()).isEqualTo(33L);
            assertThat(meta.domainId()).isEqualTo(44L);
            assertThat(meta.lifecycleState()).isEqualTo("ACTIVE");
        });

        verify(queryMapper).findActiveByHostnameAndCode("alpha.example.test", "AbC123");
        verifyNoMoreInteractions(queryMapper, coreProperties);
    }

    @Test
    void findRedirectMetaByHostAndCode_shouldFallbackToLegacyBaseHostLookup() {
        ShortLinkQueryMapper queryMapper = mock(ShortLinkQueryMapper.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        when(coreProperties.getBaseUrl()).thenReturn(" https://Go.Example.Test/base-path ");
        MybatisShortLinkReadRepository repository = new MybatisShortLinkReadRepository(queryMapper, coreProperties);

        when(queryMapper.findActiveByHostnameAndCode("go.example.test", "abc123"))
                .thenReturn(null);

        ShortLinkEntity row = redirectRow();
        row.setCode("abc123");
        row.setHostname("   ");
        when(queryMapper.findActiveByLegacyBaseHostAndCode("go.example.test", "abc123"))
                .thenReturn(row);

        Optional<LinkMeta> actual =
                repository.findRedirectMetaByHostAndCode("Go.Example.Test:443", "abc123");

        assertThat(actual).hasValueSatisfying(meta -> {
            assertThat(meta.hostname()).isEqualTo("go.example.test");
            assertThat(meta.applicationId()).isEqualTo(33L);
            assertThat(meta.domainId()).isEqualTo(44L);
        });

        verify(queryMapper).findActiveByHostnameAndCode("go.example.test", "abc123");
        verify(coreProperties).getBaseUrl();
        verify(queryMapper).findActiveByLegacyBaseHostAndCode("go.example.test", "abc123");
        verifyNoMoreInteractions(queryMapper, coreProperties);
    }

    @Test
    void findRedirectMetaByHostAndCode_shouldFallbackToUnscopedLinkOnBaseHost() {
        ShortLinkEntity row = redirectRow();
        row.setCode("abc123");
        row.setApplicationId(null);
        row.setDomainId(null);
        row.setHostname(null);
        ShortLinkQueryMapper queryMapper = mock(ShortLinkQueryMapper.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        when(coreProperties.getBaseUrl()).thenReturn(" https://Go.Example.Test/base-path ");
        when(queryMapper.findActiveUnscopedByCode("abc123")).thenReturn(row);
        MybatisShortLinkReadRepository repository = new MybatisShortLinkReadRepository(queryMapper, coreProperties);

        Optional<LinkMeta> actual =
                repository.findRedirectMetaByHostAndCode("Go.Example.Test:443", "abc123");

        assertThat(actual).hasValueSatisfying(meta -> {
            assertThat(meta.hostname()).isEqualTo("go.example.test");
            assertThat(meta.applicationId()).isNull();
            assertThat(meta.domainId()).isNull();
            assertThat(meta.originalUrl()).isEqualTo("https://example.com/live");
        });

        verify(queryMapper).findActiveByHostnameAndCode("go.example.test", "abc123");
        verify(coreProperties).getBaseUrl();
        verify(queryMapper).findActiveByLegacyBaseHostAndCode("go.example.test", "abc123");
        verify(queryMapper).findActiveUnscopedByCode("abc123");
        verifyNoMoreInteractions(queryMapper, coreProperties);
    }

    @Test
    void findOwnership_shouldMapApplicationAndDomainIds() {
        ShortLinkQueryMapper queryMapper = mock(ShortLinkQueryMapper.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        MybatisShortLinkReadRepository repository = new MybatisShortLinkReadRepository(queryMapper, coreProperties);
        ShortLinkEntity row = redirectRow();
        when(queryMapper.findByTenantIdAndId(22L, 11L)).thenReturn(row);

        Optional<ShortLinkReadPort.ShortLinkOwnership> actual = repository.findOwnership(22L, 11L);

        assertThat(actual).contains(new ShortLinkReadPort.ShortLinkOwnership(33L, 44L));
        verify(queryMapper).findByTenantIdAndId(22L, 11L);
        verifyNoMoreInteractions(queryMapper, coreProperties);
    }

    @Test
    void listSummaries_shouldReturnCurrentLinkMetadataByIds() {
        ShortLinkQueryMapper queryMapper = mock(ShortLinkQueryMapper.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        when(coreProperties.getBaseUrl()).thenReturn("https://console.example.test/app/");
        MybatisShortLinkReadRepository repository = new MybatisShortLinkReadRepository(queryMapper, coreProperties);
        ShortLinkEntity row1 = redirectRow();
        row1.setHostname("go.example.test");
        ShortLinkEntity row2 = redirectRow();
        row2.setId(12L);
        row2.setCode("xyz789");
        row2.setOriginalUrl("https://example.com/other");
        row2.setDomainId(null);
        row2.setHostname(null);
        when(queryMapper.listByTenantIdAndIds(22L, List.of(11L, 12L))).thenReturn(List.of(row1, row2));

        Map<Long, ShortLinkReadPort.ShortLinkSummary> actual = repository.listSummaries(22L, List.of(11L, 12L));

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.of(
                11L, new ShortLinkReadPort.ShortLinkSummary(
                        11L,
                        "AbC123",
                        "https://go.example.test/r/AbC123",
                        "https://example.com/live",
                        false
                ),
                12L, new ShortLinkReadPort.ShortLinkSummary(
                        12L,
                        "xyz789",
                        "https://console.example.test/app/r/xyz789",
                        "https://example.com/other",
                        false
                )
        ));
        verify(queryMapper).listByTenantIdAndIds(22L, List.of(11L, 12L));
        verify(coreProperties, times(2)).getBaseUrl();
        verifyNoMoreInteractions(queryMapper, coreProperties);
    }

    @Test
    void scopeLookups_shouldReturnCurrentLinkIds() {
        ShortLinkQueryMapper queryMapper = mock(ShortLinkQueryMapper.class);
        CoreProperties coreProperties = mock(CoreProperties.class);
        MybatisShortLinkReadRepository repository = new MybatisShortLinkReadRepository(queryMapper, coreProperties);
        when(queryMapper.listIdsByTenantIdAndApplicationId(22L, 33L)).thenReturn(List.of(11L, 12L));
        when(queryMapper.listIdsByTenantIdAndDomainId(22L, 44L)).thenReturn(List.of(11L));

        assertThat(repository.listLinkIdsByApplication(22L, 33L)).containsExactly(11L, 12L);
        assertThat(repository.listLinkIdsByDomain(22L, 44L)).containsExactly(11L);

        verify(queryMapper).listIdsByTenantIdAndApplicationId(22L, 33L);
        verify(queryMapper).listIdsByTenantIdAndDomainId(22L, 44L);
        verifyNoMoreInteractions(queryMapper, coreProperties);
    }

    private static ShortLinkEntity redirectRow() {
        ShortLinkEntity row = new ShortLinkEntity();
        row.setId(11L);
        row.setTenantId(22L);
        row.setApplicationId(33L);
        row.setDomainId(44L);
        row.setCode("AbC123");
        row.setOriginalUrl("https://example.com/live");
        row.setEnabled(true);
        row.setExpiresAt(LocalDateTime.parse("2026-03-18T10:15:30"));
        row.setRedirectStatusCode(302);
        row.setPreviewEnabled(true);
        row.setUnavailableLandingUrl("https://example.com/unavailable");
        row.setQueryForwardMode("ALLOWLIST");
        row.setQueryForwardAllowlist("utm_source,utm_medium");
        row.setLifecycleState("ACTIVE");
        return row;
    }
}
