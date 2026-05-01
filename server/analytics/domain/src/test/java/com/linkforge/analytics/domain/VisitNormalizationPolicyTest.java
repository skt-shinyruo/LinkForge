package com.linkforge.analytics.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisitNormalizationPolicyTest {

    @Test
    void normalize_shouldTrimInlineFieldsAndCopyTrackingParameters() {
        VisitNormalizationPolicy policy = new VisitNormalizationPolicy();

        VisitDimension dimension = policy.normalize(
                " 1.2.3.4 ",
                " Mozilla/5.0\nChrome ",
                " https://Example.COM/path ",
                " zh-CN,zh;q=0.9 ",
                Map.of("utm_source", " newsletter ")
        );

        assertThat(dimension.ip()).isEqualTo("1.2.3.4");
        assertThat(dimension.userAgent()).isEqualTo("Mozilla/5.0 Chrome");
        assertThat(dimension.referer()).isEqualTo("https://Example.COM/path");
        assertThat(dimension.acceptLanguage()).isEqualTo("zh-CN,zh;q=0.9");
        assertThat(dimension.trackingParams()).containsEntry("utm_source", "newsletter");
    }

    @Test
    void normalize_shouldCollapseBlankValuesToNullAndDropBlankTrackingValues() {
        VisitNormalizationPolicy policy = new VisitNormalizationPolicy();

        VisitDimension dimension = policy.normalize(" ", "\t", "", null, Map.of("utm_source", " "));

        assertThat(dimension.ip()).isNull();
        assertThat(dimension.userAgent()).isNull();
        assertThat(dimension.referer()).isNull();
        assertThat(dimension.acceptLanguage()).isNull();
        assertThat(dimension.trackingParams()).isEmpty();
    }
}
