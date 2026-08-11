package com.linkforge.shortlink.infrastructure.persistence.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkQueryMapperXmlTest {

    @Test
    void hostAwareRedirectQueries_shouldFilterActiveDomainsOnly() throws IOException {
        String xml = mapperXml();

        assertThat(xml).contains("<select id=\"findActiveByHostnameAndCode\"");
        assertThat(xml).contains("<select id=\"findActiveByLegacyBaseHostAndCode\"");
        assertThat(xml).contains("d.status = 'ACTIVE'");
    }

    @Test
    void searchQueries_shouldUseKeysetCursorAndIndexedTextSearch() throws IOException {
        String xml = mapperXml();

        assertThat(xml).contains("<select id=\"listSearchAfter\"");
        assertThat(xml).contains("created_at &lt; #{cursorCreatedAtUtc}");
        assertThat(xml).contains("id &lt; #{cursorId}");
        assertThat(xml).contains("MATCH(original_url, note) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)");
        assertThat(xml).contains("code LIKE CONCAT(#{keyword}, '%')");
    }

    private static String mapperXml() throws IOException {
        try (InputStream in = ShortLinkQueryMapperXmlTest.class.getClassLoader().getResourceAsStream(
                "com/linkforge/shortlink/infrastructure/persistence/mapper/ShortLinkQueryMapper.xml"
        )) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
