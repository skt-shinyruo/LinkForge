package com.linkforge.shortlink.application.mapper;

import com.linkforge.contract.platform.DomainHostnameLookupPort;
import com.linkforge.foundation.config.CoreProperties;
import com.linkforge.shortlink.application.ShortLinkService;
import com.linkforge.shortlink.domain.CreatedByType;
import com.linkforge.shortlink.domain.HttpUrl;
import com.linkforge.shortlink.domain.ShortCode;
import com.linkforge.shortlink.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShortLinkDtoMapperTest {

    @Test
    void toDto_shouldUseDomainHostnameForDomainScopedShortUrl() {
        CoreProperties coreProperties = new CoreProperties();
        coreProperties.setBaseUrl("https://console.example.test/app/");
        DomainHostnameLookupPort domainHostnameLookupPort = mock(DomainHostnameLookupPort.class);
        when(domainHostnameLookupPort.findDomainHostname(22L, 44L))
                .thenReturn(Optional.of("go.example.test"));
        ShortLinkDtoMapper mapper = new ShortLinkDtoMapper(coreProperties, domainHostnameLookupPort);

        ShortLinkService.LinkDto actual = mapper.toDto(domainScopedLink(), List.of());

        assertThat(actual.shortUrl()).isEqualTo("https://go.example.test/r/AbC123");
        verify(domainHostnameLookupPort).findDomainHostname(22L, 44L);
    }

    @Test
    void toDto_shouldKeepBaseUrlForUnscopedShortUrl() {
        CoreProperties coreProperties = new CoreProperties();
        coreProperties.setBaseUrl("https://go.example.test/");
        DomainHostnameLookupPort domainHostnameLookupPort = mock(DomainHostnameLookupPort.class);
        ShortLinkDtoMapper mapper = new ShortLinkDtoMapper(coreProperties, domainHostnameLookupPort);

        ShortLinkService.LinkDto actual = mapper.toDto(unscopedLink(), List.of());

        assertThat(actual.shortUrl()).isEqualTo("https://go.example.test/r/AbC123");
        verifyNoInteractions(domainHostnameLookupPort);
    }

    private static ShortLink domainScopedLink() {
        return link(33L, 44L);
    }

    private static ShortLink unscopedLink() {
        return link(null, null);
    }

    private static ShortLink link(Long applicationId, Long domainId) {
        return ShortLink.create(
                11L,
                22L,
                applicationId,
                domainId,
                ShortCode.of("AbC123"),
                null,
                HttpUrl.of("https://example.com/live"),
                null,
                true,
                null,
                null,
                false,
                null,
                null,
                null,
                CreatedByType.USER,
                99L
        );
    }
}
