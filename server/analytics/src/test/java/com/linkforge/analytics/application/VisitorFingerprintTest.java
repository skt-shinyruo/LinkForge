package com.linkforge.analytics.application;

import com.linkforge.contract.analytics.VisitContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorFingerprintTest {

    @Test
    void fingerprint_should_be_deterministic_and_hex() {
        LocalDate day = LocalDate.of(2026, 2, 19);
        VisitContext v = new VisitContext("1.2.3.4", "ua-test", null, null, java.util.Map.of());

        String f1 = VisitorFingerprint.fingerprint(day, v, "salt");
        String f2 = VisitorFingerprint.fingerprint(day, v, "salt");
        String f3 = VisitorFingerprint.fingerprint(day, new VisitContext("1.2.3.5", "ua-test", null, null, java.util.Map.of()), "salt");

        assertThat(f1).isEqualTo(f2);
        assertThat(f1).isNotEqualTo(f3);
        assertThat(f1).hasSize(64);
        assertThat(f1).matches("[0-9a-f]+");
    }

    @Test
    void fingerprint_should_cap_user_agent_length() {
        LocalDate day = LocalDate.of(2026, 2, 19);
        String longUa = "a".repeat(600);

        VisitContext capped = new VisitContext("1.2.3.4", "a".repeat(512), null, null, Map.of());
        VisitContext over = new VisitContext("1.2.3.4", longUa, null, null, Map.of());

        assertThat(VisitorFingerprint.fingerprint(day, over, "salt"))
                .isEqualTo(VisitorFingerprint.fingerprint(day, capped, "salt"));
    }
}
