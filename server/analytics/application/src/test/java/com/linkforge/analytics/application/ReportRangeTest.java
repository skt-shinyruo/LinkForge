package com.linkforge.analytics.application;

import org.junit.jupiter.api.Test;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportRangeTest {

    @Test
    void sameUtcDay_shouldBeOneInclusiveReportDay() {
        ReportRange range = ReportRange.of(
                LocalDate.parse("2026-08-15"),
                LocalDate.parse("2026-08-15")
        );

        assertThat(range.from()).isEqualTo(LocalDate.parse("2026-08-15"));
        assertThat(range.to()).isEqualTo(LocalDate.parse("2026-08-15"));
        assertThat(range.inclusiveDays()).isEqualTo(1L);
    }

    @Test
    void moreThan366UtcDays_shouldBeRejected() {
        assertThatThrownBy(() -> ReportRange.of(
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2025-01-01")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void reversedUtcDays_shouldBeRejected() {
        assertThatThrownBy(() -> ReportRange.of(
                LocalDate.parse("2026-08-16"),
                LocalDate.parse("2026-08-15")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void utcTimestamps_shouldUseTheirInclusiveNaturalDays() {
        ReportRange range = ReportRange.ofUtc(
                LocalDateTime.parse("2024-01-01T23:59:59"),
                LocalDateTime.parse("2024-12-31T00:00:00")
        );

        assertThat(range.from()).isEqualTo(LocalDate.parse("2024-01-01"));
        assertThat(range.to()).isEqualTo(LocalDate.parse("2024-12-31"));
        assertThat(range.inclusiveDays()).isEqualTo(366L);
    }
}
