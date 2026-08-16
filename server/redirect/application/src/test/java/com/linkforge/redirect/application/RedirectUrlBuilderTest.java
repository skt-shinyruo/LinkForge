package com.linkforge.redirect.application;

import com.linkforge.contract.redirect.LinkMeta;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectUrlBuilderTest {

    @Test
    void buildFinalRedirectUrl_should_not_double_encode_existing_query_or_fragment() {
        RedirectUrlBuilder builder = new RedirectUrlBuilder(null);

        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc",
                "https://example.com/a?x=1%2B2&q=hello%20world#frag",
                true,
                null,
                null,
                false,
                null,
                "ALL",
                null,
                null,
                null,
                null,
                LinkMeta.ACTIVE_LIFECYCLE_STATE
        );

        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("utm_source", new String[]{"google"});
        params.put("y", new String[]{"3+4"});

        String out = builder.buildFinalRedirectUrl(meta, params);

        assertThat(out).isEqualTo("https://example.com/a?x=1%2B2&q=hello%20world&utm_source=google&y=3%2B4#frag");
    }

    @Test
    void buildFinalRedirectUrl_should_cap_forwarded_query_size_to_avoid_extreme_urls() {
        RedirectUrlBuilder builder = new RedirectUrlBuilder(null);

        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc",
                "https://example.com/a",
                true,
                null,
                null,
                false,
                null,
                "ALL",
                null,
                null,
                null,
                null,
                LinkMeta.ACTIVE_LIFECYCLE_STATE
        );

        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("utm_source", new String[]{"x".repeat(10_000)});

        String out = builder.buildFinalRedirectUrl(meta, params);

        // Oversized query-forward should fall back to original URL (best-effort, safety first).
        assertThat(out).isEqualTo("https://example.com/a");
    }

    @Test
    void buildFinalRedirectUrl_should_not_exceed_final_url_length_cap() {
        RedirectUrlBuilder builder = new RedirectUrlBuilder(null);

        String longPath = "a".repeat(4090 - "https://example.com/".length());
        String originalUrl = "https://example.com/" + longPath;

        LinkMeta meta = new LinkMeta(
                1L,
                1L,
                "abc",
                originalUrl,
                true,
                null,
                null,
                false,
                null,
                "ALL",
                null,
                null,
                null,
                null,
                LinkMeta.ACTIVE_LIFECYCLE_STATE
        );

        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("utm_source", new String[]{"google"});

        String out = builder.buildFinalRedirectUrl(meta, params);

        // When the merged URL becomes too long, keep original URL unchanged.
        assertThat(out).isEqualTo(originalUrl);
    }
}
