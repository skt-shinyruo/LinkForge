package com.linkforge.shortlink.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryForwardAllowlistTest {

    @Test
    void shouldNormalizeDistinctAndLimit() {
        List<String> raw = new ArrayList<>();
        raw.add("utm_source");
        raw.add(" utm_source "); // duplicate after trim
        raw.add("utm_*");
        raw.add(""); // ignored

        QueryForwardAllowlist allowlist = QueryForwardAllowlist.fromRaw(raw);
        assertThat(allowlist.values()).containsExactly("utm_source", "utm_*");
        assertThat(allowlist.serializeOrNull()).isEqualTo("utm_source,utm_*");
    }

    @Test
    void shouldRejectSerializedTooLong() {
        String longName = "a".repeat(1025);
        assertThatThrownBy(() -> QueryForwardAllowlist.fromRaw(List.of(longName)))
                .isInstanceOf(ShortLinkDomainException.class)
                .satisfies(ex -> assertThat(((ShortLinkDomainException) ex).reason())
                        .isEqualTo(ShortLinkDomainException.Reason.INVALID_QUERY_FORWARD_ALLOWLIST_TOO_LONG));
    }

    @Test
    void parseSerialized_shouldReturnEmptyForBlank() {
        QueryForwardAllowlist allowlist = QueryForwardAllowlist.parseSerialized("   ");
        assertThat(allowlist.values()).isEmpty();
        assertThat(allowlist.serializeOrNull()).isNull();
    }
}

