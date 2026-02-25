package com.linkforge.edge.analytics.service;

import com.linkforge.platform.web.VisitInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorFingerprintTest {

    @Test
    void fingerprint_should_be_deterministic_and_hex() {
        LocalDate day = LocalDate.of(2026, 2, 19);
        VisitInfo v = new VisitInfo("1.2.3.4", "ua-test", null, null, java.util.Map.of());

        String f1 = VisitorFingerprint.fingerprint(day, v, "salt");
        String f2 = VisitorFingerprint.fingerprint(day, v, "salt");
        String f3 = VisitorFingerprint.fingerprint(day, new VisitInfo("1.2.3.5", "ua-test", null, null, java.util.Map.of()), "salt");

        assertThat(f1).isEqualTo(f2);
        assertThat(f1).isNotEqualTo(f3);
        assertThat(f1).hasSize(64);
        assertThat(f1).matches("[0-9a-f]+");
    }
}
