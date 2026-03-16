package com.linkforge.analytics.application;

import com.linkforge.contract.analytics.VisitContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisitDimensionNormalizerTest {

    @Test
    void normalize_should_extract_referer_domain_and_language_and_tracking() {
        VisitContext v = new VisitContext(
                "1.2.3.4",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/123.0.0.0 Safari/537.36",
                "https://Example.COM/path?a=1",
                "zh-CN,zh;q=0.9,en;q=0.8",
                Map.of(
                        "utm_source", "ads",
                        "utm_medium", "cpc",
                        "utm_campaign", "spring_sale"
                )
        );

        VisitDimensionNormalizer.Normalized n = VisitDimensionNormalizer.normalize(
                v,
                255,
                512,
                128
        );

        assertThat(n.refererDomain()).isEqualTo("example.com");
        assertThat(n.language()).isEqualTo("zh-cn");
        assertThat(n.userAgentFamily()).isEqualTo("chrome");
        assertThat(n.osFamily()).isEqualTo("windows");
        assertThat(n.deviceType()).isEqualTo("desktop");
        assertThat(n.utmSource()).isEqualTo("ads");
        assertThat(n.utmMedium()).isEqualTo("cpc");
        assertThat(n.utmCampaign()).isEqualTo("spring_sale");
    }

    @Test
    void normalize_should_be_defensive_for_missing_and_invalid_inputs() {
        VisitContext v = new VisitContext(
                "1.2.3.4",
                "Googlebot/2.1 (+http://www.google.com/bot.html)",
                "not-a-url",
                "",
                Map.of(
                        "utm_source", "----"
                )
        );

        VisitDimensionNormalizer.Normalized n = VisitDimensionNormalizer.normalize(
                v,
                255,
                512,
                128
        );

        assertThat(n.refererDomain()).isEqualTo("unknown");
        assertThat(n.language()).isEqualTo("unknown");
        assertThat(n.userAgentFamily()).isEqualTo("bot");
        assertThat(n.deviceType()).isEqualTo("bot");
        assertThat(n.utmSource()).isNull();
    }

    @Test
    void normalize_should_truncate_ua_and_tracking_values() {
        String ua = "x".repeat(600);
        String campaign = "y".repeat(200);
        VisitContext v = new VisitContext(
                "1.2.3.4",
                ua,
                null,
                null,
                Map.of("utm_campaign", campaign)
        );

        VisitDimensionNormalizer.Normalized n = VisitDimensionNormalizer.normalize(
                v,
                255,
                50,
                20
        );

        assertThat(n.userAgentRaw()).hasSize(50);
        assertThat(n.utmCampaign()).hasSize(20);
    }

    @Test
    void normalize_should_use_safe_defaults_when_max_len_is_non_positive() {
        String ua = "x".repeat(600);
        String campaign = "y".repeat(200);
        VisitContext v = new VisitContext(
                "1.2.3.4",
                ua,
                "https://example.com",
                "en-US,en;q=0.9",
                Map.of("utm_campaign", campaign)
        );

        VisitDimensionNormalizer.Normalized n = VisitDimensionNormalizer.normalize(
                v,
                0,
                0,
                0
        );

        assertThat(n.userAgentRaw()).hasSize(512);
        assertThat(n.utmCampaign()).hasSize(128);
    }
}
