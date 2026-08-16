package com.linkforge.analytics.application;

import com.linkforge.contract.api.BusinessException;
import com.linkforge.contract.api.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Analytics 查询统一使用的、首尾均包含的 UTC 自然日范围校验。 */
public final class ReportRange {

    public static final int MAX_INCLUSIVE_DAYS = 366;

    private ReportRange() {
    }

    public static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1L > MAX_INCLUSIVE_DAYS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表日期范围最大为 366 天");
        }
    }

    public static void validateUtc(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报表开始时间不能晚于结束时间");
        }
        validate(from.toLocalDate(), to.toLocalDate());
    }
}
